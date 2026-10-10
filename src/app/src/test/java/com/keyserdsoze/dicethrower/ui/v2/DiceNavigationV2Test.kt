package com.keyserdsoze.dicethrower.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiceNavigationV2Test {
    @Test
    fun androidBackUsesAppHierarchy() {
        assertNull(previousRouteFor(RouteV2.CHARACTERS))
        assertEquals(RouteV2.CHARACTERS, previousRouteFor(RouteV2.DICE_LIBRARY))
        assertEquals(RouteV2.CHARACTERS, previousRouteFor(RouteV2.CHARACTER))
        assertEquals(RouteV2.CHARACTER, previousRouteFor(RouteV2.GROUP))
        assertEquals(RouteV2.CHARACTER, previousRouteFor(RouteV2.ROLL, hasRollReturnGroup = false))
        assertEquals(RouteV2.GROUP, previousRouteFor(RouteV2.ROLL, hasRollReturnGroup = true))
        assertEquals(RouteV2.CHARACTERS, previousRouteFor(RouteV2.SETTINGS))
        assertEquals(RouteV2.CHARACTER, previousRouteFor(RouteV2.LOGS))
    }
}
