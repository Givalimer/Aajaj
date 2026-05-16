package com.givalimer.aajaj

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val world = World()
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private var shaderProgram = 0
    private var screenWidth = 1
    private var screenHeight = 1
    private var lastTime = System.nanoTime()

    // Shader source
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        attribute float aShade;
        varying vec2 vTexCoord;
        varying float vShade;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
            vShade = aShade;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        varying float vShade;
        uniform sampler2D uTexture;
        uniform vec4 uFogColor;
        uniform float uFogStart;
        uniform float uFogEnd;
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            color.rgb *= vShade;
            // Simple distance fog
            float dist = gl_FragCoord.z / gl_FragCoord.w;
            float fogFactor = clamp((uFogEnd - dist) / (uFogEnd - uFogStart), 0.0, 1.0);
            color.rgb = mix(uFogColor.rgb, color.rgb, fogFactor);
            if (color.a < 0.5) discard;
            gl_FragColor = color;
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f) // Sky blue
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        shaderProgram = createProgram(vertexShaderCode, fragmentShaderCode)
        TextureManager.loadTextures(context)

        // Generate world on background thread to avoid crash
        Thread {
            world.generate()
        }.start()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 70f, ratio, 0.1f, 150f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        lastTime = now

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Don't render until world is ready
        if (!world.isReady) return

        // Update player
        world.player.update(dt, world)
        GLES20.glUseProgram(shaderProgram)

        // Camera
        val p = world.player
        val eyeX = p.x
        val eyeY = p.y + p.eyeHeight
        val eyeZ = p.z
        val lookX = eyeX + p.lookDirX()
        val lookY = eyeY + p.lookDirY()
        val lookZ = eyeZ + p.lookDirZ()

        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Set uniforms
        val mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        val fogColorHandle = GLES20.glGetUniformLocation(shaderProgram, "uFogColor")
        val fogStartHandle = GLES20.glGetUniformLocation(shaderProgram, "uFogStart")
        val fogEndHandle = GLES20.glGetUniformLocation(shaderProgram, "uFogEnd")

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, vpMatrix, 0)
        GLES20.glUniform4f(fogColorHandle, 0.53f, 0.81f, 0.92f, 1.0f)
        GLES20.glUniform1f(fogStartHandle, 80f)
        GLES20.glUniform1f(fogEndHandle, 130f)

        // Bind texture atlas
        val texHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureManager.atlasTextureId)
        GLES20.glUniform1i(texHandle, 0)

        // Render chunks
        val posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        val shadeHandle = GLES20.glGetAttribLocation(shaderProgram, "aShade")

        world.renderChunks(posHandle, texCoordHandle, shadeHandle, mvpHandle, vpMatrix)

        // Render HUD (crosshair, hotbar) via overlay
        renderHUD()
    }

    private fun renderHUD() {
        // Simple crosshair with GL lines
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // We'll draw HUD via a separate simple shader or just skip for now
        // The overlay UI will be handled via Android Views in a future iteration
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    fun breakBlock() {
        val p = world.player
        val hit = world.raycast(p.x, p.y + p.eyeHeight, p.z, p.lookDirX(), p.lookDirY(), p.lookDirZ(), 5f)
        if (hit != null) {
            world.setBlock(hit.x, hit.y, hit.z, BlockType.AIR)
        }
    }

    fun placeBlock() {
        val p = world.player
        val hit = world.raycast(p.x, p.y + p.eyeHeight, p.z, p.lookDirX(), p.lookDirY(), p.lookDirZ(), 5f)
        if (hit != null && hit.face != null) {
            val nx = hit.x + hit.face.dx
            val ny = hit.y + hit.face.dy
            val nz = hit.z + hit.face.dz
            // Don't place inside player
            if (!world.player.collidesWith(nx, ny, nz)) {
                world.setBlock(nx, ny, nz, world.player.selectedBlock)
            }
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
