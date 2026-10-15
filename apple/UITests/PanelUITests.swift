import XCTest

final class PanelUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--demo"]
        app.launch()
        XCTAssertTrue(app.otherElements["panel.status"].waitForExistence(timeout: 10))
        return app
    }
    private func screenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func settings(_ app: XCUIApplication) {
        app.buttons["panel.menu"].tap()
        app.buttons["panel.settings"].tap()
        XCTAssertTrue(app.buttons["settings.textSize"].waitForExistence(timeout: 5))
    }
    func testDemoMuteAndFader() {
        let app = launch()
        screenshot("iOS-demo-dark")
        let mute = app.buttons["control.master.mute"]
        XCTAssertTrue(mute.isEnabled)
        mute.tap()
        XCTAssertEqual(mute.value as? String, "MUTED")
        mute.tap()
        let fader = app.otherElements["control.master.fader"]
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
        app.buttons["settings.size"].tap()
        app.buttons["Maximum · 4XL"].tap()
        app.buttons["settings.textSize"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "300")).firstMatch.tap()
        screenshot("iOS-settings-large-text")
        app.buttons["settings.language"].tap()
        app.buttons["Slovenčina"].tap()
        app.buttons["settings.done"].tap()
        XCTAssertTrue(app.otherElements["panel.status"].label.contains("demo"))
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
        let master = app.buttons["control.master.selected"]
        for _ in 0..<5 where !master.isHittable { app.swipeUp() }
        XCTAssertTrue(master.isHittable)
        master.tap()
        let mute = app.buttons["control.mute1.selected"]
        for _ in 0..<4 where !mute.isHittable { app.swipeUp() }
        mute.tap()
        screenshot("iOS-profile-controls")
        app.buttons["profile.save"].tap()
        app.buttons["mixers.done"].tap()
        XCTAssertTrue(app.buttons["control.mute1.mute"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.otherElements["control.master.fader"].exists)
        screenshot("iOS-mute-only")
    }
}
