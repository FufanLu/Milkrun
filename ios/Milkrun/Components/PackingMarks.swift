import SwiftUI

/// The 1pt corrugation line, the only divider in the system.
struct Fluting: View {
    @Environment(\.milkrun) private var c
    var body: some View { Rectangle().fill(c.fluting).frame(height: 1) }
}

/// Zig-zag cut ends, the way packing tape tears off the dispenser.
struct TornEnds: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let tooth = rect.height / 6
        let depth: CGFloat = 3
        p.move(to: CGPoint(x: depth, y: 0))
        p.addLine(to: CGPoint(x: rect.width - depth, y: 0))
        var y: CGFloat = 0
        var out = true
        while y < rect.height {
            y = min(y + tooth, rect.height)
            p.addLine(to: CGPoint(x: out ? rect.width : rect.width - depth, y: y))
            out.toggle()
        }
        p.addLine(to: CGPoint(x: depth, y: rect.height))
        out = true
        while y > 0 {
            y = max(y - tooth, 0)
            p.addLine(to: CGPoint(x: out ? 0 : depth, y: y))
            out.toggle()
        }
        p.closeSubpath()
        return p
    }
}

/// The tape strip under the header: work recorded on the phone that the server has not confirmed.
/// The box's fluting rule runs under the tape's lower third.
struct TapeStrip: View {
    @Environment(\.milkrun) private var c
    let title: String
    let detail: String

    var body: some View {
        ZStack(alignment: .bottom) {
            Rectangle().fill(c.ink2).frame(height: 2).padding(.bottom, 10)
            HStack {
                Text(title).font(.subheadline.weight(.semibold))
                Spacer()
                if !detail.isEmpty { Text(detail).font(.caption.weight(.medium)) }
            }
            .foregroundStyle(c.onTape)
            .padding(.horizontal, Space.l)
            .padding(.vertical, Space.m)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(
                c.tape.opacity(tapeAlpha)
                    .overlay(TapeFibres().stroke(c.onTape.opacity(0.10), lineWidth: 1))
            )
            .clipShape(TornEnds())
            .padding(.horizontal, Space.gutter)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct TapeFibres: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        var y: CGFloat = 4
        while y < rect.height {
            p.move(to: CGPoint(x: 0, y: y))
            p.addLine(to: CGPoint(x: rect.width, y: y))
            y += 5
        }
        return p
    }
}

/// A short piece of tape stuck across a row or a label's edge: this stop has unconfirmed changes.
struct TapeTab: View {
    @Environment(\.milkrun) private var c
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Text("Unsent")
            .font(.caption.weight(.bold))
            .foregroundStyle(c.onTape)
            .padding(.horizontal, Space.m)
            .padding(.vertical, Space.xs)
            .background(c.tape.opacity(tapeAlpha))
            .clipShape(TornEnds())
            // The tilt is how tape lies, not motion: it stays under Reduce Motion (DESIGN.md).
            .rotationEffect(.degrees(-4))
            .accessibilityLabel("Not sent yet")
            .transition(reduceMotion ? .opacity : .asymmetric(
                // Laid on from the trailing edge; peeled off toward the leading side, half its width.
                insertion: .move(edge: .trailing).combined(with: .opacity).animation(.easeOut(duration: 0.2)),
                removal: .offset(x: -36).combined(with: .opacity).animation(.easeIn(duration: 0.18))
            ))
    }
}

/// The printed mark on an exception label: an ink diamond with a cut-out exclamation.
struct HazardMark: View {
    @Environment(\.milkrun) private var c

    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            var d = Path()
            d.move(to: CGPoint(x: w / 2, y: 0))
            d.addLine(to: CGPoint(x: w, y: h / 2))
            d.addLine(to: CGPoint(x: w / 2, y: h))
            d.addLine(to: CGPoint(x: 0, y: h / 2))
            d.closeSubpath()
            ctx.fill(d, with: .color(c.onSticker))
            let bar = w * 0.11
            ctx.fill(Path(CGRect(x: w / 2 - bar / 2, y: h * 0.24, width: bar, height: h * 0.32)), with: .color(c.sticker))
            ctx.fill(Path(CGRect(x: w / 2 - bar / 2, y: h * 0.64, width: bar, height: bar)), with: .color(c.sticker))
        }
        .frame(width: 22, height: 22)
        .accessibilityLabel("Exception")
    }
}

/// Safety-orange exception sticker: the server ruled on something. Says what happened and what to do.
struct ExceptionSticker: View {
    @Environment(\.milkrun) private var c
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let title: String
    let message: String?
    var onDismiss: (() -> Void)? = nil
    @State private var landed = false

    var body: some View {
        HStack(alignment: .top, spacing: Space.m) {
            HazardMark().padding(.top, 2)
            VStack(alignment: .leading, spacing: Space.xs) {
                Text(title).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                if let message { Text(message).font(.subheadline) }
                if let onDismiss {
                    HStack {
                        Spacer()
                        Button("Got it", action: onDismiss)
                            .font(.body.weight(.semibold))
                            .frame(minHeight: 44)
                    }
                }
            }
            .foregroundStyle(c.onSticker)
            .tint(c.onSticker)
        }
        .padding(.leading, Space.m)
        .padding(.trailing, Space.s)
        .padding(.top, Space.m)
        .padding(.bottom, onDismiss == nil ? Space.m : 0)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.sticker, in: RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(c.stickerEdge, lineWidth: 2))
        .rotationEffect(.degrees(reduceMotion ? 0 : -1.2))
        .scaleEffect(landed || reduceMotion ? 1 : 1.06)
        .padding(.horizontal, Space.s)
        .onAppear { withAnimation(.easeOut(duration: 0.15)) { landed = true } }
        .accessibilityElement(children: .contain)
    }
}

/// A small exception sticker for a list row: one line, no action.
struct StickerTag: View {
    @Environment(\.milkrun) private var c
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let text: String

    var body: some View {
        Text(text)
            .font(.caption.weight(.bold))
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .fixedSize(horizontal: false, vertical: true)
            .foregroundStyle(c.onSticker)
            .padding(.horizontal, Space.s)
            .padding(.vertical, Space.xs)
            .background(c.sticker, in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(c.stickerEdge, lineWidth: 1.5))
            .rotationEffect(.degrees(reduceMotion ? 0 : -1.5))
    }
}

enum StencilMarkKind { case check, cross }

/// Stencilled marks for finished rows, each stroke cut with a bridge.
struct StencilMark: View {
    let kind: StencilMarkKind
    let color: Color

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height)
            let gap = s * 0.07
            func seg(_ a: CGPoint, _ b: CGPoint) {
                var p = Path(); p.move(to: a); p.addLine(to: b)
                ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: s * 0.16, lineCap: .butt))
            }
            switch kind {
            case .check:
                seg(CGPoint(x: s * 0.12, y: s * 0.52), CGPoint(x: s * 0.36 - gap, y: s * 0.76 - gap))
                seg(CGPoint(x: s * 0.38 + gap * 0.3, y: s * 0.74), CGPoint(x: s * 0.90, y: s * 0.20))
            case .cross:
                seg(CGPoint(x: s * 0.16, y: s * 0.16), CGPoint(x: s * 0.84, y: s * 0.84))
                seg(CGPoint(x: s * 0.84, y: s * 0.16), CGPoint(x: s * 0.5 + gap, y: s * 0.5 - gap))
                seg(CGPoint(x: s * 0.5 - gap, y: s * 0.5 + gap), CGPoint(x: s * 0.16, y: s * 0.84))
            }
        }
        .accessibilityLabel(kind == .check ? "Delivered" : "Not delivered")
    }
}

/// A thick marker line through a stop number: cancelled by dispatch.
struct MarkerStrike: View {
    @Environment(\.milkrun) private var c

    var body: some View {
        Canvas { ctx, size in
            var p = Path()
            p.move(to: CGPoint(x: 0, y: size.height * 0.62))
            p.addLine(to: CGPoint(x: size.width, y: size.height * 0.38))
            ctx.stroke(p, with: .color(c.ink), style: StrokeStyle(lineWidth: 6, lineCap: .round))
        }
        .accessibilityHidden(true)
    }
}

/// Full-width block button: the stencil ink as fill. A screen's one irreversible action.
struct BlockButtonStyle: ButtonStyle {
    @Environment(\.milkrun) private var c
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.bold))
            .frame(maxWidth: .infinity, minHeight: Target.primary)
            .foregroundStyle(enabled ? c.onPrimary : c.ink2)
            .background(enabled ? c.primary : c.fluting)
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Outlined companion for reversible actions.
struct OutlineBlockButtonStyle: ButtonStyle {
    @Environment(\.milkrun) private var c

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: Target.action)
            .foregroundStyle(c.labelInk)
            .overlay(Rectangle().stroke(c.labelInk, lineWidth: 2))
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
