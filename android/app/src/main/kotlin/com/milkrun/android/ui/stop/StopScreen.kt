package com.milkrun.android.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milkrun.android.ui.components.BlockButton
import com.milkrun.android.ui.components.ExceptionSticker
import com.milkrun.android.ui.components.Fluting
import com.milkrun.android.ui.components.OutlineBlockButton
import com.milkrun.android.ui.components.StencilNumber
import com.milkrun.android.ui.components.TapeTab
import com.milkrun.android.ui.theme.LocalMilkrunColors
import com.milkrun.android.ui.theme.Space
import com.milkrun.android.ui.theme.StencilType
import com.milkrun.android.ui.theme.Target
import com.milkrun.model.AttemptReason
import com.milkrun.model.HandoffMethod
import com.milkrun.model.ParcelId
import com.milkrun.model.StopStatus
import com.milkrun.presentation.Copy
import com.milkrun.presentation.ParcelRow
import com.milkrun.presentation.StopDetailState

/**
 * Working one stop: parcels on label stock, reversible actions on top, and the irreversible
 * Deliver alone at the bottom, separated by empty space.
 */
@Composable
fun StopScreen(
    state: StopDetailState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onArrive: () -> Unit,
    onScan: (ParcelId) -> Unit,
    onDeliver: (HandoffMethod) -> Unit,
    onAttempt: (AttemptReason) -> Unit,
    onSaveNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalMilkrunColors.current
    val card = state.card
    var handoff by rememberSaveable { mutableStateOf(HandoffMethod.FRONT_DOOR) }
    var reasonsOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = c.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                // The 96sp stencil number below is the title; repeating "Stop 02" above it was noise.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to route")
                    }
                },
                actions = { TapeTab(card.unsynced, Modifier.padding(end = Space.l)) },
                expandedHeight = 48.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.ground,
                    titleContentColor = c.ink,
                    navigationIconContentColor = c.ink,
                ),
            )
        },
        bottomBar = {
            if (state.canDeliver || state.canAttempt || state.canArrive) {
                ActionDock(state, handoff, onHandoff = { handoff = it }, onArrive = onArrive, onCantDeliver = {
                    reasonsOpen =
                        true
                }, onDeliver = { onDeliver(handoff) })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(horizontal = Space.gutter), verticalAlignment = Alignment.Top) {
                StencilNumber(
                    card.number,
                    StencilType.hero,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription =
                            "Stop ${card.number}"
                    },
                )
                Spacer(Modifier.width(Space.l))
                Column(Modifier.padding(top = Space.s)) {
                    Text(card.address, style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Spacer(Modifier.height(Space.xs))
                    Text(card.statusLabel, style = MaterialTheme.typography.bodyLarge, color = c.ink2)
                }
            }
            if (card.deliveredAfterCancel) {
                ExceptionSticker(
                    Copy.DELIVERED_AFTER_CANCEL,
                    body = "Dispatch cancelled this stop while you were delivering it. Your delivery stands.",
                    onDismiss = null,
                    modifier = Modifier.padding(Space.gutter),
                )
            }
            Spacer(Modifier.height(Space.l))
            Text(
                "Parcels · ${state.parcels.count { it.scanned }} of ${state.parcels.size} scanned",
                style = MaterialTheme.typography.titleSmall,
                color = c.ink2,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s),
            )
            Surface(
                color = c.label,
                shadowElevation = 1.dp,
                border = if (c.labelEdge.alpha >
                    0f
                ) {
                    androidx.compose.foundation.BorderStroke(2.dp, c.labelEdge)
                } else {
                    null
                },
                modifier = Modifier.padding(horizontal = Space.gutter).fillMaxWidth(),
            ) {
                Column {
                    state.parcels.forEachIndexed { i, p ->
                        ParcelLine(p, enabled = state.canScan && !p.scanned, onScan = { onScan(p.id) })
                        if (i < state.parcels.lastIndex) Fluting()
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
            NoteField(card.note, onSaveNote)
            Spacer(Modifier.height(Space.xxl))
        }
    }

    if (reasonsOpen) {
        ModalBottomSheet(onDismissRequest = {
            reasonsOpen = false
        }, containerColor = c.label, shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) {
            Column(
                Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text("Why couldn't you deliver?", style = MaterialTheme.typography.titleMedium, color = c.labelInk)
                Spacer(Modifier.height(Space.xs))
                AttemptReason.entries.forEach { r ->
                    OutlineBlockButton(Copy.attemptOption(r), onClick = {
                        reasonsOpen = false
                        onAttempt(r)
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ParcelLine(p: ParcelRow, enabled: Boolean, onScan: () -> Unit) {
    val c = LocalMilkrunColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Target.action + 8.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Scan parcel ${p.shortCode}",
                onClick = onScan,
            )
            .padding(horizontal = Space.l, vertical = Space.s)
            .semantics(mergeDescendants = true) { stateDescription = if (p.scanned) "Scanned" else "Not scanned" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p.description.replaceFirstChar {
                    it.uppercase()
                },
                style = MaterialTheme.typography.bodyLarge,
                color = c.labelInk,
            )
            Text(
                p.id.value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = c.labelInk2,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Label ending ${p.shortCode.toList().joinToString(" ")}"
                },
            )
        }
        if (p.scanned) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = c.labelInk, modifier = Modifier.size(32.dp))
        } else {
            Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = if (enabled) c.labelInk else c.labelInk2,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(Space.s))
            Text(
                if (enabled) "Tap to scan" else "Not scanned",
                style = MaterialTheme.typography.labelMedium,
                color = c.labelInk2,
            )
        }
    }
}

@Composable
private fun NoteField(saved: String, onSave: (String) -> Unit) {
    val c = LocalMilkrunColors.current
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 500) text = it },
        label = { Text("Note for this address") },
        placeholder = { Text("Gate code, where you left it") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text != saved) onSave(text) }),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.label, unfocusedContainerColor = c.label,
            focusedBorderColor = c.ink, unfocusedBorderColor = c.ink2,
            focusedTextColor = c.labelInk, unfocusedTextColor = c.labelInk,
            focusedLabelColor = c.ink, unfocusedLabelColor = c.ink2,
            focusedPlaceholderColor = c.labelInk2, unfocusedPlaceholderColor = c.labelInk2,
        ),
        modifier = Modifier.padding(horizontal = Space.gutter).fillMaxWidth(),
    )
}

/** Reversible actions above; handoff choice; a gap; then Deliver, alone. */
@Composable
private fun ActionDock(
    state: StopDetailState,
    handoff: HandoffMethod,
    onHandoff: (HandoffMethod) -> Unit,
    onArrive: () -> Unit,
    onCantDeliver: () -> Unit,
    onDeliver: () -> Unit,
) {
    val c = LocalMilkrunColors.current
    Surface(color = c.label, shadowElevation = 6.dp) {
        Column(Modifier.navigationBarsPadding().padding(top = Space.m)) {
            Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (state.canArrive && state.card.status == StopStatus.PLANNED) {
                    OutlineBlockButton("Arrived", onArrive, Modifier.weight(1f))
                }
                if (state.canAttempt) OutlineBlockButton("Can't deliver", onCantDeliver, Modifier.weight(1f))
            }
            if (state.canDeliver) {
                Spacer(Modifier.height(Space.m))
                Text(
                    "Left at",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.labelInk2,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                )
                HandoffGrid(handoff, onHandoff, Modifier.padding(horizontal = Space.gutter))
                // The deliberate gap: the irreversible action never sits within a thumb's slip of another.
                Spacer(Modifier.height(Space.xxl))
                BlockButton("Deliver ${Copy.parcels(state.parcels.size)}", onClick = onDeliver)
            } else {
                Spacer(Modifier.height(Space.m))
            }
        }
    }
}

/**
 * Where the parcels were left, as four large blocks (2 x 2) rather than a segmented row: every
 * label stays whole at 1.3x font scale, and each target is a gloved thumb wide.
 */
@Composable
private fun HandoffGrid(selected: HandoffMethod, onSelect: (HandoffMethod) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMilkrunColors.current
    // Selected is a heavier ink frame with a check, never an ink fill: a filled block is reserved for Deliver.
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        HandoffMethod.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                pair.forEach { h ->
                    val on = h == selected
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = Target.action)
                            .background(c.label)
                            .border(if (on) 4.dp else 1.5.dp, if (on) c.labelInk else c.labelInk2)
                            .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(h) })
                            .padding(horizontal = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (on) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = c.labelInk,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(Space.s))
                        }
                        Text(
                            Copy.handoffOption(h),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = c.labelInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
