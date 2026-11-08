import XCTest

/// Proof photo on iOS: deliver a stop, pick a photo from the library, watch it upload.
///
/// This test used to stall after the swipe back on the route: a `LazyVStack` re-laid out without
/// end (one core at 100 %), so the app never reported idle. See docs/illustrated/04, "The test
/// that hung".
final class ProofUITests: XCTestCase {
    func testPhotoReachesTheServer() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-resetSession", "-disableLocation"]
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
        // Picker cells are labelled "Photo, <date>"; the first image in the picker is a 17 pt icon.
        let photo = app.images.matching(NSPredicate(format: "label BEGINSWITH 'Photo'")).firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 20), "photo library did not open")
        // The picker is another process; its cells report "not hittable", so tap the spot.
        photo.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["Photo sent"].waitForExistence(timeout: 60), "photo never reached the server")
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = "12-photo-sent"
        a.lifetime = .keepAlways
        add(a)
    }
}
