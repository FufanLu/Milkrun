import XCTest

/// Drives one shift through the real server (dev mode on localhost:18081) and attaches a
/// screenshot at every state the design brief lists. "Offline" is the app pointed at a dead port;
/// the dispatcher's cancels go to the real server from the test runner.
final class ShiftUITests: XCTestCase {
    private let server = URL(string: "http://localhost:18081")!
    private let deadServer = "http://localhost:9"

    override func setUp() { continueAfterFailure = false }

    private func launch(_ args: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = args + ["-disableLocation"]
        app.launch()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 30), "route never loaded")
        return app
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    private func dispatcherToken() throws -> String {
        var req = URLRequest(url: server.appendingPathComponent("v1/dev/session"))
        req.httpMethod = "POST"
        let data = try sync(req)
        return (try JSONSerialization.jsonObject(with: data) as! [String: Any])["dispatcherToken"] as! String
    }

    /// Stop id at route position `sequence`, from the dispatcher's view of the route.
    private func stopId(route: String, sequence: Int, token: String) throws -> String {
        var req = URLRequest(url: server.appendingPathComponent("v1/dispatch/routes/\(route)"))
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try JSONSerialization.jsonObject(with: try sync(req)) as! [String: Any]
        let stops = json["stops"] as! [[String: Any]]
        return stops.first { ($0["sequence"] as! Int) == sequence }!["id"] as! String
    }

    private func cancel(route: String, stop: String, token: String) throws {
        var req = URLRequest(url: server.appendingPathComponent("v1/dispatch/routes/\(route)/commands"))
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = #"{"command":{"type":"cancel","stopId":"\#(stop)"}}"#.data(using: .utf8)
        _ = try sync(req)
    }

    private func sync(_ req: URLRequest) throws -> Data {
        let done = expectation(description: req.url!.path)
        var out: Result<Data, Error> = .failure(URLError(.unknown))
        URLSession.shared.dataTask(with: req) { d, _, e in
            out = d.map { .success($0) } ?? .failure(e ?? URLError(.badServerResponse))
            done.fulfill()
        }.resume()
        wait(for: [done], timeout: 15)
        return try out.get()
    }

    private func deliverNext(_ app: XCUIApplication) {
        let scan = app.buttons["Scan and deliver"].firstMatch
        if !scan.exists { app.buttons["Arrived"].firstMatch.tap() }
        XCTAssertTrue(scan.waitForExistence(timeout: 10))
        scan.tap()
        XCTAssertTrue(app.buttons["Deliver 1 parcel"].waitForExistence(timeout: 10))
        app.buttons["Deliver 1 parcel"].tap()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 10))
    }

    func testOneShift() throws {
        // Start of shift, fresh route.
        var app = launch(["-resetSession"])
        shot(app, "01-route-kraft")
        let route = app.staticTexts["routeLabel"].label

        // Stop 01, online: arrive, open, scan, deliver at the mailroom.
        app.buttons["Arrived"].firstMatch.tap()
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Deliver 1 parcel"].waitForExistence(timeout: 10))
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'Tap to scan'")).firstMatch.tap()
        app.buttons["Mailroom"].tap()
        shot(app, "02-stop")
        app.buttons["Deliver 1 parcel"].tap()
        sleep(3)
        shot(app, "02b-after-deliver")
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 10))

        // Into the lift: same saved shift, server unreachable. Arrive at stop 02.
        app.terminate()
        app = launch(["-serverURL", deadServer])
        app.buttons["Arrived"].firstMatch.tap()
        let waiting = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'waiting for signal'")).firstMatch
        XCTAssertTrue(waiting.waitForExistence(timeout: 40))
        shot(app, "03-offline")

        // Dispatch cancels stops 03 and 04 on the server. The driver, offline, delivers 02 and 03
        // and arrives at 04.
        let token = try dispatcherToken()
        try cancel(route: route, stop: try stopId(route: route, sequence: 3, token: token), token: token)
        try cancel(route: route, stop: try stopId(route: route, sequence: 4, token: token), token: token)
        deliverNext(app)
        deliverNext(app)
        app.buttons["Arrived"].firstMatch.tap()
        sleep(1)

        // Back in range: the server rules.
        app.terminate()
        app = launch()
        XCTAssertTrue(app.staticTexts["Stop 04 was cancelled by dispatch"].waitForExistence(timeout: 40))
        shot(app, "04-ruling")
        app.swipeUp()
        app.swipeUp()
        shot(app, "05-done")
        app.swipeDown()
        app.swipeDown()

        // Night (pinned with -forceScheme; same saved shift), a stop at night, then Sun.
        app.terminate()
        app = launch(["-forceScheme", "night"])
        shot(app, "06-night")
        app.buttons["Arrived"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Scan and deliver"].firstMatch.waitForExistence(timeout: 10))
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Can't deliver"].waitForExistence(timeout: 10))
        shot(app, "07-night-stop")
        app.terminate()
        app = launch()
        app.buttons["Sunlight mode off"].tap()
        sleep(1)
        shot(app, "08-sun")
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Can't deliver"].waitForExistence(timeout: 10))
        shot(app, "08b-sun-stop")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["Sunlight mode on"].tap()

        // Offline again: record a failed attempt at 05 and deliver 06, then look at the stop
        // screen with unsent work and the done list with a cross and an unsynced row.
        app.terminate()
        app = launch(["-serverURL", deadServer])
        if !app.buttons["Scan and deliver"].firstMatch.exists { app.buttons["Arrived"].firstMatch.tap() }
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Can't deliver"].waitForExistence(timeout: 10))
        app.buttons["Can't deliver"].tap()
        app.buttons["Nobody home"].tap()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 10))
        app.buttons["Arrived"].firstMatch.tap()
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Can't deliver"].waitForExistence(timeout: 10))
        sleep(1)
        shot(app, "10-stop-unsent")
        app.buttons["Deliver 1 parcel"].tap()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 10))
        app.swipeUp()
        app.swipeUp()
        sleep(1)
        shot(app, "11-done-offline")
        app.terminate()
        app = launch()

        // Larger text: Dynamic Type XXXL, about 1.3x body.
        app.terminate()
        app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryXXXL", "-disableLocation"]
        app.launch()
        XCTAssertTrue(app.staticTexts["routeLabel"].waitForExistence(timeout: 30))
        // At XXXL the stickers push the next stop below the fold, and LazyVStack only builds what is on screen.
        for _ in 0..<4 where !app.buttons["Scan and deliver"].firstMatch.exists && !app.buttons["Arrived"].firstMatch.exists {
            app.swipeUp()
        }
        if !app.buttons["Scan and deliver"].firstMatch.exists { app.buttons["Arrived"].firstMatch.tap() }
        XCTAssertTrue(app.buttons["Scan and deliver"].firstMatch.waitForExistence(timeout: 10))
        app.buttons["Scan and deliver"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Can't deliver"].waitForExistence(timeout: 10))
        shot(app, "09-xxxl-stop")
    }
}
