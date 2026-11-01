// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RtcOfferCheck",
    platforms: [.macOS(.v13)],
    dependencies: [.package(url: "https://github.com/stasel/WebRTC", exact: "149.0.0")],
    targets: [.executableTarget(name: "RtcOfferCheck", dependencies: ["WebRTC"])]
)
