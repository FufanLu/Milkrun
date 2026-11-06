package com.milkrun.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkrun.android.R

/**
 * Milkrun's packing-room world (design/DESIGN.md): kraft ground, stencil ink, label stock,
 * packing tape for "waiting for signal", safety-orange exception stickers for server rulings.
 * Three schemes: Kraft (day), Night (garages, dusk) and Sun (direct sunlight, maximum contrast).
 */
@Immutable
data class MilkrunColors(
    val ground: Color,
    val fluting: Color,
    val ink: Color,
    val ink2: Color,
    val label: Color,
    val labelInk: Color,
    val labelInk2: Color,
    val tape: Color,
    val onTape: Color,
    val sticker: Color,
    val onSticker: Color,
    /** The die-cut white margin around a sticker; stays light in every scheme. */
    val stickerEdge: Color,
    val primary: Color,
    val onPrimary: Color,
    val doneNumber: Color,
    /** Outline for label stock where the ground is the same white (Sun). Transparent elsewhere. */
    val labelEdge: Color,
    val isDark: Boolean,
)

enum class Scheme { KRAFT, NIGHT, SUN }

val Kraft = MilkrunColors(
    ground = Color(0xFFC9A57A), fluting = Color(0xFFB08A5E),
    ink = Color(0xFF1B1610), ink2 = Color(0xFF3E3020),
    label = Color(0xFFF8F4EA), labelInk = Color(0xFF15110C), labelInk2 = Color(0xFF5A4A36),
    tape = Color(0xFFE9B949), onTape = Color(0xFF1B1610),
    sticker = Color(0xFFFF6B1A), onSticker = Color(0xFF1B1610), stickerEdge = Color(0xFFF8F4EA),
    primary = Color(0xFF1B1610), onPrimary = Color(0xFFF8F4EA),
    doneNumber = Color(0xFF5E4934), labelEdge = Color.Transparent, isDark = false,
)

val Night = MilkrunColors(
    ground = Color(0xFF211912), fluting = Color(0xFF3A2D20),
    ink = Color(0xFFEEDDC3), ink2 = Color(0xFFC2AC8C),
    label = Color(0xFF33281D), labelInk = Color(0xFFF6E9D4), labelInk2 = Color(0xFFCDB89A),
    tape = Color(0xFFD9A63C), onTape = Color(0xFF1B1610),
    sticker = Color(0xFFFF7A2E), onSticker = Color(0xFF1B1610), stickerEdge = Color(0xFFE9DCC6),
    primary = Color(0xFFEEDDC3), onPrimary = Color(0xFF1B1610),
    doneNumber = Color(0xFF8C7559), labelEdge = Color.Transparent, isDark = true,
)

/** Everything printed on label stock: black on white, the most legible thing a screen can do outdoors. */
val Sun = MilkrunColors(
    ground = Color(0xFFFFFFFF), fluting = Color(0xFFC9A57A),
    ink = Color(0xFF000000), ink2 = Color(0xFF2B2B2B),
    label = Color(0xFFFFFFFF), labelInk = Color(0xFF000000), labelInk2 = Color(0xFF333333),
    tape = Color(0xFFFFC400), onTape = Color(0xFF000000),
    sticker = Color(0xFFFF5A00), onSticker = Color(0xFF000000), stickerEdge = Color(0xFF000000),
    primary = Color(0xFF000000), onPrimary = Color(0xFFFFFFFF),
    doneNumber = Color(0xFF555555), labelEdge = Color(0xFF000000), isDark = false,
)

val LocalMilkrunColors = staticCompositionLocalOf { Kraft }

/** The stencil face for stop numbers and the remaining count only. Everything else is the system sans. */
val Stencil = FontFamily(
    Font(
        R.font.big_shoulders_stencil,
        FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
    Font(
        R.font.big_shoulders_stencil,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

object StencilType {
    /** The remaining count in the header. */
    val count =
        TextStyle(
            fontFamily = Stencil,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 72.sp,
            lineHeight = 64.sp,
            letterSpacing = 0.sp,
        )

    /** The next stop's number. */
    val hero = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.ExtraBold, fontSize = 96.sp, lineHeight = 84.sp)

    /** A stop number in a list row. */
    val row = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp)

    /** A finished stop's number: smaller, so done rows fall back by size as well as ink. */
    val done = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp)
}

/** Material 3 roles on the system sans, tuned for arm's length: body never below 15sp. */
private val typography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Square corners: labels are cut, buttons are blocks. Stickers draw their own die-cut corners
 * (6dp, and 4dp for the row tag).
 */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
)

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
}

object Target {
    /** Gloved thumb: the primary action of a screen. */
    val primary = 64.dp

    /** Every other action a driver takes mid-stop. */
    val action = 56.dp
}

@Composable
fun MilkrunTheme(scheme: Scheme? = null, content: @Composable () -> Unit) {
    val c = when (scheme ?: if (isSystemInDarkTheme()) Scheme.NIGHT else Scheme.KRAFT) {
        Scheme.KRAFT -> Kraft
        Scheme.NIGHT -> Night
        Scheme.SUN -> Sun
    }
    val material = if (c.isDark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink, onSurfaceVariant = c.ink2, surfaceVariant = c.label,
            surfaceContainerLow = c.label, surfaceContainer = c.label, surfaceContainerHigh = c.label,
            secondaryContainer = c.ink, onSecondaryContainer = c.ground, outline = c.ink2, outlineVariant = c.fluting,
            error = c.sticker, onError = c.onSticker,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink, onSurfaceVariant = c.ink2, surfaceVariant = c.label,
            surfaceContainerLow = c.label, surfaceContainer = c.label, surfaceContainerHigh = c.label,
            secondaryContainer = c.ink, onSecondaryContainer = c.label, outline = c.ink2, outlineVariant = c.fluting,
            error = c.sticker, onError = c.onSticker,
        )
    }
    CompositionLocalProvider(LocalMilkrunColors provides c) {
        MaterialTheme(colorScheme = material, typography = typography, shapes = shapes, content = content)
    }
}
