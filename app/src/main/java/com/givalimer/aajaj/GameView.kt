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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * First-person "Baldi-like" game.
 *
 * Rendering: raycasting onto a SurfaceView in vertical strips.
 * Input: two virtual joysticks - left for movement, right for camera rotation.
 * Logic: collect notebooks (each opens a math problem). Teacher hunts you via BFS.
 * Wrong answer -> teacher speeds up. Collect all notebooks then reach the exit to win.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback, Runnable {

    interface Listener {
        fun onShowMathProblem(problem: MathProblem, onResult: (Boolean) -> Unit)
        fun onWin()
        fun onLose()
        fun onHudUpdate(collected: Int, total: Int)
    }

    var listener: Listener? = null

    // World & entities
    private val world = World()
    private var playerX = world.playerStart.first + 0.5
    private var playerY = world.playerStart.second + 0.5
    private var playerAngle = 0.0
    private val teacher = Teacher(
        world.teacherStart.first + 0.5,
        world.teacherStart.second + 0.5,
        world
    )

    private var collected = 0
    private var totalNotebooks = world.notebookPositions.size
    private var gameEnded = false
    @Volatile private var paused = false
    private var waitingForAnswer = false

    // Raycasting
    private val fov = Math.toRadians(66.0)
    private val colStep = 2 // render every N pixels wide to keep perf high
    private val paint = Paint()

    // Input - joysticks
    private var leftPointerId = -1
    private var rightPointerId = -1
    private var leftOrigin = Pair(0f, 0f)
    private var leftOffset = Pair(0f, 0f)
    private var rightOrigin = Pair(0f, 0f)
    private var rightOffset = Pair(0f, 0f)
    private val stickRadius = 120f
    private val stickVisualRadius = 180f

    private var thread: Thread? = null
    @Volatile private var running = false

    init {
        holder.addCallback(this)
        isFocusable = true
        paint.isAntiAlias = false
    }

    override fun surfaceCreated(h: SurfaceHolder) { resumeLoop() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hg: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { pauseLoop() }

    fun resumeLoop() {
        if (running) return
        running = true
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    fun pauseLoop() {
        running = false
        thread?.join(250)
        thread = null
    }

    fun setPaused(p: Boolean) { paused = p }

    fun setWaitingForAnswer(w: Boolean) { waitingForAnswer = w }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val id = event.getPointerId(idx)
        val midX = width / 2f

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(idx); val y = event.getY(idx)
                if (x < midX && leftPointerId == -1) {
                    leftPointerId = id
                    leftOrigin = Pair(x, y)
                    leftOffset = Pair(0f, 0f)
                } else if (x >= midX && rightPointerId == -1) {
                    rightPointerId = id
                    rightOrigin = Pair(x, y)
                    rightOffset = Pair(0f, 0f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i); val py = event.getY(i)
                    if (pid == leftPointerId) {
                        leftOffset = clampStick(px - leftOrigin.first, py - leftOrigin.second)
                    } else if (pid == rightPointerId) {
                        rightOffset = clampStick(px - rightOrigin.first, py - rightOrigin.second)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (id == leftPointerId) {
                    leftPointerId = -1; leftOffset = Pair(0f, 0f)
                }
                if (id == rightPointerId) {
                    rightPointerId = -1; rightOffset = Pair(0f, 0f)
                }
            }
        }
        return true
    }

    private fun clampStick(dx: Float, dy: Float): Pair<Float, Float> {
        val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d <= stickRadius) return Pair(dx, dy)
        val s = stickRadius / d
        return Pair(dx * s, dy * s)
    }

    override fun run() {
        var last = System.nanoTime()
        val targetMs = 16L
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000.0).coerceAtMost(0.05)
            last = now
            if (!paused && !waitingForAnswer && !gameEnded) {
                update(dt)
            }
            draw()
            val elapsed = (System.nanoTime() - now) / 1_000_000L
            val sleep = targetMs - elapsed
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    private fun update(dt: Double) {
        // Movement from left stick: forward/back = -dy, strafe = dx
        val moveSpeed = 2.6 // cells/sec
        val rotSpeed = 2.4  // rad/sec
        val fwd = -leftOffset.second / stickRadius
        val strafe = leftOffset.first / stickRadius
        val rot = rightOffset.first / stickRadius

        playerAngle += rot * rotSpeed * dt

        val dx = (cos(playerAngle) * fwd + cos(playerAngle + Math.PI / 2) * strafe) * moveSpeed * dt
        val dy = (sin(playerAngle) * fwd + sin(playerAngle + Math.PI / 2) * strafe) * moveSpeed * dt
        tryMove(dx, dy)

        // Pickups
        val cx = playerX.toInt(); val cy = playerY.toInt()
        val cell = world.get(cx, cy)
        if (cell == World.NOTEBOOK && !waitingForAnswer) {
            world.set(cx, cy, World.FLOOR)
            waitingForAnswer = true
            val problem = MathProblem.random(difficulty = collected / 2 + 1)
            listener?.onShowMathProblem(problem) { correct ->
                waitingForAnswer = false
                if (correct) {
                    collected++
                    listener?.onHudUpdate(collected, totalNotebooks)
                } else {
                    // wrong answer - teacher gets faster
                    teacher.speed += 0.6
                }
            }
        } else if (cell == World.EXIT && collected >= totalNotebooks && !gameEnded) {
            gameEnded = true
            post { listener?.onWin() }
        }

        // Teacher
        teacher.update(dt, playerX, playerY)
        val d = hypot(teacher.x - playerX, teacher.y - playerY)
        if (d < 0.55 && !gameEnded) {
            gameEnded = true
            post { listener?.onLose() }
        }
    }

    private fun tryMove(dx: Double, dy: Double) {
        val r = 0.18
        val nx = playerX + dx
        if (!world.isBlocking((nx + if (dx > 0) r else -r).toInt(), playerY.toInt())) {
            playerX = nx
        }
        val ny = playerY + dy
        if (!world.isBlocking(playerX.toInt(), (ny + if (dy > 0) r else -r).toInt())) {
            playerY = ny
        }
    }

    private fun draw() {
        val h = holder
        val canvas: Canvas = h.lockCanvas() ?: return
        try {
            val w = width; val hgt = height
            if (w <= 0 || hgt <= 0) return

            // Sky + floor
            paint.color = Color.rgb(40, 50, 75)
            canvas.drawRect(0f, 0f, w.toFloat(), hgt / 2f, paint)
            paint.color = Color.rgb(50, 40, 35)
            canvas.drawRect(0f, hgt / 2f, w.toFloat(), hgt.toFloat(), paint)

            // Raycast walls
            val halfH = hgt / 2f
            val numRays = w / colStep
            val depthBuf = DoubleArray(numRays)

            for (i in 0 until numRays) {
                val cameraX = 2.0 * i / numRays - 1.0
                val rayAngle = playerAngle + Math.atan(cameraX * Math.tan(fov / 2))
                val rayDX = cos(rayAngle)
                val rayDY = sin(rayAngle)

                val hit = castRay(playerX, playerY, rayDX, rayDY)
                val perp = hit.dist * cos(rayAngle - playerAngle) // fix fisheye
                depthBuf[i] = perp
                val lineH = (hgt / perp).coerceAtLeast(1.0).toInt()
                val top = (halfH - lineH / 2).toInt()
                val bottom = top + lineH

                // Color by side + simple distance shading
                val base = if (hit.cellKind == World.EXIT) Color.rgb(100, 220, 120)
                else Color.rgb(200, 190, 170)
                val darkenX = if (hit.side == 1) 0.75 else 1.0
                val fog = (1.0 / (1.0 + perp * 0.18)).coerceIn(0.25, 1.0)
                val shade = (darkenX * fog).coerceIn(0.0, 1.0)
                val r = (Color.red(base) * shade).toInt()
                val g = (Color.green(base) * shade).toInt()
                val b = (Color.blue(base) * shade).toInt()
                paint.color = Color.rgb(r, g, b)
                val xPx = i * colStep
                canvas.drawRect(
                    xPx.toFloat(), top.toFloat(),
                    (xPx + colStep).toFloat(), bottom.toFloat(), paint
                )
            }

            // Draw teacher as a billboard sprite (simple colored rect with depth check)
            drawTeacherBillboard(canvas, depthBuf, numRays, w, hgt)

            // HUD: minimap + sticks + counters
            drawMiniMap(canvas)
            drawJoysticks(canvas)
            drawCounter(canvas)

            if (gameEnded) {
                paint.color = Color.argb(180, 0, 0, 0)
                canvas.drawRect(0f, 0f, w.toFloat(), hgt.toFloat(), paint)
            }
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }

    private data class RayHit(val dist: Double, val side: Int, val cellKind: Int)

    /** DDA raycasting. Returns distance and which side was hit (0 = x, 1 = y). */
    private fun castRay(ox: Double, oy: Double, dx: Double, dy: Double): RayHit {
        var mapX = ox.toInt()
        var mapY = oy.toInt()
        val deltaX = if (dx == 0.0) 1e30 else Math.abs(1.0 / dx)
        val deltaY = if (dy == 0.0) 1e30 else Math.abs(1.0 / dy)
        val stepX: Int; val stepY: Int
        var sideX: Double; var sideY: Double

        if (dx < 0) { stepX = -1; sideX = (ox - mapX) * deltaX }
        else { stepX = 1; sideX = (mapX + 1.0 - ox) * deltaX }
        if (dy < 0) { stepY = -1; sideY = (oy - mapY) * deltaY }
        else { stepY = 1; sideY = (mapY + 1.0 - oy) * deltaY }

        var side = 0
        var iter = 0
        while (iter < 128) {
            if (sideX < sideY) { sideX += deltaX; mapX += stepX; side = 0 }
            else { sideY += deltaY; mapY += stepY; side = 1 }
            val cell = world.get(mapX, mapY)
            if (cell == World.WALL || cell == World.EXIT) {
                val dist = if (side == 0) sideX - deltaX else sideY - deltaY
                return RayHit(dist.coerceAtLeast(0.0001), side, cell)
            }
            iter++
        }
        return RayHit(64.0, 0, World.WALL)
    }

    private fun drawTeacherBillboard(
        canvas: Canvas, depthBuf: DoubleArray, numRays: Int, w: Int, hgt: Int
    ) {
        val sx = teacher.x - playerX
        val sy = teacher.y - playerY
        // Transform into camera space
        val cosA = cos(-playerAngle); val sinA = sin(-playerAngle)
        val tx = sx * cosA - sy * sinA
        val ty = sx * sinA + sy * cosA
        if (tx <= 0.05) return // behind
        val halfFovTan = Math.tan(fov / 2)
        val screenX = (w / 2.0) * (1.0 + (ty / tx) / halfFovTan)
        val size = (hgt / tx).coerceAtMost(hgt * 2.0)
        val top = (hgt / 2.0 - size / 2.0)
        val left = (screenX - size / 2.0)
        val right = (screenX + size / 2.0)
        val bottom = top + size

        // Depth test per column using depthBuf
        var drawnAny = false
        val colStart = left.toInt().coerceAtLeast(0)
        val colEnd = right.toInt().coerceAtMost(w - 1)
        paint.color = Color.rgb(30, 120, 40) // green suit
        for (x in colStart..colEnd step 2) {
            val bufIdx = (x / colStep).coerceIn(0, numRays - 1)
            if (tx < depthBuf[bufIdx]) {
                canvas.drawRect(
                    x.toFloat(), top.toFloat(),
                    (x + 2).toFloat(), bottom.toFloat(), paint
                )
                drawnAny = true
            }
        }
        if (drawnAny) {
            // "head" as a circle
            paint.color = Color.rgb(240, 210, 170)
            val headR = (size * 0.18f).toFloat()
            canvas.drawCircle(screenX.toFloat(), (top + headR).toFloat(), headR, paint)
            // "eyes"
            paint.color = Color.BLACK
            canvas.drawCircle(
                (screenX - headR * 0.4f).toFloat(),
                (top + headR).toFloat(),
                headR * 0.2f, paint
            )
            canvas.drawCircle(
                (screenX + headR * 0.4f).toFloat(),
                (top + headR).toFloat(),
                headR * 0.2f, paint
            )
        }
    }

    private fun drawMiniMap(canvas: Canvas) {
        val cell = 6f
        val ox = 16f; val oy = 16f
        paint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(
            ox - 4f, oy - 4f,
            ox + world.width * cell + 4f, oy + world.height * cell + 4f, paint
        )
        for (y in 0 until world.height) {
            for (x in 0 until world.width) {
                val c = world.get(x, y)
                paint.color = when (c) {
                    World.WALL -> Color.rgb(90, 90, 90)
                    World.NOTEBOOK -> Color.rgb(220, 200, 60)
                    World.EXIT -> Color.rgb(60, 220, 100)
                    else -> Color.rgb(30, 30, 40)
                }
                canvas.drawRect(
                    ox + x * cell, oy + y * cell,
                    ox + (x + 1) * cell, oy + (y + 1) * cell, paint
                )
            }
        }
        // teacher
        paint.color = Color.rgb(220, 60, 60)
        canvas.drawCircle(
            ox + (teacher.x * cell).toFloat(),
            oy + (teacher.y * cell).toFloat(),
            cell * 0.6f, paint
        )
        // player
        paint.color = Color.rgb(80, 200, 255)
        canvas.drawCircle(
            ox + (playerX * cell).toFloat(),
            oy + (playerY * cell).toFloat(),
            cell * 0.5f, paint
        )
    }

    private fun drawJoysticks(canvas: Canvas) {
        paint.color = Color.argb(80, 255, 255, 255)
        if (leftPointerId != -1) {
            canvas.drawCircle(leftOrigin.first, leftOrigin.second, stickVisualRadius, paint)
            paint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(
                leftOrigin.first + leftOffset.first,
                leftOrigin.second + leftOffset.second,
                stickVisualRadius * 0.45f, paint
            )
        }
        paint.color = Color.argb(80, 255, 255, 255)
        if (rightPointerId != -1) {
            canvas.drawCircle(rightOrigin.first, rightOrigin.second, stickVisualRadius, paint)
            paint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(
                rightOrigin.first + rightOffset.first,
                rightOrigin.second + rightOffset.second,
                stickVisualRadius * 0.45f, paint
            )
        }
    }

    private fun drawCounter(canvas: Canvas) {
        paint.color = Color.argb(160, 0, 0, 0)
        val r = RectF(width - 230f, 16f, width - 16f, 72f)
        canvas.drawRect(r, paint)
        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText(
            "Тетради: $collected / $totalNotebooks",
            width - 218f, 54f, paint
        )
    }
}
