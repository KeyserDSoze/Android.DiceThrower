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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Dice3DScene(
    event: DiceRollVisualEvent,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        factory = { context ->
            DiceGLView(context).also { it.setEvent(event) }
        },
        update = { view -> view.setEvent(event) },
    )
}

private class DiceGLView(context: Context) : GLSurfaceView(context) {
    private val diceRenderer = DiceSceneRenderer()
    private var lastEventId: Long? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setPreserveEGLContextOnPause(true)
        setRenderer(diceRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setEvent(event: DiceRollVisualEvent) {
        if (event.id == lastEventId) return
        lastEventId = event.id
        queueEvent { diceRenderer.setEvent(event) }
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
)

private class DiceSceneRenderer : GLSurfaceView.Renderer {
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
    private var animationStartedAt = SystemClock.elapsedRealtimeNanos()

    fun setEvent(event: DiceRollVisualEvent) {
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
        animationStartedAt = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.09f, 1f)
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
        if (program == 0 || dice.isEmpty()) return

        val elapsed = (SystemClock.elapsedRealtimeNanos() - animationStartedAt) / 1_000_000_000f
        val count = dice.size
        val columns = ceil(sqrt(count.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toFloat() / columns).toInt().coerceAtLeast(1)
        val dieScale = when {
            count <= 1 -> 1.16f
            count <= 4 -> 0.88f
            count <= 9 -> 0.67f
            else -> 0.53f
        }
        val spacingX = 2.15f * dieScale
        val spacingY = 2.02f * dieScale
        val cameraZ = 5.7f + rows * 0.34f

        Matrix.setLookAtM(view, 0, 0f, 0f, cameraZ, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)

        dice.forEachIndexed { index, die ->
            val row = index / columns
            val column = index % columns
            val rowCount = min(columns, count - row * columns)
            val x = (column - (rowCount - 1) / 2f) * spacingX
            val yBase = ((rows - 1) / 2f - row) * spacingY

            val burstProgress = (elapsed / 0.95f).coerceIn(0f, 1f)
            val bounce = if (elapsed < 0.95f) {
                (sin(burstProgress * PI).toFloat() * 0.55f) * (1f - burstProgress * 0.45f)
            } else {
                sin((elapsed - 0.95f) * 1.8f + die.phase) * 0.025f
            }
            val fastSpin = if (elapsed < 0.95f) elapsed * 620f else 589f + (elapsed - 0.95f) * 13f

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, yBase + bounce, 0f)
            Matrix.rotateM(model, 0, fastSpin + die.phase, 0.72f, 1f, 0.32f)
            Matrix.rotateM(model, 0, fastSpin * 0.71f + die.value * 17f, 1f, 0.26f, 0.83f)
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
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun gpuMesh(sides: Int): GpuMesh = gpuMeshes.getOrPut(sides) {
        val mesh = DiceMeshFactory.create(sides)
        GpuMesh(
            positions = mesh.positions.toFloatBuffer(),
            normals = mesh.normals.toFloatBuffer(),
            vertexCount = mesh.vertexCount,
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
