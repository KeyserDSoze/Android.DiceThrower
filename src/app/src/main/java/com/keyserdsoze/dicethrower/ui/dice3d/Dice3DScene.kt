package com.keyserdsoze.dicethrower.ui.dice3d

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.CandidateVisualLighting
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@Composable
fun Dice3DScene(
    event: DiceRollVisualEvent,
    modifier: Modifier = Modifier,
    tableTheme: DiceTableTheme = DiceTableTheme.ARCANE,
    tableImage: Bitmap? = null,
    tableImageKey: String? = null,
    fullBleed: Boolean = false,
    animateRoll: Boolean = true,
    winnerRevealed: Boolean = false,
    onSettled: (Long) -> Unit = {},
) {
    val sceneModifier = if (fullBleed) modifier else modifier.clip(RoundedCornerShape(24.dp))
    AndroidView(
        modifier = sceneModifier,
        factory = { context ->
            DiceGLView(context).also {
                it.onSettled = onSettled
                it.setScene(event, tableTheme, tableImage, tableImageKey, animateRoll)
                it.setWinnerRevealed(winnerRevealed)
            }
        },
        update = { view ->
            view.onSettled = onSettled
            view.setScene(event, tableTheme, tableImage, tableImageKey, animateRoll)
            view.setWinnerRevealed(winnerRevealed)
        },
    )
}

private data class SceneKey(
    val eventId: Long,
    val tableTheme: DiceTableTheme,
    val tableImageKey: String?,
    val animateRoll: Boolean,
)

private class DiceGLView(context: Context) : GLSurfaceView(context) {
    var onSettled: (Long) -> Unit = {}
    private val diceRenderer = DiceSceneRenderer { eventId -> post { onSettled(eventId) } }
    private var lastSceneKey: SceneKey? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setPreserveEGLContextOnPause(true)
        setRenderer(diceRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setScene(
        event: DiceRollVisualEvent,
        tableTheme: DiceTableTheme,
        tableImage: Bitmap?,
        tableImageKey: String?,
        animateRoll: Boolean,
    ) {
        val key = SceneKey(event.id, tableTheme, tableImageKey, animateRoll)
        if (key == lastSceneKey) return
        lastSceneKey = key
        queueEvent { diceRenderer.setEvent(event, tableTheme, tableImage, animateRoll) }
    }

    fun setWinnerRevealed(revealed: Boolean) {
        queueEvent { diceRenderer.setWinnerRevealed(revealed) }
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }
}

private data class VisualDie(
    val sides: Int,
    val value: Int,
    val phase: Float,
    val renderStyle: DiceRenderStyle,
    val dimmed: Boolean = false,
    val effectAccent: EffectType? = null,
    val candidateGroup: Int? = null,
    val chosenCandidate: Boolean = false,
)

private data class GpuMesh(
    val positions: FloatBuffer,
    val normals: FloatBuffer,
    val vertexCount: Int,
    val numberPositions: FloatBuffer,
    val numberNormals: FloatBuffer,
    val numberVertexCount: Int,
    val valueFaceNormals: FloatArray,
)

internal data class DiceTableVisualBounds(
    val halfWidth: Float,
    val halfHeight: Float,
)

internal object DiceTableViewport {
    // These are physical collision bounds. The rendered background deliberately extends beyond
    // them so a full-screen texture never changes where dice are allowed to move.
    const val HALF_WIDTH = 2.65f
    const val HALF_HEIGHT = 6.0f
    const val TABLE_Z = -0.72f
    private const val VERTICAL_FOV_DEGREES = 36f
    const val FOV_DEGREES = VERTICAL_FOV_DEGREES
    private const val VIEWPORT_PADDING = 1.015f
    private const val BACKGROUND_OVERSCAN = 1.035f

    fun cameraDistanceFor(aspect: Float): Float {
        val safeAspect = aspect.coerceAtLeast(0.25f)
        val halfFovRadians = Math.toRadians(VERTICAL_FOV_DEGREES.toDouble() / 2.0)
        val tangent = tan(halfFovRadians).toFloat()
        val vertical = HALF_HEIGHT * VIEWPORT_PADDING / tangent
        val horizontal = HALF_WIDTH * VIEWPORT_PADDING / (tangent * safeAspect)
        return max(vertical, horizontal)
    }

    fun visualBoundsFor(aspect: Float, cameraDistance: Float): DiceTableVisualBounds {
        val safeAspect = aspect.coerceAtLeast(0.25f)
        val halfFovRadians = Math.toRadians(VERTICAL_FOV_DEGREES.toDouble() / 2.0)
        val planeDistance = (cameraDistance - TABLE_Z).coerceAtLeast(0.1f)
        val halfHeight = planeDistance * tan(halfFovRadians).toFloat() * BACKGROUND_OVERSCAN
        return DiceTableVisualBounds(
            halfWidth = halfHeight * safeAspect,
            halfHeight = halfHeight,
        )
    }
}

internal data class DiceAxisAngle(
    val angleDegrees: Float,
    val axis: Vec3,
)

internal fun rotateFaceNormal(
    normal: Vec3,
    angleXDegrees: Float,
    angleYDegrees: Float,
    angleZDegrees: Float,
): Vec3 {
    fun radians(value: Float) = Math.toRadians(value.toDouble())
    var value = normal

    run {
        val a = radians(angleZDegrees)
        val c = cos(a).toFloat()
        val si = sin(a).toFloat()
        value = Vec3(value.x * c - value.y * si, value.x * si + value.y * c, value.z)
    }
    run {
        val a = radians(angleYDegrees)
        val c = cos(a).toFloat()
        val si = sin(a).toFloat()
        value = Vec3(value.x * c + value.z * si, value.y, -value.x * si + value.z * c)
    }
    run {
        val a = radians(angleXDegrees)
        val c = cos(a).toFloat()
        val si = sin(a).toFloat()
        value = Vec3(value.x, value.y * c - value.z * si, value.y * si + value.z * c)
    }
    return value.normalized()
}

internal fun faceAlignmentCorrection(
    currentNormal: Vec3,
    progress: Float,
): DiceAxisAngle {
    val normalized = currentNormal.normalized()
    val nz = normalized.z.coerceIn(-1f, 1f)
    val axisLength = sqrt(normalized.x * normalized.x + normalized.y * normalized.y)
    val fullAngle = Math.toDegrees(acos(nz).toDouble()).toFloat()
    val easedProgress = progress.coerceIn(0f, 1f)
    val axis = when {
        axisLength > 0.0001f -> Vec3(
            normalized.y / axisLength,
            -normalized.x / axisLength,
            0f,
        )
        nz < 0f -> Vec3(1f, 0f, 0f)
        else -> Vec3(0f, 0f, 1f)
    }
    return DiceAxisAngle(fullAngle * easedProgress, axis)
}

internal fun tableTextureCoordinatesFor(
    imageAspect: Float?,
    targetAspect: Float = DiceTableViewport.HALF_WIDTH / DiceTableViewport.HALF_HEIGHT,
): FloatArray {
    var u0 = 0f
    var u1 = 1f
    var v0 = 0f
    var v1 = 1f
    if (imageAspect != null && imageAspect > 0f) {
        val safeTargetAspect = targetAspect.coerceAtLeast(0.01f)
        if (imageAspect > safeTargetAspect) {
            val visibleWidth = (safeTargetAspect / imageAspect).coerceIn(0f, 1f)
            u0 = (1f - visibleWidth) / 2f
            u1 = 1f - u0
        } else if (imageAspect < safeTargetAspect) {
            val visibleHeight = (imageAspect / safeTargetAspect).coerceIn(0f, 1f)
            v0 = (1f - visibleHeight) / 2f
            v1 = 1f - v0
        }
    }
    return floatArrayOf(
        u0, v1,
        u1, v1,
        u1, v0,
        u0, v1,
        u1, v0,
        u0, v0,
    )
}

private class DiceSceneRenderer(
    private val onSettled: (Long) -> Unit,
) : GLSurfaceView.Renderer {
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var primaryColorHandle = 0
    private var secondaryColorHandle = 0
    private var materialLightingHandle = 0
    private var materialAccentHandle = 0
    private var textureCoordHandle = 0
    private var useTextureHandle = 0
    private var textureHandle = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private val gpuMeshes = mutableMapOf<Int, GpuMesh>()
    private var dice: List<VisualDie> = emptyList()
    private var persistentStates: List<DiceTablePhysics.State> = emptyList()
    private var tableTheme = DiceTableTheme.ARCANE
    private var physics: DiceTablePhysics? = null
    private var animateRoll = true
    private var winnerRevealed = false
    private var winnerRevealNanos = 0L
    private var visualSettings = RollVisualEffectsSettings()
    private var effectImpactStartedNanos = 0L
    private var currentEventId = 0L
    private var lastFrameAt = SystemClock.elapsedRealtimeNanos()
    private var settledReported = false
    private var cameraDistance = 14f
    private var tableImage: Bitmap? = null
    private var tableTexture = 0
    private var tableTextureDirty = false

    private var viewportAspect = DiceTableViewport.HALF_WIDTH / DiceTableViewport.HALF_HEIGHT
    private var tablePositions = tablePositionsFor(
        DiceTableViewport.HALF_WIDTH,
        DiceTableViewport.HALF_HEIGHT,
    )
    private val tableNormals = FloatArray(18) { index -> if (index % 3 == 2) 1f else 0f }.toFloatBuffer()
    private var tableTextureCoordinates = tableTextureCoordinatesFor(null, viewportAspect).toFloatBuffer()

    fun setWinnerRevealed(revealed: Boolean) {
        if (revealed && !winnerRevealed) winnerRevealNanos = SystemClock.elapsedRealtimeNanos()
        winnerRevealed = revealed
    }

    fun setEvent(
        event: DiceRollVisualEvent,
        tableTheme: DiceTableTheme,
        tableImage: Bitmap?,
        animateRoll: Boolean,
    ) {
        // Preserve the already-settled bodies before changing the scene. New
        // effect-generated dice animate independently, without replaying old throws.
        val previousPositions = persistentStates + (physics?.states().orEmpty())
        persistentStates = if (animateRoll && event.persistentDiceCount > 0 &&
            previousPositions.size >= event.persistentDiceCount
        ) previousPositions.take(event.persistentDiceCount) else emptyList()
        val appearanceBySlot = event.appearances.associateBy { it.slotKey }
        visualSettings = event.visualSettings
        effectImpactStartedNanos = if (animateRoll && visualSettings.cameraImpact &&
            event.effectAccentComponents.isNotEmpty()) SystemClock.elapsedRealtimeNanos() else 0L
        winnerRevealed = false
        dice = buildList {
            event.result.components.forEachIndexed { componentIndex, component ->
                component.rolls.forEachIndexed { index, value ->
                    if (size < MAX_VISIBLE_DICE) {
                        val slotKey = "$componentIndex:$index"
                        add(
                            VisualDie(
                                sides = component.sides,
                                value = value,
                                phase = ((component.sides * 37 + value * 19 + index * 53) % 360).toFloat(),
                                renderStyle = DiceRenderStyleFactory.create(appearanceBySlot[slotKey]),
                                dimmed = componentIndex in event.dimmedComponentIndices,
                                effectAccent = event.effectAccentComponents[componentIndex],
                                candidateGroup = event.candidateGroupByComponent[componentIndex]
                                    .takeIf { event.visualSettings.groupLanes },
                                chosenCandidate = componentIndex in event.chosenCandidateComponents,
                            ),
                        )
                    }
                }
            }
        }
        this.tableTheme = tableTheme
        if (this.tableImage !== tableImage) {
            this.tableImage = tableImage
            tableTextureDirty = true
        }
        this.animateRoll = animateRoll
        currentEventId = event.id
        physics = if (animateRoll) DiceTablePhysics(
            dice.size - persistentStates.size, event.id,
            laneByDieIndex = dice.drop(persistentStates.size)
                .mapIndexedNotNull { index, die ->
                    die.candidateGroup?.let { index to it }
                }.toMap(),
            spawnFromEdge = event.visualSettings.actionCues &&
                event.effectAccentComponents.isNotEmpty(),
        ) else null
        lastFrameAt = SystemClock.elapsedRealtimeNanos()
        settledReported = false
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.018f, 0.022f, 0.04f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        primaryColorHandle = GLES20.glGetUniformLocation(program, "uPrimaryColor")
        secondaryColorHandle = GLES20.glGetUniformLocation(program, "uSecondaryColor")
        materialLightingHandle = GLES20.glGetUniformLocation(program, "uMaterialLighting")
        materialAccentHandle = GLES20.glGetUniformLocation(program, "uMaterialAccent")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        tableTexture = 0
        tableTextureDirty = tableImage != null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        viewportAspect = aspect.coerceAtLeast(0.25f)
        cameraDistance = DiceTableViewport.cameraDistanceFor(viewportAspect)
        val visualBounds = DiceTableViewport.visualBoundsFor(viewportAspect, cameraDistance)
        tablePositions = tablePositionsFor(visualBounds.halfWidth, visualBounds.halfHeight)
        tableTextureCoordinates = tableTextureCoordinatesFor(
            tableImage?.takeUnless(Bitmap::isRecycled)?.let { it.width.toFloat() / it.height.toFloat() },
            viewportAspect,
        ).toFloatBuffer()
        Matrix.perspectiveM(projection, 0, DiceTableViewport.FOV_DEGREES, viewportAspect, 0.1f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        val now = SystemClock.elapsedRealtimeNanos()
        val deltaSeconds = (now - lastFrameAt) / 1_000_000_000f
        lastFrameAt = now
        physics?.step(deltaSeconds)

        val count = dice.size
        val dieScale = physics?.radius?.times(0.92f) ?: when {
            count <= 1 -> 0.72f
            count <= 4 -> 0.62f
            count <= 8 -> 0.52f
            else -> 0.44f
        }
        val states = if (persistentStates.isNotEmpty()) {
            persistentStates + (physics?.states() ?: emptyList())
        } else physics?.states() ?: staticStates(count, dieScale).mapIndexed { index, state ->
            when (dice[index].candidateGroup) {
                0 -> state.copy(y = 2.0f + state.y * 0.35f)
                1 -> state.copy(y = -2.0f + state.y * 0.35f)
                else -> state
            }
        }
        val settleProgress = if (animateRoll) physics?.settleProgress ?: 1f else 1f

        val impactAge = if (effectImpactStartedNanos > 0L)
            (now - effectImpactStartedNanos) / 1_000_000_000f else 10f
        val impactStrength = if (animateRoll && visualSettings.cameraImpact)
            (1f - impactAge / 0.48f).coerceIn(0f, 1f) * 0.045f else 0f
        val impactX = sin(impactAge * 93f) * impactStrength
        val impactY = sin(impactAge * 117f) * impactStrength * 0.62f
        Matrix.setLookAtM(view, 0, impactX, -0.12f + impactY,
            cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glUniform1f(useTextureHandle, 0f)

        drawTable()

        dice.forEachIndexed { index, die ->
            val state = states[index]

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, state.x, state.y, 0f)
            val gpuMesh = gpuMesh(die.sides)
            val alreadySettled = index < persistentStates.size
            if (animateRoll) {
                val angleX = state.angleX + die.phase * 0.17f
                val angleY = state.angleY + die.value * 3.7f
                val angleZ = state.angleZ
                resolvedFaceNormal(die, gpuMesh)?.let { localNormal ->
                    val movingNormal = rotateFaceNormal(localNormal, angleX, angleY, angleZ)
                    val correction = faceAlignmentCorrection(
                        movingNormal, if (alreadySettled) 1f else settleProgress,
                    )
                    if (correction.angleDegrees > 0.0001f) {
                        // Pre-multiply a progressively stronger world-space correction before
                        // the physical rotations. The engine-selected value never changes; the
                        // visible die naturally converges to that face while angular motion dies.
                        Matrix.rotateM(
                            model,
                            0,
                            correction.angleDegrees,
                            correction.axis.x,
                            correction.axis.y,
                            correction.axis.z,
                        )
                    }
                }
                Matrix.rotateM(model, 0, angleX, 1f, 0f, 0f)
                Matrix.rotateM(model, 0, angleY, 0f, 1f, 0f)
                Matrix.rotateM(model, 0, angleZ, 0f, 0f, 1f)
            } else {
                Matrix.rotateM(model, 0, die.phase, 0f, 0f, 1f)
                alignResolvedFace(model, die, gpuMesh)
            }
            Matrix.scaleM(model, 0, dieScale, dieScale, dieScale)

            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

            gpuMesh.positions.position(0)
            gpuMesh.normals.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.positions)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.normals)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)

            val style = die.renderStyle
            // The less favorable sampled candidate stays visible, but recedes visually.
            // Both candidates are full-brightness during physics. Only after
            // all stages settle does the chosen set glow and the other dim.
            val candidateBrightness = CandidateVisualLighting.brightness(
                revealed = winnerRevealed, enabled = visualSettings.winnerSpotlight, discarded = die.dimmed,
            )
            val selectedFlash = CandidateVisualLighting.chosenPulse(
                revealed = winnerRevealed, enabled = visualSettings.winnerSpotlight,
                chosen = die.chosenCandidate,
                elapsedSeconds = (now - winnerRevealNanos) / 1_000_000_000f,
            )
            // Accent is visual-only: green/cyan for bonuses and warm crimson
            // for maluses. No GPU path can change the sampled die value.
            val accentRgb = when (die.effectAccent) {
                EffectType.BONUS -> floatArrayOf(0.20f, 0.95f, 0.62f)
                EffectType.MALUS -> floatArrayOf(0.99f, 0.25f, 0.35f)
                null -> null
            }
            val flash = if (animateRoll && accentRgb != null && !alreadySettled) {
                (0.20f + 0.12f * sin(now / 240_000_000.0).toFloat())
            } else 0.16f
            fun toned(base: Float, target: Float, mix: Float): Float =
                ((base * (1f - mix) + target * mix) * candidateBrightness).coerceIn(0f, 1f)
            val effectiveRed = if (accentRgb == null) style.primary.red * candidateBrightness
                else toned(style.primary.red, accentRgb[0], flash)
            val effectiveGreen = if (accentRgb == null) style.primary.green * candidateBrightness
                else toned(style.primary.green, accentRgb[1], flash)
            val effectiveBlue = if (accentRgb == null) style.primary.blue * candidateBrightness
                else toned(style.primary.blue, accentRgb[2], flash)
            GLES20.glUniform4f(
                primaryColorHandle, (effectiveRed + selectedFlash * 0.9f).coerceAtMost(1f),
                (effectiveGreen + selectedFlash * 0.72f).coerceAtMost(1f),
                (effectiveBlue + selectedFlash * 0.32f).coerceAtMost(1f), style.primary.alpha,
            )
            GLES20.glUniform4f(
                secondaryColorHandle,
                if (accentRgb == null) style.secondary.red * candidateBrightness
                    else toned(style.secondary.red, accentRgb[0], 0.48f),
                if (accentRgb == null) style.secondary.green * candidateBrightness
                    else toned(style.secondary.green, accentRgb[1], 0.48f),
                if (accentRgb == null) style.secondary.blue * candidateBrightness
                    else toned(style.secondary.blue, accentRgb[2], 0.48f),
                style.secondary.alpha,
            )
            GLES20.glUniform4f(
                materialLightingHandle,
                style.lighting.ambient,
                style.lighting.diffuse,
                style.lighting.specular,
                style.lighting.shininess,
            )
            GLES20.glUniform4f(
                materialAccentHandle,
                (style.lighting.rim + (if (accentRgb != null) 0.25f else 0f) + selectedFlash).coerceAtMost(1f),
                (style.lighting.accentMix + if (accentRgb != null) 0.20f else 0f).coerceAtMost(1f),
                (style.lighting.innerGlow + if (accentRgb != null) 0.35f else 0f).coerceAtMost(1f),
                0f,
            )
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpuMesh.vertexCount)

            gpuMesh.numberPositions.position(0)
            gpuMesh.numberNormals.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.numberPositions)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.numberNormals)
            val luminance = effectiveRed * 0.299f + effectiveGreen * 0.587f + effectiveBlue * 0.114f
            val numeral = if (luminance > 0.58f) 0.045f else 0.97f
            GLES20.glUniform4f(primaryColorHandle, numeral, numeral, numeral, 1f)
            GLES20.glUniform4f(secondaryColorHandle, numeral, numeral, numeral, 1f)
            GLES20.glUniform4f(materialLightingHandle, 1f, 0.18f, 0f, 4f)
            GLES20.glUniform4f(materialAccentHandle, 0f, 0f, 0f, 0f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpuMesh.numberVertexCount)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)

        val settled = physics?.isSettled == true || (!animateRoll && currentEventId != 0L)
        if (settled && !settledReported) {
            settledReported = true
            onSettled(currentEventId)
        }
    }

    private fun drawTable() {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        tablePositions.position(0)
        tableNormals.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, tablePositions)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, tableNormals)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        val palette = tableTheme.palette()
        GLES20.glUniform4f(primaryColorHandle, palette[0], palette[1], palette[2], 1f)
        GLES20.glUniform4f(secondaryColorHandle, palette[3], palette[4], palette[5], 1f)
        GLES20.glUniform4f(materialLightingHandle, 0.76f, 0.36f, 0.08f, 12f)
        GLES20.glUniform4f(materialAccentHandle, 0.12f, 0.24f, 0f, 0f)
        val texture = ensureTableTexture()
        if (texture != 0) {
            tableTextureCoordinates.position(0)
            GLES20.glEnableVertexAttribArray(textureCoordHandle)
            GLES20.glVertexAttribPointer(
                textureCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                tableTextureCoordinates,
            )
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(textureHandle, 0)
            GLES20.glUniform1f(useTextureHandle, 1f)
        } else {
            GLES20.glUniform1f(useTextureHandle, 0f)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        if (texture != 0) {
            GLES20.glUniform1f(useTextureHandle, 0f)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }

    private fun ensureTableTexture(): Int {
        if (!tableTextureDirty) return tableTexture
        if (tableTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(tableTexture), 0)
            tableTexture = 0
        }
        val bitmap = tableImage
        if (bitmap == null || bitmap.isRecycled) {
            tableTextureDirty = false
            tableTextureCoordinates = tableTextureCoordinatesFor(null, viewportAspect).toFloatBuffer()
            return 0
        }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        tableTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tableTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        tableTextureCoordinates = tableTextureCoordinatesFor(
            imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat(),
            targetAspect = viewportAspect,
        ).toFloatBuffer()
        tableTextureDirty = false
        return tableTexture
    }

    private fun tablePositionsFor(halfWidth: Float, halfHeight: Float): FloatBuffer = floatArrayOf(
        -halfWidth, -halfHeight, DiceTableViewport.TABLE_Z,
        halfWidth, -halfHeight, DiceTableViewport.TABLE_Z,
        halfWidth, halfHeight, DiceTableViewport.TABLE_Z,
        -halfWidth, -halfHeight, DiceTableViewport.TABLE_Z,
        halfWidth, halfHeight, DiceTableViewport.TABLE_Z,
        -halfWidth, halfHeight, DiceTableViewport.TABLE_Z,
    ).toFloatBuffer()

    private fun staticStates(count: Int, scale: Float): List<DiceTablePhysics.State> {
        if (count == 0) return emptyList()
        val columns = ceil(sqrt(count.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toFloat() / columns).toInt().coerceAtLeast(1)
        return List(count) { index ->
            val row = index / columns
            val column = index % columns
            val rowCount = (count - row * columns).coerceAtMost(columns)
            DiceTablePhysics.State(
                x = (column - (rowCount - 1) / 2f) * scale * 2.35f,
                y = ((rows - 1) / 2f - row) * scale * 2.15f,
                angleX = 18f + index * 7f,
                angleY = -24f + index * 19f,
                angleZ = index * 13f,
            )
        }
    }

    private fun DiceTableTheme.palette(): FloatArray = when (this) {
        DiceTableTheme.ARCANE -> floatArrayOf(0.075f, 0.055f, 0.18f, 0.40f, 0.24f, 0.72f)
        DiceTableTheme.OAK -> floatArrayOf(0.25f, 0.105f, 0.035f, 0.64f, 0.34f, 0.10f)
        DiceTableTheme.EMERALD -> floatArrayOf(0.025f, 0.20f, 0.125f, 0.12f, 0.52f, 0.29f)
        DiceTableTheme.OBSIDIAN -> floatArrayOf(0.025f, 0.028f, 0.035f, 0.54f, 0.39f, 0.12f)
        DiceTableTheme.TAVERN_WOOD -> floatArrayOf(0.22f, 0.10f, 0.035f, 0.58f, 0.29f, 0.08f)
        DiceTableTheme.DUNGEON_STONE -> floatArrayOf(0.10f, 0.09f, 0.08f, 0.36f, 0.24f, 0.14f)
        DiceTableTheme.ELVEN_GROVE -> floatArrayOf(0.025f, 0.18f, 0.09f, 0.16f, 0.46f, 0.19f)
        DiceTableTheme.FROZEN_REALM -> floatArrayOf(0.08f, 0.20f, 0.28f, 0.32f, 0.70f, 0.86f)
        DiceTableTheme.DESERT_RUINS -> floatArrayOf(0.30f, 0.17f, 0.07f, 0.72f, 0.49f, 0.23f)
        DiceTableTheme.ASTRAL_VOID -> floatArrayOf(0.035f, 0.04f, 0.16f, 0.30f, 0.18f, 0.65f)
    }

    private fun gpuMesh(sides: Int): GpuMesh = gpuMeshes.getOrPut(sides) {
        val mesh = DiceMeshFactory.create(sides)
        GpuMesh(
            positions = mesh.positions.toFloatBuffer(),
            normals = mesh.normals.toFloatBuffer(),
            vertexCount = mesh.vertexCount,
            numberPositions = mesh.numberPositions.toFloatBuffer(),
            numberNormals = mesh.numberNormals.toFloatBuffer(),
            numberVertexCount = mesh.numberVertexCount,
            valueFaceNormals = mesh.valueFaceNormals,
        )
    }

    private fun resolvedFaceNormal(die: VisualDie, mesh: GpuMesh): Vec3? {
        val numberedFaceCount = mesh.valueFaceNormals.size / 3
        if (numberedFaceCount == 0) return null
        val faceIndex = if (die.sides == 100) {
            // d100 uses the conventional percentile-d10 artwork (00..90), so the visual face
            // represents the tens component while the logical result remains the exact 1..100.
            if (die.value == 100) 0 else (die.value / 10).coerceIn(0, numberedFaceCount - 1)
        } else {
            (die.value - 1).coerceIn(0, numberedFaceCount - 1)
        }
        val offset = faceIndex * 3
        return Vec3(
            mesh.valueFaceNormals[offset],
            mesh.valueFaceNormals[offset + 1],
            mesh.valueFaceNormals[offset + 2],
        ).normalized()
    }

    private fun alignResolvedFace(model: FloatArray, die: VisualDie, mesh: GpuMesh) {
        val normal = resolvedFaceNormal(die, mesh) ?: return
        val correction = faceAlignmentCorrection(normal, 1f)
        if (correction.angleDegrees > 0.0001f) {
            Matrix.rotateM(
                model,
                0,
                correction.angleDegrees,
                correction.axis.x,
                correction.axis.y,
                correction.axis.z,
            )
        }
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer = ByteBuffer
        .allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(this@toFloatBuffer)
            position(0)
        }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(result)
            GLES20.glDeleteProgram(result)
            error("Unable to link dice shader: $info")
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Unable to compile dice shader: $info")
        }
        return shader
    }

    companion object {
        private const val MAX_VISIBLE_DICE = 12

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec2 vTexCoord;

            void main() {
                vec4 worldPosition = uModel * vec4(aPosition, 1.0);
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vPosition = worldPosition.xyz;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uPrimaryColor;
            uniform vec4 uSecondaryColor;
            uniform vec4 uMaterialLighting;
            uniform vec4 uMaterialAccent;
            uniform sampler2D uTexture;
            uniform float uUseTexture;
            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec2 vTexCoord;

            void main() {
                if (uUseTexture > 0.5) {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                    return;
                }
                vec3 normal = normalize(vNormal);
                vec3 lightDirection = normalize(vec3(0.35, 0.78, 0.72));
                vec3 viewDirection = normalize(vec3(0.0, 0.0, 6.5) - vPosition);
                vec3 halfDirection = normalize(lightDirection + viewDirection);
                float diffuse = max(dot(normal, lightDirection), 0.0);
                float specular = pow(max(dot(normal, halfDirection), 0.0), uMaterialLighting.w);
                float rim = pow(1.0 - max(dot(normal, viewDirection), 0.0), 2.0);
                float accentAmount = clamp(
                    uMaterialAccent.y * (0.20 + diffuse * 0.30) + rim * uMaterialAccent.x,
                    0.0,
                    0.82
                );
                vec3 baseColor = mix(uPrimaryColor.rgb, uSecondaryColor.rgb, accentAmount);
                float surfaceLight = uMaterialLighting.x + diffuse * uMaterialLighting.y;
                vec3 highlight = uSecondaryColor.rgb * specular * uMaterialLighting.z;
                vec3 innerGlow = mix(uPrimaryColor.rgb, uSecondaryColor.rgb, 0.55)
                    * (1.0 - diffuse) * uMaterialAccent.z;
                vec3 color = baseColor * surfaceLight + highlight + innerGlow;
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), uPrimaryColor.a);
            }
        """
    }
}
