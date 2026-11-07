import CoreLocation
@preconcurrency import MilkrunKit

/// Feeds Core Location fixes to the shared arrival detector while the app is in use.
///
/// When-in-use only, like Android: the route is on screen while driving, and "Always" is a
/// bigger ask than the feature is worth. If the driver declines, nothing changes: the Arrived
/// button is still there. UI tests pass `-disableLocation` so no permission alert covers the app.
@MainActor
final class ArrivalWatcher {
    private let manager = CLLocationManager()
    private var task: Task<Void, Never>?
    private var ticker: Task<Void, Never>?

    func start(_ screen: RouteScreenModel) {
        guard task == nil, !ProcessInfo.processInfo.arguments.contains("-disableLocation") else { return }
        manager.requestWhenInUseAuthorization()
        task = Task {
            do {
                for try await update in CLLocationUpdate.liveUpdates(.automotiveNavigation) {
                    guard let fix = update.location, fix.horizontalAccuracy >= 0 else { continue }
                    _ = try? await screen.onLocation(
                        lat: fix.coordinate.latitude,
                        lng: fix.coordinate.longitude,
                        accuracyMeters: fix.horizontalAccuracy,
                        atMillis: Int64(fix.timestamp.timeIntervalSince1970 * 1000)
                    )
                }
            } catch {
                // Updates ended (denied, or the task was cancelled at end of shift).
            }
        }
        // Parked phones stop reporting fixes; the dwell completes on the clock.
        ticker = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                _ = try? await screen.onLocationTick(atMillis: Int64(Date().timeIntervalSince1970 * 1000))
            }
        }
    }

    func stop() {
        task?.cancel()
        ticker?.cancel()
        task = nil
        ticker = nil
    }
}
