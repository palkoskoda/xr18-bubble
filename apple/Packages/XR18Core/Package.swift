// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "XR18Core",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "XR18Core", targets: ["XR18Core"])],
    targets: [.target(name: "XR18Core"), .testTarget(name: "XR18CoreTests", dependencies: ["XR18Core"])]
)
