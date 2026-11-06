---
version: 1
slug: "m-milkrun-android-ui-route-routescreen-kt-ae92d359"
primary_target: "android/app/src/main/kotlin/com/milkrun/android/ui/route/RouteScreen.kt"
related_targets: ["ios/Milkrun/Route/RouteView.swift"]
---

# Surface brief: driver app, route and stop screens (Android Compose first, then iOS SwiftUI)

Mode: Operate. Platforms: adaptive (Material 3 structure on Android, HIG structure on iOS; one world themed through each platform's open layer).

Audience and task: a delivery driver mid-shift, one hand busy, sometimes gloved, in sun or a dark garage. Glance: what is next, how much is left, is anything still waiting for signal. Act: arrive, scan parcels, deliver (handoff) or record a failed attempt (reason), optional note. Learn: when the server ruled against something, what happened and what to do.

States the surfaces must carry: planned, arrived, delivered, attempted (reason), cancelled by dispatch, delivered-after-cancel (dispatch notified), unsynced (per stop), route-level waiting / syncing / retrying / all sent, rejection unseen.

Decision record: unattended run (user: proceed on your own). Assigned direction taken; no decision page served. Build path: code-led (no image generation on this machine).

## Direction contract

THESIS: A route is a stack of boxes to move, so the screen speaks the packing room's own marks: stencilled numbers, packing tape, exception stickers, marker strikes. It refuses the category default of white list cells with colored status chips.

OWN-WORLD: Corrugated kraft is the ground. Stencil ink is near-black. Parcels sit on label-white stock. Translucent amber packing tape means "waiting for signal". Safety-orange exception stickers carry the server's rulings. A thick black marker strike means cancelled. Stop numbers and the remaining count are set in a condensed stencil face; everything else uses the platform sans. Corners are square, the only rule is the 1-pixel corrugation fluting line, and there are no shadows except a label's lift.

STORY: The driver sees the next stop as the one lifted label on the kraft, with how many are left in big stencil and a tape strip if anything is unsent. They tap in, tap or scan each parcel, and press one large Deliver. If dispatch or another device intervened, an orange sticker on that stop explains in plain words and says what happens next.

FIRST VIEWPORT: Phone portrait. The top band is kraft, with the route id small and "9 left" in 72sp stencil; delivered and attempted counts sit beside it in 16sp. Under it runs a full-width amber tape strip, "3 waiting for signal · retrying", which collapses to a thin "All sent" fluting line when empty. Next comes the next stop as a label-white panel: its 96sp stencil number, the address at 22sp, the parcel count, and a 64dp primary "Arrived" bar across the panel's foot. Below are the remaining stops as kraft rows (40sp stencil number, address, a tape tab when unsynced). Done stops follow at 55% ink with a stencilled check.

FORM: Tote stencil and parcel tape, position 7 of 7 on the grounded list. Seed fcc3e8d7. Raises, from declined challengers:
- Dark-first console: the irreversible Deliver is isolated by empty space from reversible actions.
- Streaming wall: the next stop alone is at full strength and the rest recede.
- Catalog sleeve: the stop number is the headline.
- Exposure record: no state is carried by color alone; each state has its own physical mark.

Competitive alternates kept: jet-age ticket wallet, orienteering map.

Signature interaction and motion: recording an action lays a tape tab onto that stop (200 ms slide-in). When the server confirms it, the tape peels off (180 ms slide plus fade). A ruling slaps an exception sticker on (150 ms scale from 1.06). Reduce Motion turns all three into crossfades.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
