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
        openMenu(app)
        let action = app.buttons.matching(NSPredicate(format: "identifier == %@ OR label == %@ OR label == %@",
            "panel.settings", "Settings", "Nastavenia")).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 5))
        action.tap()
        XCTAssertTrue(element(app, "settings.textSize").waitForExistence(timeout: 5))
    }
    private func openMenu(_ app: XCUIApplication) {
        let menu = element(app, "panel.menu")
        XCTAssertTrue(menu.exists)
        XCTAssertGreaterThan(menu.frame.width, 0)
        // SwiftUI's toolbar Menu wrapper has no AX hit point on iOS 18.
        // Use its observed frame; the following assertion verifies a real menu opens.
        menu.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }
    private func scrollToControl(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        for _ in 0..<15 {
            // Rebuild the query: Form virtualizes rows as they enter/leave the viewport.
            let control = element(app, id)
            let window = app.windows.firstMatch.frame
            if control.exists, control.frame.width > 0 {
                let y = control.frame.midY
                if y > window.minY + 110 && y < window.maxY - 50 { return control }
                if y <= window.minY + 110 {
                    app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                        .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)))
                    continue
                }
            }
            // Short drags prevent skipping a 44-point row on the compact iPhone.
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)))
        }
        XCTFail("Control could not be brought into view: \(id)")
        return element(app, id)
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
        element(app, "settings.theme").tap()
        app.buttons["Light"].tap()
        app.buttons["settings.done"].tap()
        screenshot("iOS-demo-light")
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
        let value = app.staticTexts["master.value"]
        XCTAssertTrue(value.exists)
        XCTAssertGreaterThanOrEqual(value.frame.minX, app.windows.firstMatch.frame.minX)
        XCTAssertLessThanOrEqual(value.frame.maxX, app.windows.firstMatch.frame.maxX + 1)
        screenshot("iOS-slovak-4XL-300")
        XCUIDevice.shared.orientation = .landscapeLeft
        screenshot("iOS-landscape-4XL-300")
        settings(app)
        XCTAssertTrue(app.buttons["settings.done"].exists)
        app.buttons["settings.done"].tap()
        XCUIDevice.shared.orientation = .portrait
    }
    func testProfileCheckboxesAndMuteOnlyPanel() {
        let app = launch()
        app.buttons["panel.mixers"].tap()
        app.buttons["profile.add"].tap()
        XCTAssertTrue(app.textFields["profile.name"].waitForExistence(timeout: 5))
        let master = scrollToControl(app, "control.master.selected")
        master.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let mute = scrollToControl(app, "control.mute1.selected")
        mute.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        screenshot("iOS-profile-controls")
        app.buttons["profile.save"].tap()
        app.buttons["mixers.done"].tap()
        XCTAssertTrue(app.buttons["control.mute1.mute"].waitForExistence(timeout: 5))
        XCTAssertFalse(element(app, "control.master.fader").exists)
        screenshot("iOS-mute-only")
    }
}
