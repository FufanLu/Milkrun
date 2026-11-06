import SwiftUI

/// Milkrun's packing-room world on iOS. The values are the tokens in design/DESIGN.md, the same
/// ones Theme.kt implements on Android. iOS takes structure from the HIG (navigation stack,
/// sheets, SF Symbols, Dynamic Type) and takes its look from these tokens.
struct MilkrunColors: Sendable {
    let ground: Color
    let fluting: Color
    let ink: Color
    let ink2: Color
    let label: Color
    let labelInk: Color
    let labelInk2: Color
    let tape: Color
    let onTape: Color
    let sticker: Color
    let onSticker: Color
    let stickerEdge: Color
    let primary: Color
    let onPrimary: Color
    let doneNumber: Color
    /// Ink outline for label stock in Sun, where label and ground are both white.
    let labelEdge: Color?
    let isDark: Bool
}

enum Scheme: String, Sendable { case kraft, night, sun }

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

extension MilkrunColors {
    static let kraft = MilkrunColors(
        ground: Color(hex: 0xC9A57A), fluting: Color(hex: 0xB08A5E),
        ink: Color(hex: 0x1B1610), ink2: Color(hex: 0x3E3020),
        label: Color(hex: 0xF8F4EA), labelInk: Color(hex: 0x15110C), labelInk2: Color(hex: 0x5A4A36),
        tape: Color(hex: 0xE9B949), onTape: Color(hex: 0x1B1610),
        sticker: Color(hex: 0xFF6B1A), onSticker: Color(hex: 0x1B1610), stickerEdge: Color(hex: 0xF8F4EA),
        primary: Color(hex: 0x1B1610), onPrimary: Color(hex: 0xF8F4EA),
        doneNumber: Color(hex: 0x5E4934), labelEdge: nil, isDark: false
    )

    static let night = MilkrunColors(
        ground: Color(hex: 0x211912), fluting: Color(hex: 0x3A2D20),
        ink: Color(hex: 0xEEDDC3), ink2: Color(hex: 0xC2AC8C),
        label: Color(hex: 0x33281D), labelInk: Color(hex: 0xF6E9D4), labelInk2: Color(hex: 0xCDB89A),
        tape: Color(hex: 0xD9A63C), onTape: Color(hex: 0x1B1610),
        sticker: Color(hex: 0xFF7A2E), onSticker: Color(hex: 0x1B1610), stickerEdge: Color(hex: 0xE9DCC6),
        primary: Color(hex: 0xEEDDC3), onPrimary: Color(hex: 0x1B1610),
        doneNumber: Color(hex: 0x8C7559), labelEdge: nil, isDark: true
    )

    static let sun = MilkrunColors(
        ground: Color(hex: 0xFFFFFF), fluting: Color(hex: 0xC9A57A),
        ink: Color(hex: 0x000000), ink2: Color(hex: 0x2B2B2B),
        label: Color(hex: 0xFFFFFF), labelInk: Color(hex: 0x000000), labelInk2: Color(hex: 0x333333),
        tape: Color(hex: 0xFFC400), onTape: Color(hex: 0x000000),
        sticker: Color(hex: 0xFF5A00), onSticker: Color(hex: 0x000000), stickerEdge: Color(hex: 0x000000),
        primary: Color(hex: 0x000000), onPrimary: Color(hex: 0xFFFFFF),
        doneNumber: Color(hex: 0x555555), labelEdge: Color(hex: 0x000000), isDark: false
    )

    static func of(_ scheme: Scheme) -> MilkrunColors {
        switch scheme {
        case .kraft: .kraft
        case .night: .night
        case .sun: .sun
        }
    }
}

private struct MilkrunColorsKey: EnvironmentKey {
    static let defaultValue: MilkrunColors = .kraft
}

extension EnvironmentValues {
    var milkrun: MilkrunColors {
        get { self[MilkrunColorsKey.self] }
        set { self[MilkrunColorsKey.self] = newValue }
    }
}

/// Spacing and targets in points (DESIGN.md: 4/8/12/16/24/32, 16 gutter; 64 primary, 56 action).
enum Space {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 12
    static let l: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let gutter: CGFloat = 16
}

enum Target {
    static let primary: CGFloat = 64
    static let action: CGFloat = 56
}

/// Opacity of packing tape over its ground.
let tapeAlpha: Double = 0.78

/// The stencil face, for stop numbers and the remaining count only. Sizes scale with Dynamic Type
/// relative to the style they stand in for.
enum Stencil {
    static let familyName = "Big Shoulders Stencil Display"

    static func font(_ size: CGFloat, relativeTo style: Font.TextStyle, weight: Font.Weight) -> Font {
        Font.custom(familyName, size: size, relativeTo: style).weight(weight)
    }

    static let hero = font(96, relativeTo: .largeTitle, weight: .heavy)
    static let count = font(72, relativeTo: .largeTitle, weight: .heavy)
    static let row = font(40, relativeTo: .title, weight: .bold)
    static let done = font(28, relativeTo: .title2, weight: .bold)
}
