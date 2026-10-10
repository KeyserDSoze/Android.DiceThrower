package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import com.keyserdsoze.dicethrower.model.RollVisualProfile

/** Roll-local preferences: presets are fast starts, individual changes become CUSTOM. */
@Composable
internal fun RollVisualSettingsEditorV2(
    settings: RollVisualEffectsSettings,
    onChange: (RollVisualEffectsSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.cinematic_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.cinematic_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                RollVisualProfile.BALANCED to R.string.cinematic_balanced,
                RollVisualProfile.SUBTLE to R.string.cinematic_subtle,
                RollVisualProfile.OFF to R.string.cinematic_off,
            ).forEach { (profile, label) ->
                FilterChip(
                    selected = settings.profile == profile,
                    onClick = { onChange(RollVisualEffectsSettings.preset(profile)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        fun change(transform: RollVisualEffectsSettings.() -> RollVisualEffectsSettings) =
            onChange(settings.custom(transform))
        VisualToggle(R.string.cinematic_badge, settings.badge) { change { copy(badge = it) } }
        VisualToggle(R.string.cinematic_lanes, settings.groupLanes) { change { copy(groupLanes = it) } }
        VisualToggle(R.string.cinematic_spotlight, settings.winnerSpotlight) { change { copy(winnerSpotlight = it) } }
        VisualToggle(R.string.cinematic_aura, settings.tableAura) { change { copy(tableAura = it) } }
        VisualToggle(R.string.cinematic_particles, settings.particles) { change { copy(particles = it) } }
        VisualToggle(R.string.cinematic_actions, settings.actionCues) { change { copy(actionCues = it) } }
        VisualToggle(R.string.cinematic_results, settings.resultTransitions) { change { copy(resultTransitions = it) } }
        VisualToggle(R.string.cinematic_camera, settings.cameraImpact) { change { copy(cameraImpact = it) } }
    }
}

@Composable
private fun VisualToggle(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
