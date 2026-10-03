package com.keyserdsoze.dicethrower.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RollButtonPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

data class CharacterProfile(
    val id: String,
    val name: String,
    val imageUri: String? = null,
    val tag: String = "",
    val order: Int = 0,
)

data class RollGroup(
    val id: String,
    val characterId: String,
    val name: String,
    val order: Int = 0,
)

data class RollDefinition(
    val id: String,
    val characterId: String,
    val name: String,
    val expression: String,
    val groupId: String? = null,
    val enabled: Boolean = true,
    val order: Int = 0,
)

data class RollLog(
    val id: String,
    val characterId: String,
    val rollDefinitionId: String,
    val rollName: String,
    val expression: String,
    val total: Int,
    val detail: String,
    val timestamp: Long,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val shakeEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val showRollButton: Boolean = true,
    val rollButtonPosition: RollButtonPosition = RollButtonPosition.BOTTOM_RIGHT,
    val logRetention: Int = 20,
)

data class AppData(
    val characters: List<CharacterProfile> = emptyList(),
    val groups: List<RollGroup> = emptyList(),
    val rolls: List<RollDefinition> = emptyList(),
    val logs: List<RollLog> = emptyList(),
)
