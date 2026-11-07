import CoreLocation
import XCTest

/// Drives the simulated device to the first stop and waits: the app must mark it Arrived on its
/// own, from Core Location fixes through the shared ArrivalDetector (20 s dwell inside 40 m).
final class ArrivalUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    func testParkingAtTheStopMarksItArrived() throws {
        // Space Needle, ~3.3 km from stop 01: not arrived anywhere.
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 47.6205, longitude: -122.3493))
        let app = XCUIApplication()
        app.launchArguments = ["-resetSession"]
        app.launch()
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow While Using App"]
        if allow.waitForExistence(timeout: 10) { allow.tap() }
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 30), "route never loaded")
        XCTAssertTrue(app.buttons["Arrived"].firstMatch.exists)
        attach(app, "1-away")

        // Stop 01, 1st Ave S & S Royal Brougham Way.
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 47.5917, longitude: -122.3343))
        let start = Date()
        XCTAssertTrue(app.buttons["Scan and deliver"].firstMatch.waitForExistence(timeout: 60), "never arrived")
        let waited = Date().timeIntervalSince(start)
        XCTAssertGreaterThanOrEqual(waited, 15, "arrived without dwelling")
        print("milkrun-arrival waited \(Int(waited)) s")
        attach(app, "2-arrived")
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }
}
