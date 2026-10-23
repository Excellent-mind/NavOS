package com.excellentmindblow.navos.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class BearingsParamTest {
    @Test
    fun firstPointFacesTheHeadingTheRestAreFree() {
        assertEquals("&bearings=90,60;", RoutingService.bearingsParam(90.4, 2))
        assertEquals("&bearings=350,60;;", RoutingService.bearingsParam(-10.0, 3))
        assertEquals("", RoutingService.bearingsParam(null, 2))
    }
}
