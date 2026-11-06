package com.milkrun.model

import kotlin.test.Test
import kotlin.test.assertEquals

class IdsTest {
    @Test fun routeIdKeepsValue() = assertEquals("R-1", RouteId("R-1").value)
}
