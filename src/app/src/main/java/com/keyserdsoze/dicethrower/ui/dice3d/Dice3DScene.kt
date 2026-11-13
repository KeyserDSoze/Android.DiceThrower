package com.keyserdsoze.dicethrower.ui.dice3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.dice.CandidateVisualLighting
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
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
    previewMode: Boolean = false,
    winnerRevealed: Boolean = false,
    onSettled: (Long) -> Unit = {},
) {
    val sceneModifier = if (fullBleed) modifier else modifier.clip(RoundedCornerShape(24.dp))
    // A plain holder (not state): the lifecycle observer reads it when events arrive, so creating
    // the view never triggers an extra recomposition.
    val viewHolder = remember { arrayOfNulls<DiceGLView>(1) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            when (lifecycleEvent) {
                Lifecycle.Event.ON_RESUME -> viewHolder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> viewHolder[0]?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = sceneModifier,
        factory = { context ->
            DiceGLView(context, previewMode).also {
                it.onSettled = onSettled
                it.setScene(event, tableTheme, tableImage, tableImageKey, animateRoll)
                it.setWinnerRevealed(winnerRevealed)
                viewHolder[0] = it
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

private class DiceGLView(context: Context, previewMode: Boolean) : GLSurfaceView(context) {
    var onSettled: (Long) -> Unit = {}
    private val diceRenderer = DiceSceneRenderer(
        onSettled = { eventId -> post { onSettled(eventId) } },
        previewMode = previewMode,
        requestFrame = { requestRender() },
        numeralTypeface = runCatching { context.resources.getFont(R.font.cinzel_black) }.getOrNull(),
    )
    private var lastSceneKey: SceneKey? = null
    private var lastWinnerRevealed: Boolean? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(DiceEglConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(diceRenderer)
        // Frames are requested only while something moves: a settled table costs no GPU time.
        renderMode = RENDERMODE_WHEN_DIRTY
        if (!previewMode) DiceThrowSimulator.warmUpInBackground()
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
        requestRender()
    }

    fun setWinnerRevealed(revealed: Boolean) {
        if (lastWinnerRevealed == revealed) return
        lastWinnerRevealed = revealed
        queueEvent { diceRenderer.setWinnerRevealed(revealed) }
        requestRender()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }
}

/** 4x MSAA when available, otherwise the best plain configuration. */
private class DiceEglConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val base = intArrayOf(
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        )
        val attempts = listOf(
            base + intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
            base + intArrayOf(EGL10.EGL_NONE),
            intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE),
        )
        attempts.forEach { attributes -> choose(egl, display, attributes)?.let { return it } }
        throw IllegalArgumentException("No OpenGL ES 2 configuration available")
    }

    private fun choose(egl: EGL10, display: EGLDisplay, attributes: IntArray): EGLConfig? {
        val count = IntArray(1)
        if (!egl.eglChooseConfig(display, attributes, null, 0, count) || count[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!egl.eglChooseConfig(display, attributes, configs, count[0], count)) return null
        val value = IntArray(1)
        fun attribute(config: EGLConfig, name: Int): Int =
            if (egl.eglGetConfigAttrib(display, config, name, value)) value[0] else 0
        return configs.filterNotNull().firstOrNull { config ->
            attribute(config, EGL10.EGL_RED_SIZE) == 8 &&
                attribute(config, EGL10.EGL_GREEN_SIZE) == 8 &&
                attribute(config, EGL10.EGL_BLUE_SIZE) == 8
        } ?: configs.firstOrNull()
    }

    private companion object {
        const val EGL_OPENGL_ES2_BIT = 4
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

private class GpuMesh(
    val bodyBuffer: Int,
    val bodyVertexCount: Int,
    val glyphBuffer: Int,
    val glyphVertexCount: Int,
    /** Centre height of the die resting on a face, per unit scale. */
    val restHeight: Float,
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

    /** Visual die size (circumradius) for a throw; fewer dice are shown larger, crowded lanes smaller. */
    fun dieScaleFor(count: Int, largestLane: Int = 0): Float {
        val byCount = when {
            count <= 1 -> 0.86f
            count <= 2 -> 0.80f
            count <= 4 -> 0.72f
            count <= 8 -> 0.62f
            else -> 0.54f
        }
        val byLane = when {
            largestLane <= 0 -> byCount
            largestLane <= 3 -> 0.62f
            largestLane <= 6 -> 0.50f
            largestLane <= 9 -> 0.44f
            else -> 0.40f
        }
        return min(byCount, byLane)
    }
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

private class DiceProgram(val id: Int) {
    val position = GLES20.glGetAttribLocation(id, "aPosition")
    val normal = GLES20.glGetAttribLocation(id, "aNormal")
    val uv = GLES20.glGetAttribLocation(id, "aUv")
    val mvp = GLES20.glGetUniformLocation(id, "uMvp")
    val model = GLES20.glGetUniformLocation(id, "uModel")
    val camera = GLES20.glGetUniformLocation(id, "uCamera")
    val base = GLES20.glGetUniformLocation(id, "uBase")
    val accent = GLES20.glGetUniformLocation(id, "uAccent")
    val ink = GLES20.glGetUniformLocation(id, "uInk")
    val floor = GLES20.glGetUniformLocation(id, "uFloor")
    val surface = GLES20.glGetUniformLocation(id, "uSurface")
    val detail = GLES20.glGetUniformLocation(id, "uDetail")
    val tint = GLES20.glGetUniformLocation(id, "uTint")
    val ground = GLES20.glGetUniformLocation(id, "uGround")
    val center = GLES20.glGetUniformLocation(id, "uCenter")
    val inkPass = GLES20.glGetUniformLocation(id, "uInkPass")
    val glyphs = GLES20.glGetUniformLocation(id, "uGlyphs")
}

private class DiceSceneRenderer(
    private val onSettled: (Long) -> Unit,
    private val previewMode: Boolean,
    private val requestFrame: () -> Unit,
    private val numeralTypeface: Typeface?,
) : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val rotation = FloatArray(16)

    private var dice: DiceProgram? = null
    private var tableProgram = 0
    private var shadowProgram = 0
    private var glyphTexture = 0
    private var tableBuffer = 0
    private var shadowBuffer = 0
    private val gpuMeshes = HashMap<Int, GpuMesh>()

    private var visualDice: List<VisualDie> = emptyList()
    private var baked: BakedDiceThrow? = null
    private var staticPoses: List<DiePose> = emptyList()
    private var dieScale = DiceTableViewport.dieScaleFor(1)
    private var throwStartedNanos = 0L
    private var persistentCount = 0
    private var tableTheme = DiceTableTheme.ARCANE
    private var animateRoll = true
    private var winnerRevealed = false
    private var winnerRevealNanos = 0L
    private var visualSettings = RollVisualEffectsSettings()
    private var effectImpactStartedNanos = 0L
    private var currentEventId = 0L
    private var settledReported = false
    private var cameraDistance = 14f
    private var tableImage: Bitmap? = null
    private var tableTexture = 0
    private var tableTextureDirty = false
    private var viewportAspect = DiceTableViewport.HALF_WIDTH / DiceTableViewport.HALF_HEIGHT
    private var visualBounds = DiceTableVisualBounds(DiceTableViewport.HALF_WIDTH, DiceTableViewport.HALF_HEIGHT)
    private var tableGeometryDirty = true

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
        val now = SystemClock.elapsedRealtimeNanos()
        // Keep already-settled dice exactly where they stopped; effect stages add new dice only.
        val previous = settledPoses()
        val keep = if (animateRoll && event.persistentDiceCount > 0 && previous.size >= event.persistentDiceCount)
            previous.take(event.persistentDiceCount) else emptyList()
        val appearanceBySlot = event.appearances.associateBy { it.slotKey }
        visualSettings = event.visualSettings
        effectImpactStartedNanos = if (animateRoll && visualSettings.cameraImpact &&
            event.effectAccentComponents.isNotEmpty()) now else 0L
        winnerRevealed = false
        visualDice = buildList {
            event.result.components.forEachIndexed { componentIndex, component ->
                if (!DieGeometries.isSupported(component.sides)) return@forEachIndexed
                component.rolls.forEachIndexed { index, value ->
                    if (size < MAX_VISIBLE_DICE) {
                        add(
                            VisualDie(
                                sides = component.sides,
                                value = value,
                                phase = ((component.sides * 37 + value * 19 + index * 53) % 360).toFloat(),
                                renderStyle = DiceRenderStyleFactory.create(appearanceBySlot["$componentIndex:$index"]),
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
        persistentCount = keep.size
        dieScale = when {
            previewMode -> PREVIEW_SCALE
            keep.isNotEmpty() -> dieScale
            else -> DiceTableViewport.dieScaleFor(
                visualDice.size,
                visualDice.mapNotNull { it.candidateGroup }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0,
            )
        }
        if (animateRoll && !previewMode) {
            val specs = visualDice.mapIndexed { index, die ->
                DieThrowSpec(die.sides, die.value, die.candidateGroup, keep.getOrNull(index))
            }
            val bakeStarted = SystemClock.elapsedRealtimeNanos()
            baked = runCatching {
                DiceThrowSimulator.bake(
                    specs = specs,
                    seed = event.id,
                    dieScale = dieScale,
                    spawnFromEdge = event.visualSettings.actionCues && event.effectAccentComponents.isNotEmpty(),
                )
            }.onFailure { Log.e(TAG, "Dice simulation failed; showing resting dice", it) }.getOrNull()
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Baked ${specs.size} dice in ${(SystemClock.elapsedRealtimeNanos() - bakeStarted) / 1_000_000} ms")
            }
            staticPoses = if (baked == null) restingLayout(visualDice) else emptyList()
        } else {
            baked = null
            staticPoses = restingLayout(visualDice)
        }
        // The clock starts after the (few millisecond) pre-simulation, so no frame is skipped.
        throwStartedNanos = SystemClock.elapsedRealtimeNanos()
        settledReported = false
    }

    private fun settledPoses(): List<DiePose> {
        val current = baked
        return if (current != null) List(current.dieCount) { current.finalPose(it) } else staticPoses
    }

    private fun restingLayout(dice: List<VisualDie>): List<DiePose> {
        if (dice.isEmpty()) return emptyList()
        if (previewMode) return dice.map { die ->
            val resting = DiceThrowSimulator.restingPose(die.sides, die.value, 0f, 0f, 0.38f, dieScale)
            val tilt = Quat.fromAxisAngle(Vec3.UNIT_X, -0.46f) * Quat.fromAxisAngle(Vec3.UNIT_Y, 0.30f)
            DiePose(Vec3(0f, 0.04f, 0.32f), (tilt * resting.rotation).normalized())
        }
        val poses = arrayOfNulls<DiePose>(dice.size)
        dice.indices.groupBy { dice[it].candidateGroup }.forEach { (lane, members) ->
            val count = members.size
            val columns = ceil(sqrt(count.toFloat())).toInt().coerceAtLeast(1)
            val rows = ceil(count.toFloat() / columns).toInt().coerceAtLeast(1)
            val centerY = when (lane) {
                null -> if (dice.any { it.candidateGroup != null }) -3.9f else 0.4f
                else -> CandidateLaneLayout.staticCenter(lane)
            }
            members.forEachIndexed { order, dieIndex ->
                val row = order / columns
                val column = order % columns
                val rowCount = (count - row * columns).coerceAtMost(columns)
                val x = (column - (rowCount - 1) / 2f) * dieScale * 2.35f
                var y = centerY + ((rows - 1) / 2f - row) * dieScale * 2.25f
                if (lane != null) {
                    val (minY, maxY) = CandidateLaneLayout.band(lane, DiceTableViewport.HALF_HEIGHT)
                    y = y.coerceIn(minY + dieScale, maxY - dieScale)
                }
                val die = dice[dieIndex]
                poses[dieIndex] = DiceThrowSimulator.restingPose(
                    die.sides, die.value, x, y, die.phase * (PI.toFloat() / 180f), dieScale,
                )
            }
        }
        return poses.map { it!! }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.018f, 0.022f, 0.04f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        // A new EGL context invalidates every GPU object.
        gpuMeshes.clear()
        tableTexture = 0
        tableTextureDirty = tableImage != null
        tableGeometryDirty = true
        dice = (createProgram(DiceShaders.DICE_VERTEX, DiceShaders.DICE_FRAGMENT)
            ?: createProgram(DiceShaders.DICE_VERTEX, FALLBACK_DICE_FRAGMENT))?.let(::DiceProgram)
        tableProgram = createProgram(DiceShaders.TABLE_VERTEX, DiceShaders.TABLE_FRAGMENT) ?: 0
        shadowProgram = createProgram(DiceShaders.SHADOW_VERTEX, DiceShaders.SHADOW_FRAGMENT) ?: 0
        glyphTexture = uploadGlyphAtlas()
        shadowBuffer = uploadBuffer(floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f))
        tableBuffer = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        viewportAspect = aspect.coerceAtLeast(0.25f)
        // Standalone dice previews need a close-up camera, unlike the full throwing table.
        cameraDistance = if (previewMode) 4.2f else DiceTableViewport.cameraDistanceFor(viewportAspect)
        visualBounds = DiceTableViewport.visualBoundsFor(viewportAspect, cameraDistance)
        tableGeometryDirty = true
        Matrix.perspectiveM(projection, 0, DiceTableViewport.FOV_DEGREES, viewportAspect, 0.1f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val program = dice ?: return
        val now = SystemClock.elapsedRealtimeNanos()
        val elapsed = (now - throwStartedNanos).coerceAtLeast(0L) / 1_000_000_000f
        val bakedThrow = baked
        val poses = if (bakedThrow != null) List(visualDice.size) { bakedThrow.pose(it, elapsed) } else staticPoses
        val throwing = bakedThrow != null && elapsed < bakedThrow.durationSeconds

        val impactAge = if (effectImpactStartedNanos > 0L) (now - effectImpactStartedNanos) / 1_000_000_000f else 10f
        val impactStrength = if (animateRoll && visualSettings.cameraImpact)
            (1f - impactAge / 0.48f).coerceIn(0f, 1f) * 0.045f else 0f
        val eyeX = sin(impactAge * 93f) * impactStrength
        val eyeY = -0.12f + sin(impactAge * 117f) * impactStrength * 0.62f
        Matrix.setLookAtM(view, 0, eyeX, eyeY, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        drawTable()
        drawShadows(poses)
        drawDice(program, poses, now, throwing, eyeX, eyeY)

        val settled = if (bakedThrow != null) !throwing else currentEventId != 0L
        if (settled && !settledReported) {
            settledReported = true
            onSettled(currentEventId)
        }
        val pulsing = winnerRevealed && visualSettings.winnerSpotlight && (now - winnerRevealNanos) < 1_150_000_000L
        val shaking = impactStrength > 0f
        if (throwing || pulsing || shaking) requestFrame()
    }

    private fun drawTable() {
        if (tableProgram == 0) return
        val texture = ensureTableTexture()
        if (tableGeometryDirty || tableBuffer == 0) {
            val coordinates = tableTextureCoordinatesFor(
                tableImage?.takeUnless(Bitmap::isRecycled)?.let { it.width.toFloat() / it.height.toFloat() },
                viewportAspect,
            )
            val w = visualBounds.halfWidth
            val h = visualBounds.halfHeight
            val z = DiceTableViewport.TABLE_Z
            val corners = floatArrayOf(-w, -h, w, -h, w, h, -w, -h, w, h, -w, h)
            val data = FloatArray(6 * 5)
            for (i in 0 until 6) {
                data[i * 5] = corners[i * 2]
                data[i * 5 + 1] = corners[i * 2 + 1]
                data[i * 5 + 2] = z
                data[i * 5 + 3] = coordinates[i * 2]
                data[i * 5 + 4] = coordinates[i * 2 + 1]
            }
            if (tableBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(tableBuffer), 0)
            tableBuffer = uploadBuffer(data)
            tableGeometryDirty = false
        }
        GLES20.glUseProgram(tableProgram)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        val position = GLES20.glGetAttribLocation(tableProgram, "aPosition")
        val uv = GLES20.glGetAttribLocation(tableProgram, "aUv")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tableBuffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(tableProgram, "uMvp"), 1, false, viewProjection, 0)
        val palette = tableTheme.palette()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(tableProgram, "uFeltDark"), palette[0], palette[1], palette[2])
        GLES20.glUniform3f(GLES20.glGetUniformLocation(tableProgram, "uFeltLight"), palette[3], palette[4], palette[5])
        GLES20.glUniform2f(GLES20.glGetUniformLocation(tableProgram, "uHalf"), visualBounds.halfWidth, visualBounds.halfHeight)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(tableProgram, "uTexture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(tableProgram, "uUseTexture"), if (texture != 0) 1f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawShadows(poses: List<DiePose>) {
        if (shadowProgram == 0 || poses.isEmpty()) return
        GLES20.glUseProgram(shadowProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        val corner = GLES20.glGetAttribLocation(shadowProgram, "aCorner")
        val center = GLES20.glGetUniformLocation(shadowProgram, "uCenter")
        val radius = GLES20.glGetUniformLocation(shadowProgram, "uRadius")
        val opacity = GLES20.glGetUniformLocation(shadowProgram, "uOpacity")
        val core = GLES20.glGetUniformLocation(shadowProgram, "uCore")
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(shadowProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shadowBuffer)
        GLES20.glEnableVertexAttribArray(corner)
        GLES20.glVertexAttribPointer(corner, 2, GLES20.GL_FLOAT, false, 8, 0)
        poses.forEachIndexed { index, pose ->
            val mesh = gpuMesh(visualDice[index].sides) ?: return@forEachIndexed
            val height = (pose.position.z - mesh.restHeight * dieScale).coerceAtLeast(0f) / dieScale
            val size = dieScale * (0.98f + 0.45f * height)
            GLES20.glUniform3f(
                center,
                pose.position.x + dieScale * (0.07f + 0.30f * height),
                pose.position.y - dieScale * (0.09f + 0.36f * height),
                DiceTableViewport.TABLE_Z + 0.004f,
            )
            GLES20.glUniform2f(radius, size, size * 0.94f)
            val brightness = CandidateVisualLighting.brightness(winnerRevealed, visualSettings.winnerSpotlight, visualDice[index].dimmed)
            GLES20.glUniform1f(opacity, 0.50f / (1f + 0.9f * height) * (0.55f + 0.45f * brightness))
            GLES20.glUniform1f(core, 0.34f * (1f - height * 2.2f).coerceIn(0f, 1f))
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        }
        GLES20.glDisableVertexAttribArray(corner)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawDice(program: DiceProgram, poses: List<DiePose>, now: Long, throwing: Boolean, eyeX: Float, eyeY: Float) {
        if (poses.isEmpty()) return
        GLES20.glUseProgram(program.id)
        GLES20.glUniform3f(program.camera, eyeX, eyeY, cameraDistance)
        val floor = tableTheme.palette()
        GLES20.glUniform3f(program.floor, floor[3] * floor[3], floor[4] * floor[4], floor[5] * floor[5])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphTexture)
        GLES20.glUniform1i(program.glyphs, 0)
        // Previews hold the die in the air: no contact occlusion there.
        GLES20.glUniform2f(
            program.ground,
            if (previewMode) DiceTableViewport.TABLE_Z - 50f else DiceTableViewport.TABLE_Z,
            dieScale * 1.15f,
        )
        val revealAge = (now - winnerRevealNanos) / 1_000_000_000f

        visualDice.forEachIndexed { index, die ->
            val mesh = gpuMesh(die.sides) ?: return@forEachIndexed
            val pose = poses[index]
            pose.rotation.toMatrix(rotation)
            for (column in 0..2) for (row in 0..2) model[column * 4 + row] = rotation[column * 4 + row] * dieScale
            model[3] = 0f; model[7] = 0f; model[11] = 0f
            model[12] = pose.position.x
            model[13] = pose.position.y
            model[14] = pose.position.z + DiceTableViewport.TABLE_Z
            model[15] = 1f
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
            GLES20.glUniformMatrix4fv(program.mvp, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(program.model, 1, false, model, 0)

            val style = die.renderStyle
            // The less favourable candidate stays visible but recedes once the verdict is revealed.
            val brightness = CandidateVisualLighting.brightness(winnerRevealed, visualSettings.winnerSpotlight, die.dimmed)
            val pulse = CandidateVisualLighting.chosenPulse(winnerRevealed, visualSettings.winnerSpotlight, die.chosenCandidate, revealAge)
            // Effect accents are visual only: green/cyan bonuses, crimson maluses.
            val accent = when (die.effectAccent) {
                EffectType.BONUS -> floatArrayOf(0.20f, 0.95f, 0.62f)
                EffectType.MALUS -> floatArrayOf(0.99f, 0.25f, 0.35f)
                null -> null
            }
            val alreadySettled = index < persistentCount
            val flash = when {
                accent == null -> 0f
                throwing && !alreadySettled -> 0.22f + 0.12f * sin(now / 240_000_000.0).toFloat()
                else -> 0.20f
            }
            val primary = style.primary.linear()
            val base = if (accent == null) primary else FloatArray(3) { channel ->
                val target = accent[channel] * accent[channel]
                primary[channel] * (1f - flash) + target * flash
            }
            val secondary = style.secondary.linear()
            val ink = style.ink.linear()
            GLES20.glUniform3f(program.base, base[0], base[1], base[2])
            GLES20.glUniform3f(program.accent, secondary[0], secondary[1], secondary[2])
            GLES20.glUniform3f(program.ink, ink[0], ink[1], ink[2])
            val lighting = style.lighting
            GLES20.glUniform4f(program.surface, lighting.roughness, lighting.metalness, lighting.clearcoat, lighting.translucency)
            GLES20.glUniform4f(program.detail, lighting.swirl, lighting.innerGlow, lighting.sparkle, die.phase * 0.071f + index * 1.37f)
            GLES20.glUniform4f(program.center, model[12], model[13], model[14], lighting.curvature)
            val glow = if (accent != null) 0.06f * flash / 0.2f else 0f
            GLES20.glUniform4f(
                program.tint,
                pulse * 0.42f + (accent?.get(0) ?: 0f) * glow,
                pulse * 0.30f + (accent?.get(1) ?: 0f) * glow,
                pulse * 0.10f + (accent?.get(2) ?: 0f) * glow,
                brightness,
            )

            GLES20.glUniform1f(program.inkPass, 0f)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.bodyBuffer)
            GLES20.glEnableVertexAttribArray(program.position)
            GLES20.glVertexAttribPointer(program.position, 3, GLES20.GL_FLOAT, false, 24, 0)
            GLES20.glEnableVertexAttribArray(program.normal)
            GLES20.glVertexAttribPointer(program.normal, 3, GLES20.GL_FLOAT, false, 24, 12)
            if (program.uv >= 0) {
                GLES20.glDisableVertexAttribArray(program.uv)
                GLES20.glVertexAttrib2f(program.uv, 0f, 0f)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.bodyVertexCount)

            if (mesh.glyphVertexCount > 0 && glyphTexture != 0) {
                GLES20.glUniform1f(program.inkPass, 1f)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glDepthMask(false)
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
                GLES20.glPolygonOffset(-1f, -2f)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.glyphBuffer)
                GLES20.glVertexAttribPointer(program.position, 3, GLES20.GL_FLOAT, false, 32, 0)
                GLES20.glVertexAttribPointer(program.normal, 3, GLES20.GL_FLOAT, false, 32, 12)
                if (program.uv >= 0) {
                    GLES20.glEnableVertexAttribArray(program.uv)
                    GLES20.glVertexAttribPointer(program.uv, 2, GLES20.GL_FLOAT, false, 32, 24)
                }
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.glyphVertexCount)
                if (program.uv >= 0) GLES20.glDisableVertexAttribArray(program.uv)
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
                GLES20.glDepthMask(true)
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }
        GLES20.glDisableVertexAttribArray(program.position)
        GLES20.glDisableVertexAttribArray(program.normal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun gpuMesh(sides: Int): GpuMesh? {
        gpuMeshes[sides]?.let { return it }
        if (!DieGeometries.isSupported(sides)) return null
        val mesh = DiceMeshFactory.create(sides)
        val geometry = DieGeometries.forSides(sides)
        return GpuMesh(
            bodyBuffer = uploadBuffer(mesh.body),
            bodyVertexCount = mesh.bodyVertexCount,
            glyphBuffer = if (mesh.glyphVertexCount > 0) uploadBuffer(mesh.glyphs) else 0,
            glyphVertexCount = mesh.glyphVertexCount,
            restHeight = geometry.inradius,
        ).also { gpuMeshes[sides] = it }
    }

    private fun ensureTableTexture(): Int {
        if (!tableTextureDirty) return tableTexture
        if (tableTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(tableTexture), 0)
            tableTexture = 0
        }
        tableGeometryDirty = true
        tableTextureDirty = false
        val bitmap = tableImage
        if (bitmap == null || bitmap.isRecycled) return 0
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
        return tableTexture
    }

    private fun uploadGlyphAtlas(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, glyphAtlas(numeralTypeface), 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        if (GLES20.glGetString(GLES20.GL_EXTENSIONS)?.contains("GL_EXT_texture_filter_anisotropic") == true) {
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY, 4f)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    private fun uploadBuffer(data: FloatArray): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * Float.SIZE_BYTES, data.toFloatBuffer(), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
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

    private fun FloatArray.toFloatBuffer(): FloatBuffer = ByteBuffer
        .allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(this@toFloatBuffer)
            position(0)
        }

    /** Returns 0-free program id, or null so a simpler fallback can be tried instead of crashing. */
    private fun createProgram(vertexSource: String, fragmentSource: String): Int? {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource) ?: return null
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource) ?: run {
            GLES20.glDeleteShader(vertex)
            return null
        }
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Unable to link dice shader: ${GLES20.glGetProgramInfoLog(result)}")
            GLES20.glDeleteProgram(result)
            return null
        }
        return result
    }

    private fun compileShader(type: Int, source: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Unable to compile dice shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    companion object {
        private const val TAG = "Dice3DScene"
        private const val MAX_VISIBLE_DICE = 12
        private const val PREVIEW_SCALE = 1.0f
        private const val TEXTURE_MAX_ANISOTROPY = 0x84FE

        @Volatile
        private var sharedAtlas: Bitmap? = null

        /** Shared, immutable numeral atlas (about 1 MB), painted once per process in the brand typeface. */
        private fun glyphAtlas(typeface: Typeface?): Bitmap = sharedAtlas ?: synchronized(this) {
            sharedAtlas ?: (typeface?.let(DiceGlyphAtlas::render) ?: DiceGlyphAtlas.render()).also { sharedAtlas = it }
        }

        /** Minimal Lambert shading used only if a driver rejects the full material shader. */
        private const val FALLBACK_DICE_FRAGMENT = """
            precision mediump float;
            uniform vec3 uBase;
            uniform vec3 uInk;
            uniform vec4 uTint;
            uniform float uInkPass;
            uniform sampler2D uGlyphs;
            varying vec3 vNormal;
            varying vec2 vUv;
            void main() {
                vec3 n = normalize(vNormal);
                float light = 0.35 + 0.65 * max(dot(n, vec3(-0.42, 0.53, 0.74)), 0.0);
                if (uInkPass > 0.5) {
                    float a = texture2D(uGlyphs, vUv).a;
                    if (a < 0.01) discard;
                    gl_FragColor = vec4(pow(uInk * light, vec3(0.4545)), a);
                    return;
                }
                gl_FragColor = vec4(pow(uBase * light * uTint.w + uTint.rgb, vec3(0.4545)), 1.0);
            }
        """
    }
}
