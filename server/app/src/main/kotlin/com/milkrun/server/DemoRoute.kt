package com.milkrun.server

import com.milkrun.model.GeoPoint
import com.milkrun.model.ParcelId
import com.milkrun.model.RouteId
import com.milkrun.model.StopId
import com.milkrun.protocol.PlanRouteRequest
import com.milkrun.protocol.PlannedParcel
import com.milkrun.protocol.PlannedStop

/**
 * Synthetic demo route over public Seattle addresses (no customer data). Used by the dev session
 * endpoint and the trace tools.
 */
public object DemoRoute {
    public val depot: GeoPoint = GeoPoint(47.5800, -122.3300)

    private val addresses = listOf(
        Triple("410 Terry Ave N", GeoPoint(47.6223, -122.3365), listOf("medium box", "padded mailer")),
        Triple("2021 7th Ave", GeoPoint(47.6153, -122.3383), listOf("small box")),
        Triple("1st Ave S & S Royal Brougham Way", GeoPoint(47.5917, -122.3343), listOf("large box")),
        Triple("300 Pine St", GeoPoint(47.6114, -122.3392), listOf("padded mailer", "small box", "envelope")),
        Triple("1201 3rd Ave", GeoPoint(47.6077, -122.3360), listOf("envelope")),
        Triple("400 Broad St", GeoPoint(47.6205, -122.3493), listOf("medium box")),
        Triple("1000 4th Ave", GeoPoint(47.6067, -122.3325), listOf("small box", "small box")),
        Triple("500 Fairview Ave N", GeoPoint(47.6236, -122.3335), listOf("large box")),
        Triple("85 Pike St", GeoPoint(47.6094, -122.3417), listOf("padded mailer")),
        Triple("701 5th Ave", GeoPoint(47.6045, -122.3305), listOf("medium box", "envelope")),
        Triple("1600 E Olive Way", GeoPoint(47.6190, -122.3245), listOf("small box")),
        Triple("999 3rd Ave", GeoPoint(47.6050, -122.3349), listOf("envelope")),
    )

    public fun request(route: RouteId): PlanRouteRequest = PlanRouteRequest(
        routeId = route,
        depot = depot,
        stops = addresses.mapIndexed { i, (addr, loc, parcels) ->
            PlannedStop(
                id = StopId("S-%03d".format(i + 1)),
                address = addr,
                location = loc,
                parcels = parcels.mapIndexed { k, d ->
                    PlannedParcel(ParcelId("TBA3188402%03d%02d".format(i + 1, k + 1)), d)
                },
            )
        },
    )
}
