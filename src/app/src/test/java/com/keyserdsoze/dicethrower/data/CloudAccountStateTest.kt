package com.keyserdsoze.dicethrower.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAccountStateTest {
    @Test
    fun freshInstallRequiresOnboardingAndStandaloneCompletesIt() {
        val fresh = CloudAccountState()

        assertTrue(fresh.onboardingRequired)
        assertFalse(CloudAccountTransitions.useStandalone().onboardingRequired)
        assertEquals(CloudMode.STANDALONE, CloudAccountTransitions.useStandalone().mode)
    }

    @Test
    fun cancelingFirstGoogleConnectionFallsBackToStandalone() {
        val canceled = CloudAccountTransitions.connectionCanceled(CloudAccountState())

        assertEquals(CloudMode.STANDALONE, canceled.mode)
        assertNull(canceled.account)
        assertFalse(canceled.driveAppDataAuthorized)
    }

    @Test
    fun cancelingLaterConnectionKeepsExistingStandaloneState() {
        val standalone = CloudAccountTransitions.useStandalone()

        assertEquals(standalone, CloudAccountTransitions.connectionCanceled(standalone))
    }

    @Test
    fun connectingGooglePreservesExplicitReconciliationBoundary() {
        val account = GoogleAccountIdentity("subject", "player@example.com", "Player")

        val connected = CloudAccountTransitions.connectGoogle(account)

        assertTrue(connected.googleConnected)
        assertTrue(connected.initialReconciliationPending)
        assertEquals(account, connected.account)
    }

    @Test
    fun disconnectReturnsToStandaloneWithoutAnyAppDataOperation() {
        val disconnected = CloudAccountTransitions.disconnect()

        assertEquals(CloudMode.STANDALONE, disconnected.mode)
        assertNull(disconnected.account)
        assertFalse(disconnected.driveAppDataAuthorized)
    }

    @Test
    fun noBackupFileStateRoundTripsWithoutTokens() {
        val directory = Files.createTempDirectory("dice-cloud-account").toFile()
        val file = directory.resolve("state.json")
        val store = CloudAccountStateFileStore(file)
        val state = CloudAccountTransitions.connectGoogle(
            GoogleAccountIdentity("subject-1", "player@example.com", "Player One"),
        )

        try {
            store.save(state)
            val raw = file.readText()

            assertEquals(state, store.load())
            assertFalse(raw.contains("token", ignoreCase = true))
            assertFalse(raw.contains("credential", ignoreCase = true))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidConnectedStateFailsClosedToUndecided() {
        val directory = Files.createTempDirectory("dice-cloud-invalid").toFile()
        val file = directory.resolve("state.json")
        val store = CloudAccountStateFileStore(file)

        try {
            file.writeText("""{"mode":"GOOGLE","driveAppDataAuthorized":true}""")
            assertEquals(CloudAccountState(), store.load())
        } finally {
            directory.deleteRecursively()
        }
    }
}
