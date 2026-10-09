// swift-tools-version:5.9
import PackageDescription

// RivetCore: everything in the iOS app that is not UI — API client, models, certificate trust,
// permissions, deep links and formatting. It builds and tests on Linux (Docker) as well as on
// Apple platforms, so the logic is verifiable without Xcode. The SwiftUI app target (App/) depends on it.
let package = Package(
    name: "RivetCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "RivetCore", targets: ["RivetCore"]),
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-crypto.git", from: "3.0.0"),
    ],
    targets: [
        .target(
            name: "RivetCore",
            dependencies: [.product(name: "Crypto", package: "swift-crypto")],
            path: "Sources/RivetCore"
        ),
        .testTarget(
            name: "RivetCoreTests",
            dependencies: ["RivetCore"],
            path: "Tests/RivetCoreTests"
        ),
    ]
)
