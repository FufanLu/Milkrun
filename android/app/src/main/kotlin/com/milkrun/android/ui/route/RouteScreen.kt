package com.milkrun.android.ui.route

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milkrun.android.ui.components.BlockButton
import com.milkrun.android.ui.components.ExceptionSticker
import com.milkrun.android.ui.components.Fluting
import com.milkrun.android.ui.components.MarkerStrike
import com.milkrun.android.ui.components.StencilMark
import com.milkrun.android.ui.components.StencilMarkKind
import com.milkrun.android.ui.components.StencilNumber
import com.milkrun.android.ui.components.StickerTag
import com.milkrun.android.ui.components.TapeStrip
import com.milkrun.android.ui.components.TapeTab
import com.milkrun.android.ui.theme.LocalMilkrunColors
import com.milkrun.android.ui.theme.Space
import com.milkrun.android.ui.theme.StencilType
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.presentation.Copy
import com.milkrun.presentation.Notice
import com.milkrun.presentation.RouteUiState
import com.milkrun.presentation.StopCard
import com.milkrun.presentation.SyncBanner

/**
 * The route: how many are left, what is waiting for signal, the next stop at full strength, the
 * rest receding. Stateless; [RouteRoute] in the nav graph feeds it from the shared screen model.
 */
@Composable
fun RouteScreen(
    state: RouteUiState,
    sunMode: Boolean,
    onToggleSun: () -> Unit,
    onOpenStop: (StopId) -> Unit,
    onArrive: (StopId) -> Unit,
    onDismiss: (Notice) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalMilkrunColors.current
    Surface(color = c.ground, modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl), modifier = Modifier.fillMaxSize()) {
                item(key = "header") { Header(state, sunMode, onToggleSun) }
                item(key = "sync") { SyncLine(state.sync) }
                items(state.notices, key = { "notice-${it.seq}" }) { n ->
                    ExceptionSticker(
                        title = n.title,
                        body = n.body,
                        onDismiss = { onDismiss(n) },
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s).animateItem(),
                    )
                }
                val next = state.next
                if (next != null) {
                    item(key = "next") {
                        NextStop(next, onOpen = { onOpenStop(next.id) }, onArrive = { onArrive(next.id) })
                    }
                } else if (state.loaded) {
                    item(key = "finished") { Finished(state) }
                }
                items(state.upcoming, key = { it.id.value }) { s ->
                    StopRow(s, onClick = { onOpenStop(s.id) }, modifier = Modifier.animateItem())
                }
                if (state.done.isNotEmpty()) {
                    item(key = "done-heading") {
                        Text(
                            "Done · ${state.done.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.ink2,
                            modifier = Modifier.padding(
                                start = Space.gutter,
                                top = Space.xl,
                                bottom = Space.s,
                            ).semantics {
                                heading()
                            },
                        )
                        Fluting()
                    }
                    items(state.done, key = { "done-${it.id.value}" }) { s ->
                        StopRow(s, onClick = { onOpenStop(s.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: RouteUiState, sunMode: Boolean, onToggleSun: () -> Unit) {
    val c = LocalMilkrunColors.current
    Column(Modifier.padding(start = Space.gutter, end = Space.s, top = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.routeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = c.ink2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconToggleButton(checked = sunMode, onCheckedChange = { onToggleSun() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (sunMode) Icons.Filled.WbSunny else Icons.Outlined.WbSunny,
                    contentDescription = if (sunMode) "Sunlight mode on" else "Sunlight mode off",
                    tint = c.ink,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${state.remaining} stops left, " +
                    "${state.delivered} delivered, ${state.attempted} not delivered"
            },
        ) {
            StencilNumber("${state.remaining}", StencilType.count)
            Spacer(Modifier.width(Space.s))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("left", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(
                    "${state.delivered} delivered · ${state.attempted} not delivered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
            }
        }
        Spacer(Modifier.height(Space.m))
    }
}

@Composable
private fun SyncLine(sync: SyncBanner) {
    when (sync) {
        is SyncBanner.Waiting -> TapeStrip(sync.title, sync.detail, Modifier.animateContentSize())

        is SyncBanner.AllSent -> Column {
            Fluting()
            Text(
                Copy.ALL_SENT,
                style = MaterialTheme.typography.labelMedium,
                color = LocalMilkrunColors.current.ink2,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
        }
    }
}

/** The one lifted label on the kraft: the stop the driver is working or driving to. */
@Composable
private fun NextStop(s: StopCard, onOpen: () -> Unit, onArrive: () -> Unit) {
    val c = LocalMilkrunColors.current
    Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.l)) {
        Surface(
            color = c.label,
            shadowElevation = 2.dp,
            // In Sun the label and the ground are both white; an ink edge keeps the one lifted label.
            border = if (c.labelEdge.alpha > 0f) BorderStroke(2.dp, c.labelEdge) else null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable(
                        onClickLabel = "Open stop ${s.number}",
                        onClick = onOpen,
                    ).padding(Space.l),
                    verticalAlignment = Alignment.Top,
                ) {
                    StencilNumber(
                        text = s.number,
                        style = StencilType.hero,
                        color = c.labelInk,
                        modifier = Modifier.semantics { contentDescription = "Next stop, number ${s.number}" },
                    )
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f).padding(top = Space.s)) {
                        Text(s.address, style = MaterialTheme.typography.titleLarge, color = c.labelInk)
                        Spacer(Modifier.height(Space.xs))
                        Text(s.statusLabel, style = MaterialTheme.typography.bodyLarge, color = c.labelInk2)
                        if (s.note.isNotBlank()) {
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "Note: ${s.note}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.labelInk2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (s.status == StopStatus.PLANNED) {
                    BlockButton("Arrived", onClick = onArrive)
                } else {
                    BlockButton("Scan and deliver", onClick = onOpen)
                }
            }
        }
        // Tape stuck across the label's top edge, the way it seals a box: half on the label, half on the kraft.
        TapeTab(s.unsynced, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-12).dp))
    }
}

@Composable
private fun StopRow(s: StopCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    val finished =
        s.status == StopStatus.DELIVERED || s.status == StopStatus.ATTEMPTED || s.status == StopStatus.CANCELLED
    // The next stop alone is at full strength: upcoming numbers step down, finished rows recede entirely.
    val numberInk = if (finished) c.doneNumber else c.ink2
    val textInk = if (finished) c.ink2 else c.ink
    Column(modifier) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (finished) 56.dp else 72.dp)
                    .clickable(onClickLabel = "Open stop ${s.number}", onClick = onClick)
                    // Room on the right for the tape, so it never covers the address or the check.
                    .padding(
                        start = Space.gutter,
                        end = Space.gutter + if (s.unsynced) 72.dp else 0.dp,
                        top = if (finished) Space.s else Space.m,
                        bottom = if (finished) Space.s else Space.m,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
                    StencilNumber(s.number, if (finished) StencilType.done else StencilType.row, color = numberInk)
                    if (s.status == StopStatus.CANCELLED) MarkerStrike(Modifier.width(36.dp).height(30.dp))
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.address,
                        style = with(MaterialTheme.typography) { if (finished) bodyMedium else bodyLarge },
                        color = textInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(s.statusLabel, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    if (s.deliveredAfterCancel) {
                        Spacer(Modifier.height(Space.s))
                        StickerTag(Copy.DELIVERED_AFTER_CANCEL)
                    }
                }
                when (s.status) {
                    StopStatus.DELIVERED -> StencilMark(
                        StencilMarkKind.CHECK,
                        c.doneNumber,
                        Modifier.padding(top = Space.xs).size(28.dp),
                    )

                    StopStatus.ATTEMPTED -> StencilMark(
                        StencilMarkKind.CROSS,
                        c.doneNumber,
                        Modifier.padding(top = Space.xs).size(26.dp),
                    )

                    else -> Unit
                }
            }
            TapeTab(s.unsynced, Modifier.align(Alignment.TopEnd).padding(top = Space.m, end = Space.gutter))
        }
        Fluting()
    }
}

@Composable
private fun Finished(state: RouteUiState) {
    val c = LocalMilkrunColors.current
    Surface(color = c.label, shadowElevation = 2.dp, modifier = Modifier.padding(Space.gutter).fillMaxWidth()) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                Copy.ROUTE_DONE_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = c.labelInk,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                Copy.routeDoneBody(state.delivered, state.attempted),
                style = MaterialTheme.typography.bodyLarge,
                color = c.labelInk2,
            )
        }
    }
}
