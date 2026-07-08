import Foundation
import CocoaMQTT
import ComposeApp

// iOS MQTT transport for VacLocal, implementing the Kotlin `MqttNativeTransport` contract on top
// of CocoaMQTT. CocoaMQTT uses Network.framework / system TLS, so there is no OpenSSL dependency
// (KMQTT's native TLS needs OpenSSL, which isn't available on iOS — hence this native transport).
//
// The shared Kotlin `MqttClient` owns all reconnect / keep-alive / re-subscribe logic; this class
// is just the thin socket layer: connect, forward delegate callbacks, subscribe/publish/close.

final class CocoaMqttTransport: MqttNativeTransport {
    func open(
        host: String,
        port: Int32,
        tls: Bool,username: String,
        password: String,
        clientId: String,
        keepAliveSeconds: Int32,
        onConnected: @escaping () -> Void,
        onFailed: @escaping (String) -> Void,
        onMessage: @escaping (String, KotlinByteArray) -> Void,
        onDisconnected: @escaping () -> Void
    ) -> MqttNativeConnection {
        let mqtt = CocoaMQTT(clientID: clientID(clientId), host: host, port: UInt16(port))
        mqtt.username = username
        mqtt.password = password
        mqtt.keepAlive = UInt16(keepAliveSeconds)
        mqtt.autoReconnect = false // the Kotlin supervisor handles reconnects
        mqtt.cleanSession = true
        if tls {
            mqtt.enableSSL = true
            // Certificates are validated against the system trust store in the delegate's
            // `didReceive trust` handler below. If your server uses a private/self-signed CA,
            // install that CA profile on the device so it evaluates as trusted.
        }

        let connection = CocoaMqttConnection(
            mqtt: mqtt,
            onConnected: onConnected,
            onFailed: onFailed,
            onMessage: onMessage,
            onDisconnected: onDisconnected
        )
        mqtt.delegate = connection
        if !mqtt.connect() {
            onFailed("CocoaMQTT.connect() returned false")
        }
        return connection
    }

    // CocoaMQTT requires a non-empty client id; keep it stable/unique like the Kotlin caller intends.
    private func clientID(_ id: String) -> String {
        id.isEmpty ? "vaclocal-\(UUID().uuidString)" : id
    }
}

final class CocoaMqttConnection: NSObject, MqttNativeConnection, CocoaMQTTDelegate {
    private let mqtt: CocoaMQTT
    private let onConnected: () -> Void
    private let onFailed: (String) -> Void
    private let onMessage: (String, KotlinByteArray) -> Void
    private let onDisconnected: () -> Void

    private var connectedFlag = false
    private var everConnected = false

    init(
        mqtt: CocoaMQTT,
        onConnected: @escaping () -> Void,
        onFailed: @escaping (String) -> Void,
        onMessage: @escaping (String, KotlinByteArray) -> Void,
        onDisconnected: @escaping () -> Void
    ) {
        self.mqtt = mqtt
        self.onConnected = onConnected
        self.onFailed = onFailed
        self.onMessage = onMessage
        self.onDisconnected = onDisconnected
    }

    // MARK: MqttNativeConnection

    var isConnected: Bool { connectedFlag }

    func subscribe(topic: String, qos: Int32) {
        mqtt.subscribe(topic, qos: cocoaQos(qos))
    }

    func unsubscribe(topic: String) {
        mqtt.unsubscribe(topic)
    }

    func publish(topic: String, payload: KotlinByteArray, qos: Int32, retain: Bool) {
        let message = CocoaMQTTMessage(
            topic: topic,
            payload: payload.toUInt8(),
            qos: cocoaQos(qos),
            retained: retain
        )
        mqtt.publish(message)
    }

    func close() {
        connectedFlag = false
        mqtt.disconnect()
    }

    // MARK: CocoaMQTTDelegate

    func mqtt(_ mqtt: CocoaMQTT, didConnectAck ack: CocoaMQTTConnAck) {
        if ack == .accept {
            connectedFlag = true
            everConnected = true
            onConnected()
        } else {
            onFailed("CONNACK not accepted: \(ack)")
        }
    }

    func mqtt(_ mqtt: CocoaMQTT, didReceiveMessage message: CocoaMQTTMessage, id: UInt16) {
        onMessage(message.topic, KotlinByteArray.from(message.payload))
    }

    func mqttDidDisconnect(_ mqtt: CocoaMQTT, withError err: Error?) {
        connectedFlag = false
        if everConnected {
            onDisconnected()
        } else {
            onFailed(err?.localizedDescription ?? "disconnected before connect")
        }
    }

    // Validate the server's TLS certificate against the system trust store instead of blindly
    // trusting it. A private/self-signed CA passes once its profile is installed on the device.
    func mqtt(_ mqtt: CocoaMQTT, didReceive trust: SecTrust, completionHandler: @escaping (Bool) -> Void) {
        var error: CFError?
        let trusted = SecTrustEvaluateWithError(trust, &error)
        if !trusted {
            print("[LocalRock] TLS trust evaluation failed: \(error.map { String(describing: $0) } ?? "unknown")")
        }
        completionHandler(trusted)
    }

    // Unused delegate methods (required by the protocol).
    func mqtt(_ mqtt: CocoaMQTT, didPublishMessage message: CocoaMQTTMessage, id: UInt16) {}
    func mqtt(_ mqtt: CocoaMQTT, didPublishAck id: UInt16) {}
    func mqtt(_ mqtt: CocoaMQTT, didSubscribeTopics success: NSDictionary, failed: [String]) {}
    func mqtt(_ mqtt: CocoaMQTT, didUnsubscribeTopics topics: [String]) {}
    func mqttDidPing(_ mqtt: CocoaMQTT) {}
    func mqttDidReceivePong(_ mqtt: CocoaMQTT) {}

    private func cocoaQos(_ qos: Int32) -> CocoaMQTTQoS {
        CocoaMQTTQoS(rawValue: UInt8(clamping: qos)) ?? .qos1
    }
}

// MARK: - KotlinByteArray <-> bytes bridging

extension KotlinByteArray {
    func toUInt8() -> [UInt8] {
        let count = Int(size)
        var out = [UInt8](repeating: 0, count: count)
        for i in 0..<count { out[i] = UInt8(bitPattern: get(index: Int32(i))) }
        return out
    }

    static func from(_ bytes: [UInt8]) -> KotlinByteArray {
        let arr = KotlinByteArray(size: Int32(bytes.count))
        for (i, b) in bytes.enumerated() { arr.set(index: Int32(i), value: Int8(bitPattern: b)) }
        return arr
    }
}
