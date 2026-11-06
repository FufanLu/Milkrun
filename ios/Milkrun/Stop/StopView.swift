@preconcurrency import MilkrunKit
import SwiftUI

/// Working one stop: parcels on label stock, reversible actions on top, and the irreversible
/// Deliver alone at the bottom, separated by empty space.
struct StopView: View {
    @Environment(\.milkrun) private var c
    @Environment(\.dismiss) private var dismiss
    let stopKey: String
    let screen: RouteScreenModel
    @State private var detail: StopDetailState?
    @State private var handoff: HandoffMethod = .frontDoor
    @State private var reasonsOpen = false
    @State private var note = ""
    @State private var refusal: String?

    var body: some View {
        Group {
            if let d = detail {
                content(d)
            } else {
                Color.clear
            }
        }
        .background(c.ground)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(c.ground, for: .navigationBar)
        .toolbar {
            if detail?.card.unsynced == true {
                #if compiler(>=6.2)
                if #available(iOS 26.0, *) {
                    // Tape is stuck on the bar, not floated in iOS 26's shared glass capsule.
                    ToolbarItem(placement: .topBarTrailing) { TapeTab() }.sharedBackgroundVisibility(.hidden)
                } else {
                    ToolbarItem(placement: .topBarTrailing) { TapeTab() }
                }
                #else
                ToolbarItem(placement: .topBarTrailing) { TapeTab() }
                #endif
            }
        }
        .task(id: stopKey) {
            guard let card = currentCard() else { return }
            for await d in screen.stop(id: card.id) {
                detail = d
                if note.isEmpty { note = d?.card.note ?? "" }
            }
        }
        .alert(refusal ?? "", isPresented: Binding(get: { refusal != nil }, set: { if !$0 { refusal = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private func currentCard() -> StopCard? {
        let s = screen.state.value
        return ([s.next].compactMap { $0 } + s.upcoming + s.done).first { $0.key == stopKey }
    }

    private func content(_ d: StopDetailState) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .top, spacing: Space.l) {
                        Text(d.card.number).font(Stencil.hero).foregroundStyle(c.ink)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityLabel("Stop \(d.card.number)")
                        VStack(alignment: .leading, spacing: Space.xs) {
                            Text(d.card.address).font(.title2.weight(.semibold)).foregroundStyle(c.ink)
                            Text(d.card.statusLabel).font(.body).foregroundStyle(c.ink2)
                        }
                        .padding(.top, Space.s)
                    }
                    .padding(.horizontal, Space.gutter)
                    if d.card.deliveredAfterCancel {
                        ExceptionSticker(
                            title: Copy.shared.DELIVERED_AFTER_CANCEL,
                            message: "Dispatch cancelled this stop while you were delivering it. Your delivery stands."
                        )
                        .padding(Space.gutter)
                    }
                    Text("Parcels · \(d.parcels.filter(\.scanned).count) of \(d.parcels.count) scanned")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(c.ink2)
                        .padding(.horizontal, Space.gutter)
                        .padding(.top, Space.xl)
                        .padding(.bottom, Space.s)
                    VStack(spacing: 0) {
                        ForEach(Array(d.parcels.enumerated()), id: \.element.key) { i, p in
                            ParcelLine(parcel: p, enabled: d.canScan && !p.scanned) { scan(d, p) }
                            if i < d.parcels.count - 1 { Fluting() }
                        }
                    }
                    .background(Rectangle().fill(c.label).shadow(color: .black.opacity(0.12), radius: 1, y: 1))
                    .overlay { if let edge = c.labelEdge { Rectangle().stroke(edge, lineWidth: 2) } }
                    .padding(.horizontal, Space.gutter)
                    TextField("Note for this address", text: $note, prompt: Text("Gate code, where you left it").foregroundStyle(c.labelInk2))
                        .font(.body)
                        .foregroundStyle(c.labelInk)
                        .padding(Space.l)
                        .frame(minHeight: Target.action)
                        .background(c.label)
                        .overlay(Rectangle().stroke(c.ink2, lineWidth: 1))
                        .submitLabel(.done)
                        .onSubmit { save(d) }
                        .padding(Space.gutter)
                        .padding(.top, Space.s)
                }
            }
            if d.canDeliver || d.canAttempt || d.canArrive {
                dock(d)
            }
        }
        .confirmationDialog("Why couldn't you deliver?", isPresented: $reasonsOpen, titleVisibility: .visible) {
            ForEach(AttemptReason.allCases, id: \.self) { r in
                Button(Copy.shared.attemptOption(r: r)) { attempt(d, r) }
            }
        }
    }

    /// Reversible actions above; where it was left; a gap; then Deliver, alone.
    private func dock(_ d: StopDetailState) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: Space.m) {
                if d.canArrive && d.card.status == .planned {
                    Button("Arrived") { record { try await screen.arrive(stop: d.card.id) } }
                        .buttonStyle(OutlineBlockButtonStyle())
                }
                if d.canAttempt {
                    Button("Can't deliver") { reasonsOpen = true }.buttonStyle(OutlineBlockButtonStyle())
                }
            }
            .padding(.horizontal, Space.gutter)
            .padding(.top, Space.m)
            if d.canDeliver {
                Text("Left at").font(.subheadline.weight(.semibold)).foregroundStyle(c.labelInk2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Space.gutter).padding(.top, Space.m).padding(.bottom, Space.xs)
                HandoffGrid(selected: $handoff).padding(.horizontal, Space.gutter)
                // The deliberate gap: the irreversible action never sits within a thumb's slip of another.
                Spacer().frame(height: Space.xxl)
                Button("Deliver \(Copy.shared.parcels(n: Int32(d.parcels.count)))") { deliver(d) }
                    .buttonStyle(BlockButtonStyle())
            } else {
                Spacer().frame(height: Space.m)
            }
        }
        // The dock is lifted off the page: an upward offset, not a halo.
        .background(c.label.shadow(.drop(color: .black.opacity(0.22), radius: 6, y: -2)))
    }

    private func scan(_ d: StopDetailState, _ p: ParcelRow) { record { try await screen.scan(stop: d.card.id, parcel: p.id) } }

    private func save(_ d: StopDetailState) {
        guard note != d.card.note else { return }
        record { try await screen.setNote(stop: d.card.id, text: note) }
    }

    private func deliver(_ d: StopDetailState) {
        let handoff = handoff
        record(done: true) { try await screen.deliver(stop: d.card.id, handoff: handoff) }
    }

    private func attempt(_ d: StopDetailState, _ r: AttemptReason) {
        record(done: true) { try await screen.attempt(stop: d.card.id, reason: r) }
    }

    private func record(done: Bool = false, _ action: @escaping @MainActor () async throws -> RecordResult) {
        Task { @MainActor in
            guard let result = try? await action(), let number = detail?.card.number else { return }
            if let message = screen.refusal(result: result, stopNumber: number) {
                refusal = message
            } else if done {
                dismiss()
            }
        }
    }
}

private struct ParcelLine: View {
    @Environment(\.milkrun) private var c
    let parcel: ParcelRow
    let enabled: Bool
    let onScan: () -> Void

    var body: some View {
        Button(action: onScan) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(parcel.itemDescription.prefix(1).uppercased() + parcel.itemDescription.dropFirst())
                        .font(.body).foregroundStyle(c.labelInk)
                    Text(parcel.key).font(.subheadline.monospaced()).foregroundStyle(c.labelInk2)
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                Spacer()
                if parcel.scanned {
                    Image(systemName: "checkmark").font(.title3.weight(.semibold)).foregroundStyle(c.labelInk)
                } else {
                    Image(systemName: "barcode.viewfinder").font(.title3).foregroundStyle(enabled ? c.labelInk : c.labelInk2)
                    Text(enabled ? "Tap to scan" : "Not scanned").font(.footnote.weight(.medium)).foregroundStyle(c.labelInk2)
                }
            }
            .padding(.horizontal, Space.l)
            .padding(.vertical, Space.s)
            .frame(minHeight: Target.action + 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Not .disabled: that would grey out a scanned parcel's text below the contrast floor.
        .allowsHitTesting(enabled)
        .accessibilityValue(parcel.scanned ? "Scanned" : "Not scanned")
    }
}

/// Where the parcels were left: four large blocks, 2 by 2. Selected is a heavier frame with a
/// check, never an ink fill, since a filled block is reserved for Deliver.
private struct HandoffGrid: View {
    @Environment(\.milkrun) private var c
    @Binding var selected: HandoffMethod

    var body: some View {
        let options = Array(HandoffMethod.allCases)
        Grid(horizontalSpacing: Space.m, verticalSpacing: Space.m) {
            ForEach(0..<(options.count / 2), id: \.self) { row in
                GridRow {
                    ForEach(options[(row * 2)..<(row * 2 + 2)], id: \.self) { h in
                        let on = h == selected
                        Button {
                            selected = h
                        } label: {
                            HStack(spacing: Space.s) {
                                if on { Image(systemName: "checkmark").font(.body.weight(.bold)) }
                                Text(Copy.shared.handoffOption(h: h)).font(.body.weight(on ? .bold : .medium))
                            }
                            .foregroundStyle(c.labelInk)
                            .frame(maxWidth: .infinity, minHeight: Target.action)
                            .background(c.label)
                            .overlay(Rectangle().stroke(on ? c.labelInk : c.labelInk2, lineWidth: on ? 4 : 1.5))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? [.isSelected] : [])
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Left at")
    }
}
