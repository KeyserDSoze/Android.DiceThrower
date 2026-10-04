package com.keyserdsoze.dicethrower.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveHttpPolicyTest {
    @Test
    fun authenticationAndAuthorizationFailuresAreDistinctFromTransientFailures() {
        assertTrue(DriveHttpErrorPolicy.toException(401, "") is CloudAuthorizationException)
        assertTrue(DriveHttpErrorPolicy.toException(403, "permission denied") is CloudAuthorizationException)
        assertTrue(DriveHttpErrorPolicy.toException(403, "rateLimitExceeded") is CloudTransientException)
        assertTrue(DriveHttpErrorPolicy.toException(429, "") is CloudTransientException)
        assertTrue(DriveHttpErrorPolicy.toException(503, "") is CloudTransientException)
    }

    @Test
    fun backoffIsBoundedAndIncludesOnlyBoundedJitter() {
        assertEquals(1_000L, DriveRetryPolicy.delayMillis(0, 0))
        assertEquals(2_250L, DriveRetryPolicy.delayMillis(1, 250))
        assertEquals(8_250L, DriveRetryPolicy.delayMillis(99, 999))
    }
}
