import Foundation

#if DEBUG
/// Debug builds only: pings the main queue every second from a background thread and logs when a
/// ping waits more than a second. Separates "main thread blocked" from "main thread busy" when a
/// UI test stalls.
enum HangWatchdog {
    static func start() {
        if ProcessInfo.processInfo.arguments.contains("-watchdogSelfTest") {
            // Proves the watchdog can see a stall: block the main thread once, 3 s after launch.
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { Thread.sleep(forTimeInterval: 2.5) }
        }
        let q = DispatchQueue(label: "milkrun.watchdog", qos: .utility)
        q.async {
            while true {
                let sent = Date()
                let sema = DispatchSemaphore(value: 0)
                DispatchQueue.main.async { sema.signal() }
                if sema.wait(timeout: .now() + 1) == .timedOut {
                    sema.wait()
                    NSLog("milkrun watchdog: main thread blocked %.1f s", Date().timeIntervalSince(sent))
                }
                Thread.sleep(forTimeInterval: 1)
            }
        }
    }
}
#endif
