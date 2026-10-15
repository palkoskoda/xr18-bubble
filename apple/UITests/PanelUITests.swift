import XCTest

final class PanelUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--demo"]
        app.launch()
        XCTAssertTrue(element(app, "panel.status").waitForExistence(timeout: 10))
        return app
    }
    private func screenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func settings(_ app: XCUIApplication) {
        app.buttons["panel.menu"].tap()
        app.buttons["panel.settings"].tap()
        XCTAssertTrue(element(app, "settings.textSize").waitForExistence(timeout: 5))
    }
    func testDemoMuteAndFader() {
        let app = launch()
        screenshot("iOS-demo-dark")
        let mute = app.buttons["control.master.mute"]
        XCTAssertTrue(mute.isEnabled)
        mute.tap()
        XCTAssertEqual(mute.value as? String, "MUTED")
        mute.tap()
        let fader = element(app, "control.master.fader")
        XCTAssertTrue(fader.exists)
        let before = app.staticTexts["master.value"].label
        fader.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.1, thenDragTo: fader.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)))
        XCTAssertNotEqual(app.staticTexts["master.value"].label, before)
        screenshot("iOS-demo-adjusted")
    }
    func testMaximumSizeTextAndSlovak() {
        let app = launch()
        settings(app)
        element(app, "settings.size").tap()
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Maximum · 4XL")).firstMatch.tap()
        element(app, "settings.textSize").tap()
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "300")).firstMatch.tap()
        screenshot("iOS-settings-large-text")
        element(app, "settings.language").tap()
        app.buttons["Slovenčina"].tap()
        app.buttons["settings.done"].tap()
        XCTAssertTrue(element(app, "panel.status").label.contains("Ukážka"))
        screenshot("iOS-slovak-4XL-300")
        XCUIDevice.shared.orientation = .landscapeLeft
        screenshot("iOS-landscape-4XL-300")
        XCTAssertTrue(app.buttons["panel.menu"].isHittable)
        XCUIDevice.shared.orientation = .portrait
    }
    func testProfileCheckboxesAndMuteOnlyPanel() {
        let app = launch()
        app.buttons["panel.mixers"].tap()
        app.buttons["profile.add"].tap()
        XCTAssertTrue(app.textFields["profile.name"].waitForExistence(timeout: 5))
        let master = element(app, "control.master.selected")
        for _ in 0..<5 where !master.isHittable { app.swipeUp() }
        XCTAssertTrue(master.isHittable)
        master.tap()
        let mute = element(app, "control.mute1.selected")
        for _ in 0..<4 where !mute.isHittable { app.swipeUp() }
        mute.tap()
        screenshot("iOS-profile-controls")
        app.buttons["profile.save"].tap()
        app.buttons["mixers.done"].tap()
        XCTAssertTrue(app.buttons["control.mute1.mute"].waitForExistence(timeout: 5))
        XCTAssertFalse(element(app, "control.master.fader").exists)
        screenshot("iOS-mute-only")
    }
}
