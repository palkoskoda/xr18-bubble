import Foundation
import NetworkExtension
import CoreLocation

/// Real Wi-Fi changes are intentionally unavailable in Simulator. No fake success.
@MainActor final class WifiService: NSObject, CLLocationManagerDelegate {
    private let location = CLLocationManager()
    private var permission: CheckedContinuation<Void, Never>?
    override init() { super.init(); location.delegate = self }
    func currentSSID(requestPermission: Bool = false) async -> String? {
        #if targetEnvironment(simulator)
        return nil
        #else
        if requestPermission && location.authorizationStatus == .notDetermined {
            await withCheckedContinuation { continuation in
                permission = continuation
                location.requestWhenInUseAuthorization()
            }
        }
        return await withCheckedContinuation { continuation in
            NEHotspotNetwork.fetchCurrent { network in continuation.resume(returning: network?.ssid) }
        }
        #endif
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined else { return }
        permission?.resume(); permission = nil
    }
    func join(ssid: String, password: String) async throws {
        #if targetEnvironment(simulator)
        throw WifiError.simulator
        #else
        let configuration = password.isEmpty ? NEHotspotConfiguration(ssid: ssid)
            : NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
        // Persistent: switching to Mixing Station must not disconnect after 15 seconds.
        configuration.joinOnce = false
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            NEHotspotConfigurationManager.shared.apply(configuration) { error in
                if let error {
                    let ns = error as NSError
                    if ns.domain == NEHotspotConfigurationErrorDomain &&
                        ns.code == NEHotspotConfigurationError.alreadyAssociated.rawValue {
                        continuation.resume()
                    } else { continuation.resume(throwing: error) }
                } else { continuation.resume() }
            }
        }
        // apply() completes after configuration, not necessarily association/IP readiness.
        for _ in 0..<20 {
            try Task.checkCancellation()
            if await currentSSID() == ssid { return }
            try await Task.sleep(nanoseconds: 500_000_000)
        }
        throw WifiError.unconfirmed
        #endif
    }
    enum WifiError: LocalizedError {
        case simulator, unconfirmed
        var errorDescription: String? {
            switch self {
            case .simulator: return "Wi-Fi switching needs a physical iPhone or iPad. In Simulator use Demo, or a reachable mixer address with Wi-Fi name left empty."
            case .unconfirmed: return "The requested Wi-Fi connection could not be confirmed. Check Wi-Fi settings and try again."
            }
        }
    }
}
