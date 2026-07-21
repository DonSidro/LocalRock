import Foundation
import Network
import NetworkExtension
import ComposeApp

// iOS Wi-Fi pairing for LocalRock, implementing the Kotlin `VacuumPairingNative` contract.
//
// Pairing works like this: the vacuum, when unprovisioned, exposes an open access point named
// `roborock-vacuum-<something>`. The phone joins that AP, exchanges a UDP handshake with the robot
// at 192.168.8.1:55559 (hello -> RSA-wrapped session key -> Wi-Fi config -> ack), and then leaves.
//
// Two Apple APIs are needed, neither of which Kotlin/Native can reach:
//  - NEHotspotConfiguration (NetworkExtension) to join the vacuum's AP. This requires the Hotspot
//    Configuration entitlement on the app target (`iosApp.entitlements`).
//  - NWConnection (Network.framework) for the UDP exchange. Talking to a private LAN address makes
//    iOS show the local-network prompt (NSLocalNetworkUsageDescription in Info.plist).
//
// The shared Kotlin `VacuumOnboarder` drives the protocol and the step ordering; this class is just
// the platform plumbing: join, send, receive, clean up.

final class VacuumPairingFactory: VacuumPairingNativeFactory {

    /// Drops any hotspot configuration left behind by an earlier session. A pairing attempt that
    /// died without tearing down leaves the phone on the robot's AP, where the server is
    /// unreachable — and since pairing talks to the server *before* it joins the robot, a stale
    /// configuration would sink the next attempt during login, with a DNS error that says nothing
    /// about Wi-Fi. Cheap to run at launch, so run it at launch.
    init() {
        let manager = NEHotspotConfigurationManager.shared
        manager.getConfiguredSSIDs { configured in
            for ssid in configured where ssid.hasPrefix(Self.vacuumSsidPrefix) {
                manager.removeConfiguration(forSSID: ssid)
            }
        }
    }

    func create() -> VacuumPairingNative { VacuumPairingSession() }

    // Matches the default in the shared Kotlin `VacuumPairingTransport.joinVacuumWifi`.
    private static let vacuumSsidPrefix = "roborock-vacuum-"
}

final class VacuumPairingSession: VacuumPairingNative {

    private let queue = DispatchQueue(label: "com.kodraliu.localrock.pairing")
    private var connection: NWConnection?
    private var joinedSsid: String?
    private var joinedPrefix: String?

    // MARK: Joining the vacuum's access point

    func joinWifi(
        ssidPrefix: String,
        timeoutMs: Int64,
        onJoined: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        // The join and the timeout race each other; whichever lands first wins.
        let done = CallbackGuard()

        let config = NEHotspotConfiguration(ssidPrefix: ssidPrefix)
        // Don't leave the vacuum's AP in the user's known-networks list after pairing.
        config.joinOnce = true

        // Remember the prefix even if the join later fails: iOS may already have associated, and
        // `close()` has to be able to undo that or the phone is stranded on an AP with no route to
        // the user's server.
        joinedPrefix = ssidPrefix

        queue.asyncAfter(deadline: .now() + .milliseconds(Int(timeoutMs))) {
            guard done.claim() else { return }
            onError("Timed out waiting to join the vacuum's Wi-Fi network (\(ssidPrefix)…). " +
                    "Make sure the robot is in pairing mode and close to the phone.")
        }

        NEHotspotConfigurationManager.shared.apply(config) { error in
            if let error = error as NSError?,
               error.code != NEHotspotConfigurationError.alreadyAssociated.rawValue {
                guard done.claim() else { return }
                onError(Self.describe(error, ssidPrefix: ssidPrefix))
                return
            }
            // `apply` reports success as soon as iOS accepts the join, not once Wi-Fi has finished
            // associating, so the current network needs to be sampled until it settles rather than
            // read once.
            self.confirmJoined(ssidPrefix: ssidPrefix, done: done, onJoined: onJoined)
        }
    }

    /// Polls the current network until it is the vacuum's AP. `fetchCurrent` is unreliable right
    /// after a join — it can report the previous network, or nothing at all — so a single mismatched
    /// sample is not treated as failure. If it never confirms, the handshake proceeds anyway and the
    /// UDP hello timeout becomes the real verdict: a false "you're not on the vacuum's network" is
    /// worse than a clean handshake timeout, because the former blocks a pairing that would work.
    private func confirmJoined(
        ssidPrefix: String,
        done: CallbackGuard,
        attempt: Int = 0,
        onJoined: @escaping () -> Void
    ) {
        NEHotspotNetwork.fetchCurrent { network in
            if let network, network.ssid.hasPrefix(ssidPrefix) {
                guard done.claim() else { return }
                self.joinedSsid = network.ssid
                onJoined()
                return
            }
            guard attempt < Self.joinConfirmAttempts else {
                guard done.claim() else { return }
                onJoined() // Unconfirmed, but let the handshake decide.
                return
            }
            self.queue.asyncAfter(deadline: .now() + .milliseconds(Self.joinConfirmIntervalMs)) {
                self.confirmJoined(
                    ssidPrefix: ssidPrefix,
                    done: done,
                    attempt: attempt + 1,
                    onJoined: onJoined
                )
            }
        }
    }

    private static let joinConfirmAttempts = 20      // ~10s of association grace
    private static let joinConfirmIntervalMs = 500

    // MARK: UDP handshake

    func send(
        host: String,
        port: Int32,
        data: KotlinByteArray,
        onSent: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        let payload = Data(data.toUInt8())
        withReadyConnection(host: host, port: port, onError: onError) { connection in
            connection.send(content: payload, completion: .contentProcessed { error in
                if let error {
                    onError("UDP send failed: \(error.localizedDescription)")
                } else {
                    onSent()
                }
            })
        }
    }

    func receive(
        timeoutMs: Int64,
        onReceived: @escaping (KotlinByteArray?) -> Void,
        onError: @escaping (String) -> Void
    ) {
        guard let connection else {
            onError("Cannot receive before sending: no UDP socket is open.")
            return
        }

        // The robot answers to the source port of our datagram, so the same NWConnection is reused
        // for the whole exchange rather than opened per message.
        let done = CallbackGuard()

        queue.asyncAfter(deadline: .now() + .milliseconds(Int(timeoutMs))) {
            guard done.claim() else { return }
            onReceived(nil) // A timeout is a normal outcome here, not an error.
        }

        connection.receiveMessage { data, _, _, error in
            guard done.claim() else { return }
            if let error {
                onError("UDP receive failed: \(error.localizedDescription)")
                return
            }
            guard let data, !data.isEmpty else {
                onReceived(nil)
                return
            }
            onReceived(KotlinByteArray.from([UInt8](data)))
        }
    }

    func close() {
        connection?.cancel()
        connection = nil

        // Put the phone back on its normal network. A prefix-joined configuration is registered
        // under the *prefix*, not under the SSID the phone actually landed on, so removing only the
        // resolved SSID is a silent no-op that strands the phone on the robot's AP — with no DNS and
        // no route to the user's server. Remove every configuration this session could have created,
        // and let iOS's own list be the source of truth.
        let manager = NEHotspotConfigurationManager.shared
        let ssid = joinedSsid
        let prefix = joinedPrefix

        if let prefix { manager.removeConfiguration(forSSID: prefix) }
        if let ssid { manager.removeConfiguration(forSSID: ssid) }
        manager.getConfiguredSSIDs { configured in
            for entry in configured {
                if entry == ssid || entry == prefix || (prefix.map { entry.hasPrefix($0) } ?? false) {
                    manager.removeConfiguration(forSSID: entry)
                }
            }
        }

        joinedSsid = nil
        joinedPrefix = nil
    }

    // MARK: Helpers

    /// Opens the UDP socket on first use and calls `body` once it is ready to carry traffic.
    private func withReadyConnection(
        host: String,
        port: Int32,
        onError: @escaping (String) -> Void,
        body: @escaping (NWConnection) -> Void
    ) {
        if let connection, connection.state == .ready {
            body(connection)
            return
        }

        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(clamping: port)) else {
            onError("Invalid pairing port: \(port)")
            return
        }

        let parameters = NWParameters.udp
        // The vacuum's AP has no internet, so pin the socket to Wi-Fi and stop iOS from quietly
        // routing these datagrams over cellular.
        parameters.requiredInterfaceType = .wifi
        parameters.prohibitExpensivePaths = false

        let connection = NWConnection(
            host: NWEndpoint.Host(host),
            port: nwPort,
            using: parameters
        )
        self.connection = connection

        let done = CallbackGuard()
        connection.stateUpdateHandler = { state in
            switch state {
            case .ready:
                guard done.claim() else { return }
                body(connection)
            case .failed(let error):
                guard done.claim() else { return }
                onError("UDP socket failed: \(error.localizedDescription)")
            case .cancelled:
                guard done.claim() else { return }
                onError("UDP socket was cancelled.")
            default:
                break
            }
        }
        connection.start(queue: queue)
    }

    private static func describe(_ error: NSError, ssidPrefix: String) -> String {
        switch NEHotspotConfigurationError(rawValue: error.code) {
        case .userDenied:
            return "Joining the vacuum's Wi-Fi network was declined."
        case .invalidSSIDPrefix:
            return "iOS rejected the vacuum SSID prefix \(ssidPrefix)."
        case .pending, .systemConfiguration, .unknown, .joinOnceNotSupported:
            return "iOS could not join the vacuum's Wi-Fi network: \(error.localizedDescription)"
        default:
            return "Could not join the vacuum's Wi-Fi network: \(error.localizedDescription)"
        }
    }
}

/// Both the network callback and its timeout can fire; the first one through wins and the loser is
/// dropped, so the Kotlin continuation is never resumed twice.
private final class CallbackGuard {
    private let lock = NSLock()
    private var used = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if used { return false }
        used = true
        return true
    }
}
