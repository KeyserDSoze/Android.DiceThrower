package com.keyserdsoze.dicethrower.model

/** Settings belong to a Roll, not to the app-wide dice RNG or to the renderer. */
enum class RollVisualProfile { BALANCED, SUBTLE, OFF, CUSTOM }

data class RollVisualEffectsSettings(
    val profile: RollVisualProfile = RollVisualProfile.BALANCED,
    val badge: Boolean = true,
    val groupLanes: Boolean = true,
    val winnerSpotlight: Boolean = true,
    val tableAura: Boolean = true,
    val particles: Boolean = true,
    val actionCues: Boolean = true,
    val resultTransitions: Boolean = true,
) {
    companion object {
        fun preset(profile: RollVisualProfile): RollVisualEffectsSettings = when (profile) {
            RollVisualProfile.BALANCED -> RollVisualEffectsSettings()
            RollVisualProfile.SUBTLE -> RollVisualEffectsSettings(
                profile = profile, particles = false, tableAura = false,
                resultTransitions = false,
            )
            RollVisualProfile.OFF -> RollVisualEffectsSettings(
                profile = profile, badge = false, groupLanes = false,
                winnerSpotlight = false, tableAura = false, particles = false,
                actionCues = false, resultTransitions = false,
            )
            RollVisualProfile.CUSTOM -> RollVisualEffectsSettings(profile = profile)
        }
    }

    fun custom(update: RollVisualEffectsSettings.() -> RollVisualEffectsSettings): RollVisualEffectsSettings =
        update().copy(profile = RollVisualProfile.CUSTOM)

    /**
     * Device-wide motion preference has priority over per-Roll animation toggles,
     * but does not erase the Roll's saved configuration.
     */
    fun withGlobalMotionEnabled(enabled: Boolean): RollVisualEffectsSettings =
        if (enabled) this else copy(
            tableAura = false, particles = false, resultTransitions = false,
            actionCues = false,
        )
}
