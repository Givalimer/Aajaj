package com.givalimer.aajaj

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

/**
 * Falling-sand SurfaceView.
 *
 * Pipeline per frame (~60 FPS target):
 *  1. Advance the simulation one step.
 *  2. Render each cell into a pixel IntArray with slight per-pixel variation so
 *     the result looks textured instead of flat.
 *  3. Blit the ARGB bitmap to the canvas, scaled up to screen size.
 *
 * Input: continuous touch paints the current material in a circular brush.
 * Line interpolation between touch events so fast drags still leave continuous trails.
 */
class SandboxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback, Runnable {

    @Volatile var currentMaterial: Material = Material.SAND
    @Volatile var brushRadius: Int = 3

    private val cellSize = 4             // one simulation cell = 4 screen pixels
    private var cols = 0
    private var rows = 0
    private lateinit var world: World
    private lateinit var pixels: IntArray
    private lateinit var bitmap: Bitmap

    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val destRect = RectF()
    private val rng = Random.Default

    private var thread: Thread? = null
    @Volatile private var running = false

    // Track last touch so we can interpolate a line between events
    private var lastX = -1f
    private var lastY = -1f
    private var touching = false

    private var tick = 0

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        cols = (width / cellSize).coerceAtLeast(4)
        rows = (height / cellSize).coerceAtLeast(4)
        world = World(cols, rows)
        pixels = IntArray(cols * rows)
        bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        resumeLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { pauseLoop() }

    fun resumeLoop() {
        if (running) return
        running = true
        thread = Thread(this, "PixelAlchemyLoop").also { it.start() }
    }

    fun pauseLoop() {
        running = false
        thread?.join(250)
        thread = null
    }

    fun clearWorld() {
        if (::world.isInitialized) world.clear()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                paintAt(event.x, event.y)
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                // Interpolate from last touch point to avoid gaps on fast drags.
                if (lastX >= 0f) {
                    val steps = kotlin.math.max(
                        1,
                        (kotlin.math.hypot((event.x - lastX).toDouble(), (event.y - lastY).toDouble()) / (cellSize * 1.5)).toInt()
                    )
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        paintAt(lastX + (event.x - lastX) * t, lastY + (event.y - lastY) * t)
                    }
                } else paintAt(event.x, event.y)
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                lastX = -1f; lastY = -1f
            }
        }
        return true
    }

    private fun paintAt(px: Float, py: Float) {
        if (!::world.isInitialized) return
        val cx = (px / cellSize).toInt()
        val cy = (py / cellSize).toInt()
        world.paintBrush(cx, cy, brushRadius, currentMaterial)
    }

    override fun run() {
        val targetMs = 16L
        while (running) {
            val start = System.currentTimeMillis()
            if (::world.isInitialized) {
                world.step()
                render()
                drawFrame()
            }
            val elapsed = System.currentTimeMillis() - start
            val sleep = targetMs - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    // --- rendering ---

    private fun render() {
        tick++
        val g = world.grid
        val n = cols * rows
        for (i in 0 until n) {
            val mOrd = g[i].toInt()
            pixels[i] = colorFor(mOrd, i)
        }
    }

    /** Returns the pixel color for the given cell. Adds jitter per cell so materials
     *  don't look flat; fire and lava animate by mixing colors based on tick. */
    private fun colorFor(mOrd: Int, i: Int): Int {
        val mat = Material.values()[mOrd]
        return when (mat) {
            Material.EMPTY -> 0xFF101018.toInt()          // background = very dark
            Material.SAND -> jitter(0xFFE0C068.toInt(), i, 14)
            Material.WATER -> jitter(0xFF3A80E0.toInt(), i, 18)
            Material.STONE -> jitter(0xFF777777.toInt(), i, 16)
            Material.WOOD -> woodColor(i)
            Material.FIRE -> fireColor(i, tick)
            Material.SMOKE -> jitter(0xFF303030.toInt(), i, 18)
            Material.OIL -> jitter(0xFF221810.toInt(), i, 10)
            Material.ACID -> jitter(0xFF7CFC00.toInt(), i, 20)
            Material.LAVA -> lavaColor(i, tick)
            Material.ICE -> jitter(0xFFB0E0F0.toInt(), i, 16)
            Material.STEAM -> jitter(0xFFC8D8E8.toInt(), i, 14)
            Material.PLANT -> jitter(0xFF2FA03A.toInt(), i, 18)
            Material.GUNPOWDER -> jitter(0xFF2A2A2A.toInt(), i, 10)
            Material.SUGAR -> jitter(0xFFF0F0F0.toInt(), i, 12)
            Material.ANT -> 0xFF1A0B04.toInt()
            Material.ERASE -> 0xFF101018.toInt()
        }
    }

    private fun jitter(base: Int, i: Int, amount: Int): Int {
        // cheap hash of cell index -> noise in [-amount/2, amount/2]
        val h = (i * 2654435761u.toInt()) ushr 16
        val n = (h and 0xFF) % amount - amount / 2
        val r = ((base ushr 16) and 0xFF) + n
        val g = ((base ushr 8) and 0xFF) + n
        val b = (base and 0xFF) + n
        return (0xFF shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)
    }

    private fun woodColor(i: Int): Int {
        // Horizontal grain stripes
        val y = i / cols
        val stripe = (y * 13 + (i * 29)) and 31
        val dark = stripe < 6
        return if (dark) jitter(0xFF5A3212.toInt(), i, 10)
        else jitter(0xFF8A5826.toInt(), i, 10)
    }

    private fun fireColor(i: Int, tick: Int): Int {
        val h = ((i * 2654435761u.toInt()) ushr 20) + tick
        val phase = (h and 3)
        return when (phase) {
            0 -> 0xFFFFF060.toInt()
            1 -> 0xFFFFB020.toInt()
            2 -> 0xFFFF5020.toInt()
            else -> 0xFFD02010.toInt()
        }
    }

    private fun lavaColor(i: Int, tick: Int): Int {
        val h = ((i * 2654435761u.toInt()) ushr 18) + tick / 2
        val phase = (h and 3)
        return when (phase) {
            0 -> 0xFFFFA020.toInt()
            1 -> 0xFFFF5810.toInt()
            2 -> 0xFFE03000.toInt()
            else -> 0xFF802008.toInt()
        }
    }

    private fun drawFrame() {
        val h = holder
        val canvas: Canvas = h.lockCanvas() ?: return
        try {
            bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
            canvas.drawColor(Color.BLACK)
            destRect.set(0f, 0f, (cols * cellSize).toFloat(), (rows * cellSize).toFloat())
            canvas.drawBitmap(bitmap, null, destRect, paint)
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }
}
