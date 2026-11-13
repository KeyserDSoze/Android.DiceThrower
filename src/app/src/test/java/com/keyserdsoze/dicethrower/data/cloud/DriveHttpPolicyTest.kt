package com.keyserdsoze.dicethrower.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun quotaAndUsageLimitsAreTemporaryAndNeverDisconnectTheAccount() {
        listOf(
            "dailyLimitExceeded", "dailyLimitExceededUnreg", "quotaExceeded", "storageQuotaExceeded",
            "numChildrenInNonRootLimitExceeded", "activeItemCreationLimitExceeded",
        ).forEach { reason ->
            val failure = DriveHttpErrorPolicy.toException(403, driveError(reason))
            assertTrue(reason, failure is CloudTransientException)
            // Retrying within the same request cannot succeed while the quota is exhausted.
            assertFalse(reason, (failure as CloudTransientException).retryable)
        }
        listOf("rateLimitExceeded", "userRateLimitExceeded").forEach { reason ->
            val failure = DriveHttpErrorPolicy.toException(403, driveError(reason))
            assertTrue(reason, failure is CloudTransientException && failure.retryable)
        }
        assertTrue(DriveHttpErrorPolicy.toException(403, driveError("insufficientPermissions")) is CloudAuthorizationException)
        assertTrue(DriveHttpErrorPolicy.toException(403, driveError("appNotAuthorizedToFile")) is CloudAuthorizationException)
    }

    private fun driveError(reason: String) =
        """{"error":{"code":403,"message":"Denied","errors":[{"message":"Denied","domain":"usageLimits","reason":"$reason"}]}}"""

    @Test
    fun backoffIsBoundedAndIncludesOnlyBoundedJitter() {
        assertEquals(1_000L, DriveRetryPolicy.delayMillis(0, 0))
        assertEquals(2_250L, DriveRetryPolicy.delayMillis(1, 250))
        assertEquals(8_250L, DriveRetryPolicy.delayMillis(99, 999))
    }
}
