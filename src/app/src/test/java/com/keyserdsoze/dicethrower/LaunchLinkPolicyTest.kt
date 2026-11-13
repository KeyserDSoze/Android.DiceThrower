package com.keyserdsoze.dicethrower

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchLinkPolicyTest {
    @Test
    fun importLinkIsHandledOnlyOnAFreshLaunch() {
        assertTrue(shouldHandleLaunchLink(restored = false, importPreviewPending = false, intentFlags = 0))
        // Recents relaunches the task with its original intent: never import it again.
        assertFalse(shouldHandleLaunchLink(false, false, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        // Rotation and recreate() after a language change restore an Activity: no re-import...
        assertFalse(shouldHandleLaunchLink(restored = true, importPreviewPending = false, intentFlags = 0))
        // ...unless the preview of that link was still waiting for the user's decision.
        assertTrue(shouldHandleLaunchLink(restored = true, importPreviewPending = true, intentFlags = 0))
        assertTrue(shouldHandleLaunchLink(true, true, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }
}
