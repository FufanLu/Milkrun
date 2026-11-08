@preconcurrency import MilkrunKit
import BackgroundTasks
import SwiftUI

@main
struct MilkrunApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        SyncTask.register()
        #if DEBUG
        HangWatchdog.start()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .task { await model.start() }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: model.refresh()
            case .background: SyncTask.schedule()
            default: break
            }
        }
    }
}

/// Owns the process's one MilkrunSession and mirrors the shared screen model's state for SwiftUI.
@MainActor
@Observable
final class AppModel {
    enum Phase { case loading, failed, ready }

    private(set) var phase: Phase = .loading
    private(set) var route: RouteUiState?
    private(set) var session: MilkrunSession?
    private let arrival = ArrivalWatcher()
    var sunMode: Bool = UserDefaults.standard.bool(forKey: "sun") {
        didSet { UserDefaults.standard.set(sunMode, forKey: "sun") }
    }

    /// `-serverURL <url>` overrides Info.plist; UI tests point it at a dead port to go "offline".
    static var serverURL: String {
        UserDefaults.standard.string(forKey: "serverURL")
            ?? Bundle.main.object(forInfoDictionaryKey: "MilkrunServerURL") as? String
            ?? "http://localhost:18081"
    }

    func start() async {
        guard session == nil else { return }
        if ProcessInfo.processInfo.arguments.contains("-resetSession") { KeychainSessionStore().clear() }
        do {
            let s = try await Milkrun.shared.start(serverUrl: Self.serverURL, store: KeychainSessionStore())
            session = s
            SyncTask.session = s
            phase = .ready
            arrival.start(s.screen)
            for await state in s.screen.state {
                route = state
            }
        } catch {
            phase = .failed
        }
    }

    func refresh() { session?.screen.refresh() }

    /// End of shift: wipe the route's database and proof photos, then start the next shift.
    func endShift() async {
        guard let s = session else { return }
        let ended = s.endShift { route in
            Milkrun.shared.deleteRouteDatabase(routeId: route)
            try? FileManager.default.removeItem(at: URL.documentsDirectory.appending(path: "proof"))
        }
        guard ended else { return }
        arrival.stop()
        session = nil
        route = nil
        phase = .loading
        await start()
    }

    var screen: RouteScreenModel? { session?.screen }
}

/// BGTaskScheduler drain: one sync round when iOS gives the app background time.
enum SyncTask {
    static let identifier = "com.milkrun.sync"
    nonisolated(unsafe) static var session: MilkrunSession?

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { bgTask in
            schedule()
            // The system hands the task to this closure alone; completing it from the main actor is safe.
            nonisolated(unsafe) let task = bgTask
            let work = Task { @MainActor in
                do {
                    try await session?.engine.syncOnce()
                    task.setTaskCompleted(success: true)
                } catch {
                    task.setTaskCompleted(success: false)
                }
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

struct RootView: View {
    @Bindable var model: AppModel
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        // `-forceScheme night|kraft|sun` pins a scheme for UI tests and screenshots.
        let forced = UserDefaults.standard.string(forKey: "forceScheme").flatMap(Scheme.init(rawValue:))
        let colors = MilkrunColors.of(forced ?? (model.sunMode ? .sun : (systemScheme == .dark ? .night : .kraft)))
        Group {
            switch model.phase {
            case .loading: Splash(text: "Loading your route")
            case .failed: Splash(text: "Couldn't reach the station to load a route. Check signal and reopen the app.")
            case .ready:
                if let route = model.route, let screen = model.screen {
                    NavigationStack {
                        RouteView(state: route, screen: screen, sunMode: $model.sunMode) { Task { await model.endShift() } }
                    }
                    .tint(colors.ink)
                } else {
                    Splash(text: "Loading your route")
                }
            }
        }
        .environment(\.milkrun, colors)
        .preferredColorScheme(colors.isDark ? .dark : (model.sunMode ? .light : nil))
    }
}

private struct Splash: View {
    @Environment(\.milkrun) private var c
    let text: String

    var body: some View {
        Text(text)
            .font(.body)
            .foregroundStyle(c.ink)
            .multilineTextAlignment(.center)
            .padding(Space.xl)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(c.ground)
    }
}
