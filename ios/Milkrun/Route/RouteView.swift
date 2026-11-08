@preconcurrency import MilkrunKit
import SwiftUI

/// The route: how many are left, what is waiting for signal, the next stop lifted at full
/// strength, the rest receding. Same structure as RouteScreen.kt; HIG navigation around it.
struct RouteView: View {
    @Environment(\.milkrun) private var c
    let state: RouteUiState
    let screen: RouteScreenModel
    @Binding var sunMode: Bool
    var onEndShift: () -> Void = {}
    @State private var path: [String] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                syncLine
                ForEach(state.notices, id: \.seq) { notice in
                    ExceptionSticker(title: notice.title, message: notice.body) { screen.dismiss(notice: notice) }
                        .padding(.horizontal, Space.gutter)
                        .padding(.vertical, Space.s)
                }
                if let next = state.next {
                    NextStopLabel(card: next, onArrive: { arrive(next) })
                } else if state.loaded {
                    finished
                }
                ForEach(state.upcoming, id: \.key) { card in
                    NavigationLink(value: card.key) { StopRow(card: card) }.buttonStyle(.plain)
                }
                if !state.done.isEmpty {
                    Text("Done · \(state.done.count)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(c.ink2)
                        .padding(.leading, Space.gutter)
                        .padding(.top, Space.xl)
                        .padding(.bottom, Space.s)
                        .accessibilityAddTraits(.isHeader)
                    Fluting()
                    // Distinct identity from the upcoming list: a stop moving between the two lists
                    // must get a fresh row, not the upcoming row's stale view.
                    ForEach(state.done.map { DoneRow(card: $0) }) { row in
                        let card = row.card
                        NavigationLink(value: card.key) { StopRow(card: card) }.buttonStyle(.plain)
                    }
                }
            }
            .padding(.bottom, Space.xxl)
        }
        .refreshable { screen.refresh() }
        // Rows stop at the safe area instead of sliding under the clock; the kraft runs behind it.
        .clipped()
        .background(c.ground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: String.self) { key in
            StopView(stopKey: key, screen: screen)
        }
        .animation(.easeOut(duration: 0.2), value: state.next?.key)
    }

    private func arrive(_ card: StopCard) {
        Task {
            _ = try? await screen.arrive(stop: card.id)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(state.routeLabel).font(.footnote.weight(.medium)).foregroundStyle(c.ink2).lineLimit(1)
                    .accessibilityIdentifier("routeLabel")
                Spacer()
                Button {
                    sunMode.toggle()
                } label: {
                    Image(systemName: sunMode ? "sun.max.fill" : "sun.max")
                        .font(.title3)
                        .foregroundStyle(c.ink)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(sunMode ? "Sunlight mode on" : "Sunlight mode off")
            }
            HStack(alignment: .lastTextBaseline, spacing: Space.s) {
                Text("\(state.remaining)").font(Stencil.count).foregroundStyle(c.ink)
                VStack(alignment: .leading, spacing: 0) {
                    Text("left").font(.title2.weight(.semibold)).foregroundStyle(c.ink)
                    Text("\(state.delivered) delivered · \(state.attempted) not delivered")
                        .font(.subheadline).foregroundStyle(c.ink2)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(state.remaining) stops left, \(state.delivered) delivered, \(state.attempted) not delivered")
        }
        .padding(.leading, Space.gutter)
        .padding(.trailing, Space.s)
        .padding(.bottom, Space.m)
    }

    @ViewBuilder private var syncLine: some View {
        switch onEnum(of: state.sync) {
        case .waiting(let w):
            TapeStrip(title: w.title, detail: w.detail)
        case .allSent:
            VStack(alignment: .leading, spacing: 0) {
                Fluting()
                Text(Copy.shared.ALL_SENT)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(c.ink2)
                    .padding(.horizontal, Space.gutter)
                    .padding(.vertical, Space.xs)
            }
        }
    }

    private var finished: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: Space.xs) {
                Text(Copy.shared.ROUTE_DONE_TITLE).font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
                Text(Copy.shared.routeDoneBody(delivered: state.delivered, attempted: state.attempted)).font(.body)
                    .foregroundStyle(c.labelInk2)
                if !state.canEndShift {
                    Text(Copy.shared.END_SHIFT_WAITING).font(.subheadline).foregroundStyle(c.labelInk2)
                }
            }
            .foregroundStyle(c.labelInk)
            .padding(Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            Button("End shift", action: onEndShift)
                .buttonStyle(BlockButtonStyle())
                .disabled(!state.canEndShift)
        }
        .background(Rectangle().fill(c.label).shadow(color: .black.opacity(0.18), radius: 2, y: 1))
        .padding(Space.gutter)
    }

}

/// The one lifted label on the kraft: the stop the driver is working or driving to.
private struct NextStopLabel: View {
    @Environment(\.milkrun) private var c
    let card: StopCard
    let onArrive: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                NavigationLink(value: card.key) {
                    HStack(alignment: .top, spacing: Space.l) {
                        Text(card.number).font(Stencil.hero).foregroundStyle(c.labelInk)
                            .accessibilityLabel("Next stop, number \(card.number)")
                        VStack(alignment: .leading, spacing: Space.xs) {
                            Text(card.address).font(.title2.weight(.semibold)).foregroundStyle(c.labelInk)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(card.statusLabel).font(.body).foregroundStyle(c.labelInk2)
                            if !card.note.isEmpty {
                                Text("Note: \(card.note)").font(.subheadline).foregroundStyle(c.labelInk2).lineLimit(2)
                            }
                        }
                        .padding(.top, Space.s)
                        Spacer(minLength: 0)
                    }
                    .padding(Space.l)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if card.status == .planned {
                    Button("Arrived", action: onArrive).buttonStyle(BlockButtonStyle())
                } else {
                    NavigationLink("Scan and deliver", value: card.key).buttonStyle(BlockButtonStyle())
                }
            }
            // Shadow on the label's shape only, never on its text.
            .background(Rectangle().fill(c.label).shadow(color: .black.opacity(0.18), radius: 2, y: 1))
            .overlay { if let edge = c.labelEdge { Rectangle().stroke(edge, lineWidth: 2) } }
            if card.unsynced {
                // Across the label's top edge, half on the label, half on the kraft.
                TapeTab().offset(x: 6, y: -12)
            }
        }
        .padding(.horizontal, Space.gutter)
        .padding(.vertical, Space.l)
        .animation(.easeOut(duration: 0.2), value: card.unsynced)
    }
}

/// Upcoming rows step down; finished rows recede by size and ink.
struct StopRow: View {
    @Environment(\.milkrun) private var c
    let card: StopCard

    private var finished: Bool {
        card.status == .delivered || card.status == .attempted || card.status == .cancelled
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topTrailing) {
                HStack(alignment: .top, spacing: Space.m) {
                    ZStack(alignment: .leading) {
                        Text(card.number)
                            .font(finished ? Stencil.done : Stencil.row)
                            .foregroundStyle(finished ? c.doneNumber : c.ink2)
                        if card.status == .cancelled { MarkerStrike().frame(width: 36, height: 30) }
                    }
                    .frame(width: 56, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(card.address)
                            .layoutPriority(1)
                            .font(finished ? .subheadline : .body)
                            .foregroundStyle(finished ? c.ink2 : c.ink)
                            .lineLimit(2)
                        Text(card.statusLabel).font(.subheadline).foregroundStyle(c.ink2)
                        if card.deliveredAfterCancel {
                            StickerTag(text: Copy.shared.DELIVERED_AFTER_CANCEL).padding(.top, Space.s)
                        }
                    }
                    Spacer(minLength: 0)
                    switch card.status {
                    case .delivered: StencilMark(kind: .check, color: c.doneNumber).frame(width: 28, height: 28).padding(.top, Space.xs)
                    case .attempted: StencilMark(kind: .cross, color: c.doneNumber).frame(width: 26, height: 26).padding(.top, Space.xs)
                    default: EmptyView()
                    }
                }
                .padding(.leading, Space.gutter)
                .padding(.trailing, Space.gutter + (card.unsynced ? 72 : 0))
                .padding(.vertical, finished ? Space.s : Space.m)
                .frame(minHeight: finished ? 56 : 72, alignment: .top)
                if card.unsynced { TapeTab().padding(.top, Space.m).padding(.trailing, Space.gutter) }
            }
            .contentShape(Rectangle())
            .animation(.easeOut(duration: 0.2), value: card.unsynced)
            Fluting()
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens stop \(card.number)")
    }
}

private struct DoneRow: Identifiable {
    let card: StopCard
    var id: String { "done-" + card.key }
}
