@preconcurrency import MilkrunKit
import SwiftUI
import Testing
@testable import Milkrun

/// The Swift side of the shared core: what crosses the Kotlin/Swift boundary must read naturally
/// here, and the iOS tokens must stay the DESIGN.md tokens Android uses.
@MainActor
struct MilkrunTests {
    @Test func sharedCopyIsTheSameSentenceAndroidShows() {
        #expect(Copy.shared.handoffOption(h: .mailroom) == "Mailroom")
        #expect(Copy.shared.attemptOption(r: .nobodyHome) == "Nobody home")
        let (title, body) = (
            Copy.shared.rejection(stop: "04", reason: .stopCancelled).first as String?,
            Copy.shared.rejection(stop: "04", reason: .stopCancelled).second as String?
        )
        #expect(title == "Stop 04 was cancelled by dispatch")
        #expect(body == "Keep its parcels on the van and bring them back to the station.")
    }

    @Test func kotlinEnumsArriveAsExhaustiveSwiftEnums() {
        #expect(HandoffMethod.allCases.count == 4)
        #expect(AttemptReason.allCases.count == 4)
        let statuses: [StopStatus] = [.planned, .arrived, .delivered, .attempted, .cancelled]
        #expect(statuses.map(\.isTerminal) == [false, false, true, false, false])
    }

    @Test func sealedSyncBannerSwitchesExhaustively() {
        let waiting = SyncBannerWaiting(pending: 3, retrying: true, lastError: "no signal")
        switch onEnum(of: waiting as SyncBanner) {
        case .waiting(let w): #expect(w.title == "3 waiting for signal")
        case .allSent: Issue.record("wrong case")
        }
        #expect(SyncBannerWaiting(pending: 1, retrying: false, lastError: nil).title == "Sending 1 change")
    }

    @Test func sunIsTheOnlySchemeWithALabelEdge() {
        #expect(MilkrunColors.sun.labelEdge != nil)
        #expect(MilkrunColors.kraft.labelEdge == nil)
        #expect(MilkrunColors.night.labelEdge == nil)
        #expect(MilkrunColors.night.isDark)
    }

    @Test func tornEndsStayInsideTheirRect() {
        let rect = CGRect(x: 0, y: 0, width: 200, height: 48)
        let path = TornEnds().path(in: rect)
        #expect(rect.insetBy(dx: -0.5, dy: -0.5).contains(path.boundingRect))
        #expect(path.boundingRect.width == 200)
    }
}
