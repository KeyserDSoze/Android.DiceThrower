package com.keyserdsoze.dicethrower.ui.dice3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun Dice3DScene(
    event: DiceRollVisualEvent,
    modifier: Modifier = Modifier,
    tableTheme: DiceTableTheme = DiceTableTheme.ARCANE,
    animateRoll: Boolean = true,
    onSettled: (Long) -> Unit = {},
) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        factory = { context ->
            DiceGLView(context).also {
                it.onSettled = onSettled
                it.setScene(event, tableTheme, animateRoll)
            }
        },
        update = { view ->
            view.onSettled = onSettled
            view.setScene(event, tableTheme, animateRoll)
        },
    )
}

private class DiceGLView(context: Context) : GLSurfaceView(context) {
    var onSettled: (Long) -> Unit = {}
    private val diceRenderer = DiceSceneRenderer { eventId -> post { onSettled(eventId) } }
    private var lastSceneKey: Triple<Long, DiceTableTheme, Boolean>? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setPreserveEGLContextOnPause(true)
        setRenderer(diceRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setScene(event: DiceRollVisualEvent, tableTheme: DiceTableTheme, animateRoll: Boolean) {
        val key = Triple(event.id, tableTheme, animateRoll)
        if (key == lastSceneKey) return
        lastSceneKey = key
        queueEvent { diceRenderer.setEvent(event, tableTheme, animateRoll) }
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
)

private data class GpuMesh(
    val positions: FloatBuffer,
    val normals: FloatBuffer,
    val vertexCount: Int,
    val numberPositions: FloatBuffer,
    val numberNormals: FloatBuffer,
    val numberVertexCount: Int,
)

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

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private val gpuMeshes = mutableMapOf<Int, GpuMesh>()
    private var dice: List<VisualDie> = emptyList()
    private var tableTheme = DiceTableTheme.ARCANE
    private var physics: DiceTablePhysics? = null
    private var animateRoll = true
    private var currentEventId = 0L
    private var lastFrameAt = SystemClock.elapsedRealtimeNanos()
    private var settledReported = false

    private val tablePositions = floatArrayOf(
        -2.95f, -3.7f, -0.72f, 2.95f, -3.7f, -0.72f, 2.95f, 3.7f, -0.72f,
        -2.95f, -3.7f, -0.72f, 2.95f, 3.7f, -0.72f, -2.95f, 3.7f, -0.72f,
    ).toFloatBuffer()
    private val tableNormals = FloatArray(18) { index -> if (index % 3 == 2) 1f else 0f }.toFloatBuffer()

    fun setEvent(event: DiceRollVisualEvent, tableTheme: DiceTableTheme, animateRoll: Boolean) {
        val appearanceBySlot = event.appearances.associateBy { it.slotKey }
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
                            ),
                        )
                    }
                }
            }
        }
        this.tableTheme = tableTheme
        this.animateRoll = animateRoll
        currentEventId = event.id
        physics = if (animateRoll) DiceTablePhysics(dice.size, event.id) else null
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
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 36f, aspect, 0.1f, 30f)
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
        val states = physics?.states() ?: staticStates(count, dieScale)

        Matrix.setLookAtM(view, 0, 0f, -0.12f, 8.8f, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)

        drawTable()

        dice.forEachIndexed { index, die ->
            val state = states[index]

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, state.x, state.y, 0f)
            Matrix.rotateM(model, 0, state.angleX + die.phase * 0.17f, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, state.angleY + die.value * 3.7f, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, state.angleZ, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, dieScale, dieScale, dieScale)

            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

            val gpuMesh = gpuMesh(die.sides)
            gpuMesh.positions.position(0)
            gpuMesh.normals.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.positions)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.normals)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)

            val style = die.renderStyle
            GLES20.glUniform4f(
                primaryColorHandle,
                style.primary.red,
                style.primary.green,
                style.primary.blue,
                style.primary.alpha,
            )
            GLES20.glUniform4f(
                secondaryColorHandle,
                style.secondary.red,
                style.secondary.green,
                style.secondary.blue,
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
                style.lighting.rim,
                style.lighting.accentMix,
                style.lighting.innerGlow,
                0f,
            )
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpuMesh.vertexCount)

            gpuMesh.numberPositions.position(0)
            gpuMesh.numberNormals.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.numberPositions)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, gpuMesh.numberNormals)
            val luminance = style.primary.red * 0.299f + style.primary.green * 0.587f + style.primary.blue * 0.114f
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

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
        )
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
            varying vec3 vNormal;
            varying vec3 vPosition;

            void main() {
                vec4 worldPosition = uModel * vec4(aPosition, 1.0);
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vPosition = worldPosition.xyz;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uPrimaryColor;
            uniform vec4 uSecondaryColor;
            uniform vec4 uMaterialLighting;
            uniform vec4 uMaterialAccent;
            varying vec3 vNormal;
            varying vec3 vPosition;

            void main() {
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
