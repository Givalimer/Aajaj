package com.givalimer.aajaj

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

/**
 * Falling-sand style sandbox.
 *
 * The world is a 2D grid of Materials. Each frame we run a simple cellular-automata
 * step: sand falls down (and diagonally), water flows down and sideways, stone stays put.
 * Touch input paints cells with the currently selected material.
 */
class SandboxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback, Runnable {

    @Volatile var currentMaterial: Material = Material.SAND

    private val cellSize = 8          // pixel size of one cell on screen
    private val brushRadius = 3       // cells painted around touch point

    private var cols = 0
    private var rows = 0
    private lateinit var grid: Array<IntArray>      // stores Material ordinals
    private lateinit var bitmap: Bitmap             // render target
    private val paint = Paint()

    private var thread: Thread? = null
    @Volatile private var running = false

    private var lastTouchX = -1f
    private var lastTouchY = -1f
    private var touching = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        cols = (width / cellSize).coerceAtLeast(1)
        rows = (height / cellSize).coerceAtLeast(1)
        grid = Array(rows) { IntArray(cols) }
        bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        resumeLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        pauseLoop()
    }

    fun resumeLoop() {
        if (running) return
        running = true
        thread = Thread(this, "SandboxLoop").also { it.start() }
    }

    fun pauseLoop() {
        running = false
        thread?.join(200)
        thread = null
    }

    fun clearGrid() {
        if (!::grid.isInitialized) return
        for (y in 0 until rows) {
            for (x in 0 until cols) grid[y][x] = Material.EMPTY.ordinal
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touching = true
                paintAt(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                lastTouchX = -1f
                lastTouchY = -1f
            }
        }
        return true
    }

    private fun paintAt(px: Float, py: Float) {
        if (!::grid.isInitialized) return
        val cx = (px / cellSize).toInt()
        val cy = (py / cellSize).toInt()
        val r = brushRadius
        val mat = currentMaterial.ordinal
        for (dy in -r..r) {
            for (dx in -r..r) {
                if (dx * dx + dy * dy > r * r) continue
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until cols && y in 0 until rows) {
                    grid[y][x] = mat
                }
            }
        }
    }

    override fun run() {
        val frameMs = 16L // ~60 FPS target
        while (running) {
            val start = System.currentTimeMillis()
            step()
            draw()
            val elapsed = System.currentTimeMillis() - start
            val sleep = frameMs - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    /** One tick of the simulation. Iterate bottom-up so falling pixels don't get
     *  processed twice in the same frame. */
    private fun step() {
        if (!::grid.isInitialized) return
        val sand = Material.SAND.ordinal
        val water = Material.WATER.ordinal
        val empty = Material.EMPTY.ordinal

        for (y in rows - 2 downTo 0) {
            // alternate sweep direction per row to avoid bias
            val leftToRight = Random.nextBoolean()
            val range = if (leftToRight) 0 until cols else cols - 1 downTo 0
            for (x in range) {
                val cell = grid[y][x]
                if (cell == sand) {
                    // sand: try straight down, then diagonals. Can also sink through water.
                    val below = grid[y + 1][x]
                    if (below == empty || below == water) {
                        grid[y + 1][x] = sand
                        grid[y][x] = below
                    } else {
                        val dir = if (Random.nextBoolean()) 1 else -1
                        if (trySwap(x, y, x + dir, y + 1, sand, listOf(empty, water))) {
                            // moved
                        } else {
                            trySwap(x, y, x - dir, y + 1, sand, listOf(empty, water))
                        }
                    }
                } else if (cell == water) {
                    // water: down, then diagonals-down, then sideways
                    if (grid[y + 1][x] == empty) {
                        grid[y + 1][x] = water
                        grid[y][x] = empty
                    } else {
                        val dir = if (Random.nextBoolean()) 1 else -1
                        if (!trySwap(x, y, x + dir, y + 1, water, listOf(empty))) {
                            if (!trySwap(x, y, x - dir, y + 1, water, listOf(empty))) {
                                if (!trySwap(x, y, x + dir, y, water, listOf(empty))) {
                                    trySwap(x, y, x - dir, y, water, listOf(empty))
                                }
                            }
                        }
                    }
                }
                // stone and empty: do nothing
            }
        }
    }

    private fun trySwap(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        movingMat: Int,
        allowedTargets: List<Int>
    ): Boolean {
        if (toX !in 0 until cols || toY !in 0 until rows) return false
        val target = grid[toY][toX]
        if (target in allowedTargets) {
            grid[toY][toX] = movingMat
            grid[fromY][fromX] = target
            return true
        }
        return false
    }

    private fun draw() {
        if (!::grid.isInitialized) return
        val holder = holder
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            // Paint bitmap from grid
            for (y in 0 until rows) {
                val row = grid[y]
                for (x in 0 until cols) {
                    val m = row[x]
                    val color = when (m) {
                        Material.SAND.ordinal -> Material.SAND.color
                        Material.WATER.ordinal -> Material.WATER.color
                        Material.STONE.ordinal -> Material.STONE.color
                        else -> Color.BLACK
                    }
                    bitmap.setPixel(x, y, color)
                }
            }
            canvas.drawColor(Color.BLACK)
            val dst = Rect(0, 0, cols * cellSize, rows * cellSize)
            canvas.drawBitmap(bitmap, null, dst, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
