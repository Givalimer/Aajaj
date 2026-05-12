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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * First-person Baldi-like game with a proper textured raycaster.
 *
 * Rendering pipeline per frame:
 *   1. Fill a low-resolution pixel buffer (ARGB ints).
 *   2. Fill sky and floor as vertical gradients.
 *   3. Raycast walls using DDA. For each screen column, sample a 64x64 wall
 *      texture at the exact hit position and write column pixels with distance
 *      shading. Store per-column depth for sprite z-testing.
 *   4. Draw sprites (notebooks and teacher) as depth-tested billboards, pixel
 *      by pixel with alpha test.
 *   5. Blit the buffer to the SurfaceView at full screen size, scaled by Canvas.
 *   6. Overlay HUD (minimap, joysticks, counter) with Paint on the Canvas.
 *
 * Rendering at a low internal resolution and scaling up is what keeps this
 * smooth on a phone - a 240px-wide buffer is still ~120k pixels per frame.
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

    private val fov = Math.toRadians(66.0)
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    // Low-res render target. Gets (re)allocated when the surface size is known.
    private var rtWidth = 0
    private var rtHeight = 0
    private val rtScale = 3            // 3x downscale - sweet spot for perf vs quality
    private lateinit var pixels: IntArray
    private lateinit var zBuffer: DoubleArray
    private lateinit var rtBitmap: Bitmap
    private val destRect = RectF()

    // Input - two virtual joysticks
    private var leftPointerId = -1
    private var rightPointerId = -1
    private var leftOrigin = Pair(0f, 0f)
    private var leftOffset = Pair(0f, 0f)
    private var rightOrigin = Pair(0f, 0f)
    private var rightOffset = Pair(0f, 0f)
    private val stickRadius = 120f
    private val stickVisualRadius = 180f

    // Head bob while moving - purely visual
    private var bobPhase = 0.0
    private var bobOffsetPx = 0

    private var thread: Thread? = null
    @Volatile private var running = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        ensureRenderTarget()
        resumeLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hg: Int) {
        ensureRenderTarget()
    }

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

    private fun ensureRenderTarget() {
        val w = (width / rtScale).coerceAtLeast(1)
        val h = (height / rtScale).coerceAtLeast(1)
        if (w == rtWidth && h == rtHeight && ::rtBitmap.isInitialized) return
        rtWidth = w; rtHeight = h
        pixels = IntArray(w * h)
        zBuffer = DoubleArray(w)
        rtBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

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
                if (id == leftPointerId) { leftPointerId = -1; leftOffset = Pair(0f, 0f) }
                if (id == rightPointerId) { rightPointerId = -1; rightOffset = Pair(0f, 0f) }
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
        val moveSpeed = 2.6
        val rotSpeed = 2.4
        val fwd = -leftOffset.second / stickRadius
        val strafe = leftOffset.first / stickRadius
        val rot = rightOffset.first / stickRadius
        playerAngle += rot * rotSpeed * dt

        val dx = (cos(playerAngle) * fwd + cos(playerAngle + Math.PI / 2) * strafe) * moveSpeed * dt
        val dy = (sin(playerAngle) * fwd + sin(playerAngle + Math.PI / 2) * strafe) * moveSpeed * dt
        tryMove(dx, dy)

        // Head bob while moving
        val moving = abs(fwd) + abs(strafe)
        if (moving > 0.05) {
            bobPhase += dt * 10.0 * moving
            bobOffsetPx = (sin(bobPhase) * 3).toInt()
        } else {
            bobOffsetPx = (bobOffsetPx * 0.7).toInt()
        }

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
                    teacher.speed += 0.6
                }
            }
        } else if (cell == World.EXIT && collected >= totalNotebooks && !gameEnded) {
            gameEnded = true
            post { listener?.onWin() }
        }

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

    // ---------- Rendering ----------

    private fun draw() {
        val h = holder
        val canvas: Canvas = h.lockCanvas() ?: return
        try {
            val w = width; val hgt = height
            if (w <= 0 || hgt <= 0) return
            ensureRenderTarget()

            renderScene()

            rtBitmap.setPixels(pixels, 0, rtWidth, 0, 0, rtWidth, rtHeight)
            destRect.set(0f, 0f, w.toFloat(), hgt.toFloat())
            canvas.drawBitmap(rtBitmap, null, destRect, paint)

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

    private fun renderScene() {
        val w = rtWidth; val h = rtHeight
        val horizon = h / 2 + bobOffsetPx

        // Sky & floor gradients
        for (y in 0 until h) {
            val color = if (y < horizon) {
                // sky: dark navy -> purple-gray
                val t = y.toDouble() / horizon
                val r = (20 + 30 * t).toInt()
                val g = (25 + 35 * t).toInt()
                val b = (50 + 50 * t).toInt()
                rgb(r, g, b)
            } else {
                // floor: warm brown -> darker
                val t = (y - horizon).toDouble() / (h - horizon).coerceAtLeast(1)
                val r = (90 - 50 * t).toInt()
                val g = (65 - 35 * t).toInt()
                val b = (50 - 30 * t).toInt()
                rgb(r, g, b)
            }
            val rowStart = y * w
            for (x in 0 until w) pixels[rowStart + x] = color
        }

        // Raycast textured walls
        val halfFovTan = Math.tan(fov / 2)
        for (x in 0 until w) {
            val cameraX = 2.0 * x / w - 1.0
            val rayAngle = playerAngle + Math.atan(cameraX * halfFovTan)
            val rayDX = cos(rayAngle)
            val rayDY = sin(rayAngle)

            val hit = castRay(playerX, playerY, rayDX, rayDY)
            val perp = hit.dist * cos(rayAngle - playerAngle)
            zBuffer[x] = perp

            // Line height on the render target, centered around the bobbed horizon
            val lineH = (h / perp).coerceAtLeast(1.0)
            val topF = horizon - lineH / 2
            val bottomF = horizon + lineH / 2
            val top = topF.toInt().coerceAtLeast(0)
            val bottom = bottomF.toInt().coerceAtMost(h - 1)

            // Texture selection and U coordinate
            val tex = if (hit.cellKind == World.EXIT) Textures.exitWall else Textures.wall
            val size = Textures.SIZE
            var wallX: Double = if (hit.side == 0) playerY + hit.dist * rayDY
                                else playerX + hit.dist * rayDX
            wallX -= Math.floor(wallX)
            var texU = (wallX * size).toInt()
            if ((hit.side == 0 && rayDX > 0) || (hit.side == 1 && rayDY < 0)) {
                texU = size - texU - 1
            }
            texU = texU.coerceIn(0, size - 1)

            // Fog/distance shading multiplier
            val fog = (1.0 / (1.0 + perp * 0.22)).coerceIn(0.18, 1.0)
            val sideShade = if (hit.side == 1) 0.8 else 1.0
            val shade = (fog * sideShade).coerceIn(0.0, 1.0)

            // Texture V stepping
            val step = size.toDouble() / lineH
            var texPos = (top - topF) * step
            for (y in top..bottom) {
                val texV = texPos.toInt().coerceIn(0, size - 1)
                texPos += step
                val c = tex[texV * size + texU]
                pixels[y * w + x] = shadeColor(c, shade)
            }
        }

        // Notebooks in world as billboard sprites
        for (np in world.notebookPositions) {
            if (world.get(np.first, np.second) != World.NOTEBOOK) continue
            drawSprite(
                spriteX = np.first + 0.5,
                spriteY = np.second + 0.5,
                tex = Textures.notebook,
                vOffset = 0.3,          // hovers slightly above floor
                scale = 0.55
            )
        }

        // Teacher sprite
        drawSprite(
            spriteX = teacher.x,
            spriteY = teacher.y,
            tex = Textures.teacher,
            vOffset = 0.0,
            scale = 1.0
        )
    }

    private data class RayHit(val dist: Double, val side: Int, val cellKind: Int)

    /** DDA raycast. `side` is 0 for vertical grid-line hits, 1 for horizontal. */
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

    /**
     * Draws a textured billboard sprite with alpha test (zero alpha = skip),
     * depth tested per column against the wall z-buffer.
     *
     * vOffset shifts the sprite vertically in world space (0 = centered on horizon,
     * positive = drawn lower/on the floor).
     */
    private fun drawSprite(
        spriteX: Double,
        spriteY: Double,
        tex: IntArray,
        vOffset: Double,
        scale: Double
    ) {
        val w = rtWidth; val h = rtHeight
        val dx = spriteX - playerX
        val dy = spriteY - playerY
        // Rotate into camera space. tx = forward distance, ty = side distance.
        val cosA = cos(-playerAngle); val sinA = sin(-playerAngle)
        val tx = dx * cosA - dy * sinA
        val ty = dx * sinA + dy * cosA
        if (tx <= 0.1) return

        val halfFovTan = Math.tan(fov / 2)
        val spriteScreenX = (w / 2.0) * (1.0 + (ty / tx) / halfFovTan)
        val spriteSize = (h / tx * scale)
        val horizon = h / 2 + bobOffsetPx
        val vShift = (vOffset * h / tx).toInt()
        val topF = horizon - spriteSize / 2 + vShift
        val bottomF = horizon + spriteSize / 2 + vShift
        val leftF = spriteScreenX - spriteSize / 2
        val rightF = spriteScreenX + spriteSize / 2

        val top = topF.toInt().coerceAtLeast(0)
        val bottom = bottomF.toInt().coerceAtMost(h - 1)
        val left = leftF.toInt().coerceAtLeast(0)
        val right = rightF.toInt().coerceAtMost(w - 1)
        if (top > bottom || left > right) return

        val size = Textures.SIZE
        val fog = (1.0 / (1.0 + tx * 0.22)).coerceIn(0.3, 1.0)

        for (x in left..right) {
            if (tx >= zBuffer[x]) continue // behind a wall
            val texU = ((x - leftF) / (rightF - leftF) * size).toInt().coerceIn(0, size - 1)
            for (y in top..bottom) {
                val texV = ((y - topF) / (bottomF - topF) * size).toInt().coerceIn(0, size - 1)
                val c = tex[texV * size + texU]
                if ((c ushr 24) == 0) continue
                pixels[y * w + x] = shadeColor(c, fog)
            }
        }
    }

    // ---------- HUD ----------

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
        paint.color = Color.rgb(220, 60, 60)
        canvas.drawCircle(
            ox + (teacher.x * cell).toFloat(),
            oy + (teacher.y * cell).toFloat(),
            cell * 0.6f, paint
        )
        paint.color = Color.rgb(80, 200, 255)
        canvas.drawCircle(
            ox + (playerX * cell).toFloat(),
            oy + (playerY * cell).toFloat(),
            cell * 0.5f, paint
        )
        // facing indicator
        paint.color = Color.rgb(140, 230, 255)
        val fx = ox + ((playerX + cos(playerAngle) * 0.8) * cell).toFloat()
        val fy = oy + ((playerY + sin(playerAngle) * 0.8) * cell).toFloat()
        canvas.drawCircle(fx, fy, cell * 0.25f, paint)
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

    // ---------- color helpers ----------

    private fun rgb(r: Int, g: Int, b: Int): Int {
        val rr = r.coerceIn(0, 255); val gg = g.coerceIn(0, 255); val bb = b.coerceIn(0, 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun shadeColor(c: Int, shade: Double): Int {
        val a = (c ushr 24) and 0xFF
        val r = ((c ushr 16) and 0xFF) * shade
        val g = ((c ushr 8) and 0xFF) * shade
        val b = (c and 0xFF) * shade
        return (a shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
}
