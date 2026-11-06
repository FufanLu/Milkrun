package com.milkrun.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milkrun.android.ui.theme.LocalMilkrunColors
import com.milkrun.android.ui.theme.Space
import com.milkrun.android.ui.theme.Target

/** True when the user asked the system to remove animations: every mark then crossfades. */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            ctx.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) ==
            0f
    }
}

/** A stencilled number: the headline of a stop, or the remaining count. */
@Composable
fun StencilNumber(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = LocalMilkrunColors.current.ink,
) {
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1)
}

/** The 1dp corrugation line: the only rule in the system. */
@Composable
fun Fluting(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalMilkrunColors.current.fluting))
}

/** Zig-zag cut ends, as packing tape tears off the dispenser. */
private val TornEnds = GenericShape { size, _ ->
    val tooth = size.height / 6f
    val depth = 5f
    moveTo(depth, 0f)
    lineTo(size.width - depth, 0f)
    var y = 0f
    var out = true
    while (y < size.height) {
        y = (y + tooth).coerceAtMost(size.height)
        lineTo(if (out) size.width else size.width - depth, y)
        out = !out
    }
    lineTo(depth, size.height)
    y = size.height
    out = true
    while (y > 0f) {
        y = (y - tooth).coerceAtLeast(0f)
        lineTo(if (out) 0f else depth, y)
        out = !out
    }
    close()
}

/**
 * The tape strip under the route header: work recorded on the phone that the server has not
 * confirmed. Calm by design: waiting is not an error.
 */
@Composable
fun TapeStrip(title: String, detail: String, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        // The box's fluting rule runs under the tape: the one thing that proves the tape is tape.
        // Kept in the strip's lower third: a line through the words would read as a marker strike.
        Box(
            Modifier.align(
                Alignment.BottomCenter,
            ).padding(bottom = 10.dp).fillMaxWidth().height(2.dp).background(c.ink2),
        )
        // Laid across the box, ends torn at the gutter.
        Box(
            Modifier
                .padding(horizontal = Space.gutter)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(TornEnds)
                .background(c.tape.copy(alpha = TAPE_ALPHA))
                .semantics(mergeDescendants = true) { contentDescription = "$title. $detail" },
        ) {
            Canvas(Modifier.matchParentSize()) {
                var y = 6f
                while (y < size.height) {
                    drawLine(c.onTape.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                    y += 9f
                }
            }
            Row(
                Modifier.padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.onTape)
                Spacer(Modifier.weight(1f))
                Text(detail, style = MaterialTheme.typography.labelMedium, color = c.onTape)
            }
        }
    }
}

/** Opacity of packing tape over its ground; text on it stays at or above 5.5:1 (Night is the lowest). */
const val TAPE_ALPHA: Float = 0.78f

/**
 * A short piece of tape stuck across a row: this stop has unconfirmed changes. Slides on when a
 * change is recorded and peels off when the server confirms it.
 */
@Composable
fun TapeTab(visible: Boolean, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    val still = reduceMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (still) fadeIn(tween(150)) else slideInHorizontally(tween(200)) { it } + fadeIn(tween(120)),
        exit = if (still) fadeOut(tween(150)) else slideOutHorizontally(tween(180)) { -it / 2 } + fadeOut(tween(180)),
    ) {
        Box(
            Modifier
                .rotate(-4f)
                .clip(TornEnds)
                .background(c.tape.copy(alpha = TAPE_ALPHA))
                .padding(horizontal = Space.m, vertical = Space.xs)
                .semantics { contentDescription = "Not sent yet" },
        ) {
            Text(
                "Unsent",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = c.onTape,
            )
        }
    }
}

/**
 * Safety-orange exception sticker: the server ruled on something. Slapped on with a slight tilt;
 * says what happened and what to do, and stays until acknowledged.
 */
@Composable
fun ExceptionSticker(title: String, body: String?, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    val still = reduceMotion()
    var landed by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { landed = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (landed) 1f else 1.06f,
        tween(150),
        label = "slap",
    )
    val dieCut = RoundedCornerShape(6.dp)
    Row(
        modifier
            .padding(horizontal = Space.s)
            .rotate(if (still) 0f else -1.2f)
            .scale(scale)
            .clip(dieCut)
            .background(c.sticker)
            .border(2.dp, c.stickerEdge, dieCut)
            .padding(start = Space.m, end = Space.s, top = Space.m, bottom = if (onDismiss == null) Space.m else 0.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        HazardMark(Modifier.padding(top = 2.dp).size(22.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.onSticker,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            if (body != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSticker,
                    modifier = Modifier.padding(end = Space.s),
                )
            }
            if (onDismiss != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Got it", style = MaterialTheme.typography.labelLarge, color = c.onSticker)
                    }
                }
            }
        }
    }
}

/**
 * The printed mark on an exception label: an ink diamond with a cut-out exclamation, so the
 * ruling reads without the orange.
 */
@Composable
fun HazardMark(modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    Canvas(modifier.semantics { contentDescription = "Exception" }) {
        val w = size.width
        val h = size.height
        val diamond = Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w, h / 2)
            lineTo(w / 2, h)
            lineTo(0f, h / 2)
            close()
        }
        drawPath(diamond, c.onSticker)
        val bar = w * 0.11f
        drawRect(c.sticker, topLeft = Offset(w / 2 - bar / 2, h * 0.24f), size = Size(bar, h * 0.32f))
        drawRect(c.sticker, topLeft = Offset(w / 2 - bar / 2, h * 0.64f), size = Size(bar, bar))
    }
}

/** Stencilled marks for finished rows: a check (delivered) or a cross (not delivered), each cut with a bridge. */
enum class StencilMarkKind { CHECK, CROSS }

@Composable
fun StencilMark(kind: StencilMarkKind, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier.semantics {
            contentDescription = if (kind ==
                StencilMarkKind.CHECK
            ) {
                "Delivered"
            } else {
                "Not delivered"
            }
        },
    ) {
        val s = size.minDimension
        val stroke = s * 0.16f
        val gap = s * 0.07f
        fun seg(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Butt)
        when (kind) {
            StencilMarkKind.CHECK -> {
                // Short arm, a stencil bridge, then the long arm.
                seg(Offset(s * 0.12f, s * 0.52f), Offset(s * 0.36f - gap, s * 0.76f - gap))
                seg(Offset(s * 0.38f + gap * 0.3f, s * 0.74f), Offset(s * 0.90f, s * 0.20f))
            }

            StencilMarkKind.CROSS -> {
                seg(Offset(s * 0.16f, s * 0.16f), Offset(s * 0.84f, s * 0.84f))
                // The second stroke is bridged where it crosses the first.
                seg(Offset(s * 0.84f, s * 0.16f), Offset(s * 0.5f + gap, s * 0.5f - gap))
                seg(Offset(s * 0.5f - gap, s * 0.5f + gap), Offset(s * 0.16f, s * 0.84f))
            }
        }
    }
}

/** A thick marker line through a stop number: cancelled by dispatch. */
@Composable
fun MarkerStrike(modifier: Modifier = Modifier) {
    val ink = LocalMilkrunColors.current.ink
    Canvas(modifier) {
        drawLine(
            ink,
            Offset(0f, size.height * 0.62f),
            Offset(size.width, size.height * 0.38f),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A full-width block button, the stencil ink as the fill. The screen's one irreversible action. */
@Composable
fun BlockButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalMilkrunColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Target.primary),
        shape = RoundedCornerShape(0.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = c.primary,
            contentColor = c.onPrimary,
            disabledContainerColor = c.fluting,
            disabledContentColor = c.ink2,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** Outlined companion to [BlockButton] for reversible actions. */
@Composable
fun OutlineBlockButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalMilkrunColors.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Target.action),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (enabled) c.ink else c.fluting),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small exception sticker for a list row: one line, no action. */
@Composable
fun StickerTag(text: String, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = c.onSticker,
        modifier = modifier
            .rotate(if (reduceMotion()) 0f else -1.5f)
            .clip(RoundedCornerShape(4.dp))
            .background(c.sticker)
            .border(1.5.dp, c.stickerEdge, RoundedCornerShape(4.dp))
            .padding(horizontal = Space.s, vertical = Space.xs),
    )
}
