import XCTest

/// Proof photo on iOS: deliver a stop, pick a photo from the library, watch it upload.
///
/// Known issue (open): after an *online* delivery and the pop back to the route, XCUITest's
/// accessibility snapshot never completes ("Wait for app to idle"); the app itself sits at 0 % CPU
/// until queried. Reproduces on the M4 code too. Not yet isolated (needs a profiler).
final class ProofUITests: XCTestCase {
    func testPhotoReachesTheServer() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-resetSession"]
        app.launch()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 30))
        app.buttons["Arrived"].firstMatch.tap()
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Deliver 1 parcel"].waitForExistence(timeout: 10))
        app.buttons["Deliver 1 parcel"].tap()
        // Back on the route; open the delivered stop from Done.
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 10))
        for _ in 0..<4 { app.swipeUp() }
        app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Delivered'")).firstMatch.tap()
        XCTAssertTrue(app.buttons["Add photo"].waitForExistence(timeout: 10))
        app.buttons["Add photo"].tap()
        let photo = app.scrollViews.otherElements.images.firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 20), "photo library did not open")
        photo.tap()
        XCTAssertTrue(app.staticTexts["Photo sent"].waitForExistence(timeout: 60), "photo never reached the server")
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = "12-photo-sent"
        a.lifetime = .keepAlways
        add(a)
    }
}
