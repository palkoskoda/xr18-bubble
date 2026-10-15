import XCTest
import XR18Core
@testable import XR18Panel

final class SessionModelTests: XCTestCase {
    @MainActor func testDemoNeverBecomesPersistedLiveProfile() {
        let name = "xr18.tests.\(UUID())"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let model = SessionModel(defaults: defaults)
        model.enterDemo()
        XCTAssertTrue(model.isDemo)
        XCTAssertFalse(model.connected)
        XCTAssertEqual(model.controls.count, 3)
        model.setFader("master", value: 0.42)
        model.toggleMute("mute1")
        XCTAssertEqual(model.controls.first?.fader ?? -1, 0.42, accuracy: 0.001)
        XCTAssertEqual(model.controls.last?.muted, true)
        model.savePreferences()
        XCTAssertTrue(model.profiles.isEmpty)
        let restored = SessionModel(defaults: defaults)
        restored.startup()
        XCTAssertTrue(restored.isDemo)
        XCTAssertFalse(restored.connected)
        XCTAssertTrue(restored.profiles.isEmpty)
        restored.disconnect(); model.disconnect()
    }

    @MainActor func testProfilesPersistWithoutSecretsAndCanRemoveControls() {
        let name = "xr18.tests.\(UUID())"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let model = SessionModel(defaults: defaults)
        var profile = MixerProfile(name: "Stage", controls: ["master", "ch16", "mute4"])
        model.saveProfile(profile, password: nil, returnPassword: nil)
        profile.controls = ["mute4"]
        model.saveProfile(profile, password: nil, returnPassword: nil)
        model.textPercent = 300; model.controlSize = "4xl"; model.savePreferences()
        let restored = SessionModel(defaults: defaults)
        XCTAssertEqual(restored.profiles, [profile])
        XCTAssertEqual(restored.controls.count, 1)
        XCTAssertFalse(restored.controls[0].hasFader)
        XCTAssertEqual(restored.textPercent, 300)
        XCTAssertEqual(restored.controlSize, "4xl")
        restored.deleteProfile(profile.id)
        XCTAssertTrue(restored.profiles.isEmpty)
    }

    /// Requires tools/fake_mixer.py on this Mac at 127.0.0.1:10024.
    @MainActor func testConnectedUDPReadbackAndOfflineWriteRejection() async throws {
        let name = "xr18.transport.tests.\(UUID())"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let model = SessionModel(defaults: defaults)
        defer { model.disconnect() }
        let profile = MixerProfile(name: "Local fixture", host: "127.0.0.1", controls: ["master", "mute1"])
        model.saveProfile(profile, password: nil, returnPassword: nil)
        // No connection exists: these must never become queued SETs.
        model.setFader("master", value: 1); model.toggleMute("mute1")
        model.connect()
        try await wait { model.connected }
        XCTAssertEqual(model.controls[0].fader ?? -1, 0.65, accuracy: 0.001)
        XCTAssertEqual(model.controls[1].muted, false)
        model.setFader("master", value: 0.42)
        model.toggleMute("mute1")
        try await wait { abs((model.controls[0].fader ?? -1) - 0.42) < 0.001 && model.controls[1].muted == true }
        try await wait { model.controls[0].meter > 0 }
        model.disconnect()
        XCTAssertFalse(model.connected)
        XCTAssertNil(model.controls[0].fader)
        model.setFader("master", value: 1); model.toggleMute("mute1")
        try await Task.sleep(nanoseconds: 250_000_000)
    }

    @MainActor private func wait(_ predicate: () -> Bool) async throws {
        for _ in 0..<150 {
            if predicate() { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTFail("Timed out waiting for verified UDP fixture state")
        throw NSError(domain: "fixture.timeout", code: 1)
    }
}
