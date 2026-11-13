package com.keyserdsoze.dicethrower.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.v2.RollTableRenderedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Full journeys through the real app, not isolated composables or a mocked renderer. */
@RunWith(AndroidJUnit4::class)
class RollJourneyE2ETest {
    private val compose = createEmptyComposeRule()
    private val app = NativeAppE2ERule(compose)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(app)

    private val hero = CharacterProfile("e2e-hero", "E2E Hero", level = 2)
    private val group = RollGroup("e2e-group", hero.id, "E2E Rolls")
    private val single = RollDefinition("e2e-single", hero.id, "E2E d20", "1d20+{Strength}", group.id)
    private val modifier = CharacterModifier("e2e-strength", hero.id, "Strength", 3)
    private val multipart = RollDefinition(
        "e2e-multipart", hero.id, "E2E attack and damage", "(1d20+{Strength})+(1d6+{Strength})", group.id,
        doubleRollEnabled = true,
        subgroups = listOf(
            RollSubgroup("attack", "Attack", "1d20+{Strength}", includeInDoubleRoll = true),
            RollSubgroup("damage", "Damage", "1d6+{Strength}"),
            RollSubgroup("support", "Support boost", "1d8", includeInNormalRoll = false),
        ),
        effects = listOf(RollEffect(
            "support-effect", "E2E support effect", EffectType.BONUS,
            activationGroups = listOf(EffectActivationGroup("activation", listOf(EffectCondition(
                "level-check", source = EffectValueSource.VARIABLE, variableName = "level",
                scope = EffectValueScope.TOTAL, comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "1",
            )))),
            actions = listOf(EffectAction(
                "support-action", EffectActionType.ROLL_AFTER, "support", EffectValueScope.DICE_ONLY,
            )),
        )),
    )

    private fun launch(roll: RollDefinition = single, animate: Boolean = false,
        theme: ThemeMode = ThemeMode.DARK, locale: String = "en") {
        app.launch(
            AppData(characters = listOf(hero), modifiers = listOf(modifier), groups = listOf(group), rolls = listOf(roll)),
            AppSettings(themeMode = theme, animationsEnabled = animate, shakeEnabled = false),
            locale,
        )
        tap("character-${hero.id}")
        tap("group-${group.id}")
        tap("launch-roll-${roll.id}")
        awaitNode("roll-screen")
        assertEquals(0, app.store.loadData().logs.size)
    }

    private fun awaitNode(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(tag: String) {
        app.step("Tap $tag")
        awaitNode(tag)
        compose.waitUntil(10_000) {
            !compose.onNodeWithTag(tag).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        compose.onNodeWithTag(tag).performClick()
    }

    private fun awaitLogCount(count: Int): List<RollLog> {
        app.step("Expect $count persisted roll(s)")
        compose.waitUntil(10_000) { app.store.loadData().logs.size == count }
        return app.store.loadData().logs
    }

    private fun expectTotal(total: Int, tag: String = "roll-single-result") {
        awaitNode(tag)
        awaitRenderedTable()
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNode(hasText(total.toString()) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
    }

    private fun awaitRenderedTable() {
        app.step("Wait for rendered OpenGL table and texture")
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("roll-table").fetchSemanticsNode().config
                .getOrElse(RollTableRenderedKey) { false }
        }
    }

    private fun checkpoint(name: String) {
        awaitRenderedTable()
        app.checkpoint(name)
    }

    private fun expectParts(log: RollLog) {
        assertEquals(listOf("Attack", "Damage", "Support boost"), log.parts.map { it.name })
        awaitNode("roll-results-overlay")
        compose.onNodeWithTag("roll-single-result").assertDoesNotExist()
        log.parts.forEachIndexed { index, part ->
            expectTotal(part.total, "roll-part-$index")
            compose.onNode(hasText(part.name) and hasAnyAncestor(hasTestTag("roll-part-$index"))).assertIsDisplayed()
        }
        assertTrue(log.effectSteps.single().activated)
        assertEquals(EffectActionType.ROLL_AFTER, log.effectSteps.single().actions.single().kind)
        assertTrue(log.effectSteps.single().actions.single().applied)
        assertTrue(log.effectSteps.single().actions.single().generatedDiceDetail?.contains("1d8") == true)
    }

    private fun pressSystemBack() {
        app.step("Press Android Back")
        Espresso.pressBack()
    }

    @Test
    fun normalRollSurvivesRecreationAndOnlyExplicitRerollAddsHistory() {
        launch()
        tap("roll-normal")
        val first = awaitLogCount(1).single()
        expectTotal(first.total)
        checkpoint("rolled")

        app.recreate()
        expectTotal(first.total)
        assertEquals(listOf(first), app.store.loadData().logs)
        checkpoint("after-recreation")

        tap("roll-normal")
        val second = awaitLogCount(2)
        assertEquals(first, second.single { it.id == first.id })
        expectTotal(second.single { it.id != first.id }.total)
    }

    @Test
    fun actualOrientationChangePreservesTheDisplayedResultAndHistory() {
        launch(theme = ThemeMode.LIGHT)
        tap("roll-normal")
        val first = awaitLogCount(1).single()
        expectTotal(first.total)
        app.assertSystemBarIcons(darkIcons = false)
        app.rotate()
        expectTotal(first.total)
        app.assertSystemBarIcons(darkIcons = false)
        assertEquals(listOf(first), app.store.loadData().logs)
        checkpoint("after-orientation-change")
        tap("roll-back")
        awaitNode("launch-roll-${single.id}")
        app.assertSystemBarIcons(darkIcons = true)
    }

    @Test
    fun bestAndWorstPreserveIndependentPartsAndEffectsInItalianLightTheme() {
        launch(multipart, theme = ThemeMode.LIGHT, locale = "it")
        tap("roll-best")
        val best = awaitLogCount(1).single()
        assertEquals(DoubleRollMode.BEST, best.doubleRollMode)
        assertTrue(best.comparisonTotal!! >= best.alternativeComparisonTotal!!)
        assertEquals(null, best.parts[1].alternativeTotal) // Damage was sampled only once.
        expectParts(best)
        app.assertSystemBarIcons(darkIcons = false)
        checkpoint("best-parts")

        app.recreate()
        expectParts(best)
        app.assertSystemBarIcons(darkIcons = false)
        assertEquals(listOf(best), app.store.loadData().logs)
        checkpoint("best-after-recreation")

        tap("roll-worst")
        val worst = awaitLogCount(2).single { it.id != best.id }
        assertEquals(DoubleRollMode.WORST, worst.doubleRollMode)
        assertTrue(worst.comparisonTotal!! <= worst.alternativeComparisonTotal!!)
        expectParts(worst)
        assertEquals(best, app.store.loadData().logs.single { it.id == best.id })
    }

    @Test
    fun recreationAtGeneratedDiceStageEntryDoesNotExecuteTheEffectAgain() {
        launch(multipart, animate = true)
        val awaitRecreation = app.recreateAtNextGeneratedDiceStage()
        tap("roll-normal")
        val first = awaitLogCount(1).single()
        awaitRecreation()
        expectParts(first)
        assertEquals(listOf(first), app.store.loadData().logs)
        checkpoint("generated-dice-after-recreation")
    }

    @Test
    fun statisticsLevelEditsAndSystemBackFollowTheExpectedHierarchy() {
        launch()
        tap("roll-normal")
        val first = awaitLogCount(1).single()
        expectTotal(first.total)
        tap("roll-statistics")
        awaitNode("roll-stats-sheet-content")
        app.recreate()
        awaitNode("roll-stats-sheet-content")
        assertEquals(listOf(first), app.store.loadData().logs)

        tap("roll-level-up")
        compose.waitUntil(10_000) { app.store.loadData().characters.single().level == 3 }
        awaitNode("roll-stats-sheet-content")
        compose.onNodeWithTag("roll-results-overlay").assertDoesNotExist() // Old-level result invalidated.
        assertEquals(listOf(first), app.store.loadData().logs)

        pressSystemBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("roll-stats-sheet-content").fetchSemanticsNodes().isEmpty() }
        awaitNode("roll-screen")
        pressSystemBack()
        awaitNode("launch-roll-${single.id}")
        tap("launch-roll-${single.id}")
        awaitNode("roll-screen")
        compose.onNodeWithTag("roll-results-overlay").assertDoesNotExist()
        assertEquals(listOf(first), app.store.loadData().logs)
    }

    @Test
    fun cancellingATableEditorAfterRecreationKeepsTheResultAndStoredDefinition() {
        launch()
        tap("roll-normal")
        val first = awaitLogCount(1).single()
        expectTotal(first.total)
        tap("roll-statistics")
        tap("roll-edit")
        awaitNode("roll-editor-screen")
        compose.onNodeWithTag("roll-name").performTextReplacement("Unsaved table draft")
        compose.onNodeWithTag("roll-minimum-level").performTextReplacement("0")
        app.recreate()
        awaitNode("roll-editor-screen")
        compose.onNodeWithTag("roll-name").assertTextContains("Unsaved table draft")
        compose.onNodeWithTag("roll-minimum-level").assertTextContains("0")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("roll-editor-cancel"))
        tap("roll-editor-cancel")
        expectTotal(first.total)
        assertEquals(single, app.store.loadData().rolls.single())
        assertEquals(listOf(first), app.store.loadData().logs)
        checkpoint("after-cancelling-table-editor")
    }
}
