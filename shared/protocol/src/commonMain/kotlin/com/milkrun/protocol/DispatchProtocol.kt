package com.milkrun.protocol

import com.milkrun.model.DispatcherCommand
import com.milkrun.model.GeoPoint
import com.milkrun.model.ParcelId
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import kotlinx.serialization.Serializable

/**
 * Dispatcher-facing API (station operations, not the driver app).
 *
 * ```
 * POST /v1/dispatch/routes                       PlanRouteRequest -> PlanRouteResponse
 * GET  /v1/dispatch/routes/{routeId}             -> RouteSnapshot
 * POST /v1/dispatch/routes/{routeId}/commands    DispatcherCommand -> CommandResponse
 * ```
 */
public object DispatchApi {
    public const val ROUTES_PATH: String = "/v1/dispatch/routes"

    public fun routePath(route: RouteId): String = "$ROUTES_PATH/${route.value}"

    public fun commandsPath(route: RouteId): String = "${routePath(route)}/commands"
}

@Serializable
public data class PlannedStop(
    val id: StopId,
    val address: String,
    val location: GeoPoint,
    val parcels: List<PlannedParcel>,
)

@Serializable
public data class PlannedParcel(val id: ParcelId, val description: String)

/** Stops arrive unordered; the server sequences them (nearest neighbour + 2-opt from the depot). */
@Serializable
public data class PlanRouteRequest(val routeId: RouteId, val depot: GeoPoint, val stops: List<PlannedStop>)

@Serializable
public data class PlanRouteResponse(
    val routeId: RouteId,
    val order: List<StopId>,
    val nearestNeighbourKm: Double,
    val optimisedKm: Double,
)

@Serializable
public data class RouteSnapshot(val routeId: RouteId, val version: Long, val stops: List<Stop>)

@Serializable
public data class CommandResponse(val accepted: Boolean, val reason: RejectReason? = null)

/** Wrapper so the command keeps its `type` discriminator on the wire. */
@Serializable
public data class CommandRequest(val command: DispatcherCommand)

/** `POST /v1/dev/session` (dev mode only): a fresh demo route and tokens for it. */
@Serializable
public data class DevSession(val routeId: RouteId, val driverToken: String, val dispatcherToken: String) {
    public companion object {
        public const val PATH: String = "/v1/dev/session"
    }
}
