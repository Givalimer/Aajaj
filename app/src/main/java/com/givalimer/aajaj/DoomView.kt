package com.givalimer.aajaj

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Мини-Doom. Классический raycasting-рендер поверх [SurfaceView]:
 *  - мир это сетка [map] (1 = стена, 0 = пусто);
 *  - игрок описан вектором направления ([dirX], [dirY]) и плоскостью камеры;
 *  - для каждого столбца экрана кидаем DDA-луч, рисуем вертикальную полосу стены,
 *    пол/потолок и спрайты врагов с учётом z-буфера.
 *  - выстрел это hit-scan: ищем ближайшего врага на направлении взгляда, если ближе,
 *    чем стена.
 */
class DoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback, Runnable {

    // ---------------- Карта ----------------
    private val mapRows = arrayOf(
        "1111111111111111",
        "1..............1",
        "1..1111...1....1",
        "1......1..1....1",
        "1..1...1..1....1",
        "1..1...1111....1",
        "1..1...........1",
        "1..111.........1",
        "1......11111...1",
        "1......1...1...1",
        "1..........1...1",
        "1......11111...1",
        "1..............1",
        "1...........11.1",
        "1..............1",
        "1111111111111111"
    )
    private val map: Array<IntArray> = Array(mapRows.size) { r ->
        IntArray(mapRows[r].length) { c -> if (mapRows[r][c] == '1') 1 else 0 }
    }
    private val mapH = map.size
    private val mapW = map[0].size

    // ---------------- Игрок ----------------
    private var posX = 2.5
    private var posY = 2.5
    private var dirX = 1.0
    private var dirY = 0.0
    private var planeX = 0.0
    private var planeY = 0.66

    private var hp = 100
    private var ammo = 40
    private var kills = 0
    private var damageFlash = 0
    private var deadFrames = 0

    // ---------------- Враги ----------------
    private class Enemy(var x: Double, var y: Double) {
        var hp = 30
        var alive = true
        var hitFlash = 0
        var attackCooldown = 0
    }

    private val enemySpawns = listOf(
        6.5 to 3.5,
        12.5 to 5.5,
        4.5 to 10.5,
        13.5 to 12.5,
        8.5 to 9.5,
        11.5 to 2.5
    )
    private val enemies: MutableList<Enemy> =
        enemySpawns.map { Enemy(it.first, it.second) }.toMutableList()

    // ---------------- Рендер ----------------
    private val renderW = 320
    private val renderH = 180
    private val pixels = IntArray(renderW * renderH)
    private val renderBitmap: Bitmap =
        Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
    private val zBuffer = DoubleArray(renderW)
    private val enemySprite: Bitmap
    private val enemySpritePixels: IntArray
    private val spriteW: Int
    private val spriteH: Int

    private val bitmapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val hudPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hudShadowPaint = Paint().apply {
        color = Color.BLACK
        textSize = 42f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val overlayPaint = Paint()
    private val uiPaint = Paint().apply { isAntiAlias = true }

    private var screenW = 0
    private var screenH = 0

    private var thread: Thread? = null
    @Volatile private var running = false

    // ---------------- Ввод ----------------
    private var leftPointerId = -1
    private var leftStartX = 0f
    private var leftStartY = 0f
    private var leftCurX = 0f
    private var leftCurY = 0f

    private var rightPointerId = -1
    private var rightLastX = 0f

    private var firePointerId = -1
    private var fireCooldown = 0
    private var muzzleFrames = 0

    init {
        holder.addCallback(this)
        isFocusable = true
        enemySprite = buildEnemySprite()
        spriteW = enemySprite.width
        spriteH = enemySprite.height
        enemySpritePixels = IntArray(spriteW * spriteH).also {
            enemySprite.getPixels(it, 0, spriteW, 0, 0, spriteW, spriteH)
        }
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        screenW = width
        screenH = height
        resumeLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        screenW = w
        screenH = hh
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        pauseLoop()
    }

    fun resumeLoop() {
        if (running) return
        running = true
        thread = Thread(this, "DoomLoop").also { it.start() }
    }

    fun pauseLoop() {
        running = false
        thread?.join(200)
        thread = null
    }

    // ---------------- Touch ----------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                assignPointer(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until event.pointerCount) {
                    val pid = event.getPointerId(p)
                    val px = event.getX(p)
                    val py = event.getY(p)
                    when (pid) {
                        leftPointerId -> {
                            leftCurX = px; leftCurY = py
                        }
                        rightPointerId -> {
                            val dx = px - rightLastX
                            rotate(-dx * 0.004)
                            rightLastX = px
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                when (id) {
                    leftPointerId -> leftPointerId = -1
                    rightPointerId -> rightPointerId = -1
                    firePointerId -> firePointerId = -1
                }
            }
        }
        return true
    }

    private fun assignPointer(id: Int, x: Float, y: Float) {
        val fbx = screenW - fireBtnMargin()
        val fby = screenH - fireBtnMargin()
        if (hypot((x - fbx).toDouble(), (y - fby).toDouble()) < fireBtnRadius()) {
            if (firePointerId == -1) {
                firePointerId = id
                tryFire()
            }
            return
        }
        if (x < screenW / 2f) {
            if (leftPointerId == -1) {
                leftPointerId = id
                leftStartX = x; leftStartY = y
                leftCurX = x; leftCurY = y
            }
        } else {
            if (rightPointerId == -1) {
                rightPointerId = id
                rightLastX = x
            }
        }
    }

    private fun fireBtnRadius(): Float = min(screenW, screenH) * 0.12f
    private fun fireBtnMargin(): Float = fireBtnRadius() * 1.4f
    private fun joystickRadius(): Float = min(screenW, screenH) * 0.13f

    private fun rotate(a: Double) {
        val cs = cos(a); val sn = sin(a)
        val ndx = dirX * cs - dirY * sn
        val ndy = dirX * sn + dirY * cs
        dirX = ndx; dirY = ndy
        val npx = planeX * cs - planeY * sn
        val npy = planeX * sn + planeY * cs
        planeX = npx; planeY = npy
    }

    private fun tryFire() {
        if (deadFrames > 0) return
        if (fireCooldown > 0 || ammo <= 0) return
        ammo--
        fireCooldown = 14
        muzzleFrames = 4
        val wallDist = rayWallDist(posX, posY, dirX, dirY)
        var bestDist = wallDist
        var bestEnemy: Enemy? = null
        for (e in enemies) {
            if (!e.alive) continue
            val t = rayCircleHit(posX, posY, dirX, dirY, e.x, e.y, 0.35) ?: continue
            if (t < bestDist) {
                bestDist = t; bestEnemy = e
            }
        }
        bestEnemy?.let {
            it.hp -= 18
            it.hitFlash = 6
            if (it.hp <= 0) {
                it.alive = false
                kills++
            }
        }
    }

    private fun rayCircleHit(
        sx: Double, sy: Double, dx: Double, dy: Double,
        cx: Double, cy: Double, r: Double
    ): Double? {
        val ox = sx - cx; val oy = sy - cy
        val a = dx * dx + dy * dy
        val b = 2.0 * (ox * dx + oy * dy)
        val c = ox * ox + oy * oy - r * r
        val disc = b * b - 4 * a * c
        if (disc < 0) return null
        val sq = sqrt(disc)
        val t1 = (-b - sq) / (2 * a)
        if (t1 > 0) return t1
        val t2 = (-b + sq) / (2 * a)
        if (t2 > 0) return t2
        return null
    }

    private fun rayWallDist(sx: Double, sy: Double, dx: Double, dy: Double): Double {
        var mx = sx.toInt(); var my = sy.toInt()
        val ddx = if (dx == 0.0) 1e30 else abs(1.0 / dx)
        val ddy = if (dy == 0.0) 1e30 else abs(1.0 / dy)
        val stepX: Int; val stepY: Int
        var sideX: Double; var sideY: Double
        if (dx < 0) { stepX = -1; sideX = (sx - mx) * ddx } else { stepX = 1; sideX = (mx + 1.0 - sx) * ddx }
        if (dy < 0) { stepY = -1; sideY = (sy - my) * ddy } else { stepY = 1; sideY = (my + 1.0 - sy) * ddy }
        var side = 0
        repeat(64) {
            if (sideX < sideY) { sideX += ddx; mx += stepX; side = 0 }
            else { sideY += ddy; my += stepY; side = 1 }
            if (mx < 0 || my < 0 || mx >= mapW || my >= mapH) return 20.0
            if (map[my][mx] != 0) {
                return if (side == 0) (mx - sx + (1 - stepX) / 2.0) / dx
                else (my - sy + (1 - stepY) / 2.0) / dy
            }
        }
        return 20.0
    }

    // ---------------- Game loop ----------------
    override fun run() {
        val target = 16L
        var last = System.currentTimeMillis()
        while (running) {
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000.0).coerceAtMost(0.05)
            last = now
            update(dt)
            renderFrame()
            val frameTime = System.currentTimeMillis() - now
            val sleep = target - frameTime
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    private fun update(dt: Double) {
        if (deadFrames > 0) {
            deadFrames--
            if (deadFrames == 0) respawn()
            return
        }

        if (leftPointerId != -1) {
            val dx = leftCurX - leftStartX
            val dy = leftCurY - leftStartY
            val max = joystickRadius()
            val nx = (dx / max).coerceIn(-1f, 1f).toDouble()
            val ny = (dy / max).coerceIn(-1f, 1f).toDouble()
            val speed = 3.2
            // forward/back = -ny (tilt up = forward), strafe right = +nx
            val moveDX = dirX * (-ny) + (-dirY) * nx
            val moveDY = dirY * (-ny) + (dirX) * nx
            val vx = moveDX * speed * dt
            val vy = moveDY * speed * dt
            tryMove(vx, vy)
        }

        if (fireCooldown > 0) fireCooldown--
        if (muzzleFrames > 0) muzzleFrames--
        if (damageFlash > 0) damageFlash--

        if (firePointerId != -1) tryFire()

        for (e in enemies) {
            if (!e.alive) continue
            if (e.hitFlash > 0) e.hitFlash--
            if (e.attackCooldown > 0) e.attackCooldown--
            val dxp = posX - e.x
            val dyp = posY - e.y
            val dist = hypot(dxp, dyp)
            if (dist < 9.0 && hasLineOfSight(e.x, e.y, posX, posY)) {
                val step = 1.3 * dt
                val ndx = dxp / dist * step
                val ndy = dyp / dist * step
                if (dist > 0.9) {
                    val nx = e.x + ndx
                    val ny = e.y + ndy
                    if (map[e.y.toInt()][nx.toInt()] == 0 && !enemyBlocked(nx, e.y, e)) e.x = nx
                    if (map[ny.toInt()][e.x.toInt()] == 0 && !enemyBlocked(e.x, ny, e)) e.y = ny
                } else if (e.attackCooldown == 0) {
                    hp -= 10
                    damageFlash = 8
                    e.attackCooldown = 40
                    if (hp <= 0) {
                        hp = 0
                        deadFrames = 80
                    }
                }
            }
        }
    }

    private fun enemyBlocked(nx: Double, ny: Double, self: Enemy): Boolean {
        for (o in enemies) {
            if (o === self || !o.alive) continue
            if (abs(o.x - nx) < 0.5 && abs(o.y - ny) < 0.5) return true
        }
        return false
    }

    private fun respawn() {
        posX = 2.5; posY = 2.5
        dirX = 1.0; dirY = 0.0
        planeX = 0.0; planeY = 0.66
        hp = 100; ammo = 40; kills = 0; damageFlash = 0
        for ((i, s) in enemySpawns.withIndex()) {
            val e = enemies[i]
            e.x = s.first; e.y = s.second
            e.hp = 30; e.alive = true; e.hitFlash = 0; e.attackCooldown = 0
        }
    }

    private fun hasLineOfSight(x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        val dx = x1 - x0; val dy = y1 - y0
        val len = hypot(dx, dy)
        val steps = (len * 8).toInt().coerceAtLeast(1)
        for (i in 1 until steps) {
            val t = i / steps.toDouble()
            val mx = (x0 + dx * t).toInt()
            val my = (y0 + dy * t).toInt()
            if (mx !in 0 until mapW || my !in 0 until mapH) return false
            if (map[my][mx] != 0) return false
        }
        return true
    }

    private fun tryMove(vx: Double, vy: Double) {
        val pad = 0.2
        val tx = posX + vx + sign(vx) * pad
        if (tx.toInt() in 0 until mapW && map[posY.toInt()][tx.toInt()] == 0) posX += vx
        val ty = posY + vy + sign(vy) * pad
        if (ty.toInt() in 0 until mapH && map[ty.toInt()][posX.toInt()] == 0) posY += vy
    }

    // ---------------- Render ----------------
    private fun renderFrame() {
        val canvas = holder.lockCanvas() ?: return
        try {
            renderCeilingFloor()
            renderWalls()
            renderEnemies()
            renderBitmap.setPixels(pixels, 0, renderW, 0, 0, renderW, renderH)
            canvas.drawColor(Color.BLACK)
            val dst = Rect(0, 0, screenW, screenH)
            canvas.drawBitmap(renderBitmap, null, dst, bitmapPaint)
            renderGun(canvas)
            renderHud(canvas)
            if (damageFlash > 0) {
                overlayPaint.color = Color.argb(100, 255, 0, 0)
                canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
            }
            if (deadFrames > 0) {
                overlayPaint.color = Color.argb(180, 60, 0, 0)
                canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
                val s = screenH * 0.12f
                hudPaint.textSize = s; hudShadowPaint.textSize = s
                val msg = "YOU DIED"
                val w = hudPaint.measureText(msg)
                canvas.drawText(msg, (screenW - w) / 2f + 4, screenH / 2f + 4, hudShadowPaint)
                canvas.drawText(msg, (screenW - w) / 2f, screenH / 2f, hudPaint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun renderCeilingFloor() {
        val ceil = 0xFF202028.toInt()
        val floor = 0xFF3a2a1a.toInt()
        val mid = renderH / 2
        var idx = 0
        for (y in 0 until renderH) {
            val c = if (y < mid) ceil else floor
            val dy = abs(y - mid) / mid.toDouble()
            val f = (dy * 1.1).coerceAtMost(1.0)
            val shaded = shade(c, 0.3 + 0.7 * f)
            for (x in 0 until renderW) pixels[idx++] = shaded
        }
    }

    private fun renderWalls() {
        for (x in 0 until renderW) {
            val camX = 2.0 * x / renderW - 1.0
            val rdx = dirX + planeX * camX
            val rdy = dirY + planeY * camX
            var mx = posX.toInt(); var my = posY.toInt()
            val ddx = if (rdx == 0.0) 1e30 else abs(1.0 / rdx)
            val ddy = if (rdy == 0.0) 1e30 else abs(1.0 / rdy)
            val stepX: Int; val stepY: Int
            var sideX: Double; var sideY: Double
            if (rdx < 0) { stepX = -1; sideX = (posX - mx) * ddx } else { stepX = 1; sideX = (mx + 1.0 - posX) * ddx }
            if (rdy < 0) { stepY = -1; sideY = (posY - my) * ddy } else { stepY = 1; sideY = (my + 1.0 - posY) * ddy }
            var hit = 0; var side = 0
            var safety = 0
            while (hit == 0 && safety < 64) {
                if (sideX < sideY) { sideX += ddx; mx += stepX; side = 0 }
                else { sideY += ddy; my += stepY; side = 1 }
                if (mx < 0 || my < 0 || mx >= mapW || my >= mapH) { hit = 1; break }
                if (map[my][mx] != 0) hit = 1
                safety++
            }
            val perp = if (side == 0) (mx - posX + (1 - stepX) / 2.0) / rdx
            else (my - posY + (1 - stepY) / 2.0) / rdy
            zBuffer[x] = perp
            val lineH = if (perp > 0.0001) (renderH / perp).toInt() else renderH * 4
            val drawStart = max(-lineH / 2 + renderH / 2, 0)
            val drawEnd = min(lineH / 2 + renderH / 2, renderH - 1)
            val base = if (side == 0) 0xFFaa4040.toInt() else 0xFF884040.toInt()
            val shadeFactor = (1.0 / (1.0 + perp * 0.15)).coerceIn(0.15, 1.0)
            val color = shade(base, shadeFactor)
            var p = drawStart * renderW + x
            for (y in drawStart..drawEnd) {
                pixels[p] = color
                p += renderW
            }
        }
    }

    private fun renderEnemies() {
        val visible = enemies.filter { it.alive }
            .sortedByDescending {
                val ex = it.x - posX; val ey = it.y - posY; ex * ex + ey * ey
            }
        for (e in visible) {
            val ex = e.x - posX
            val ey = e.y - posY
            val invDet = 1.0 / (planeX * dirY - dirX * planeY)
            val transX = invDet * (dirY * ex - dirX * ey)
            val transY = invDet * (-planeY * ex + planeX * ey)
            if (transY <= 0.1) continue
            val spriteScreenX = ((renderW / 2) * (1 + transX / transY)).toInt()
            val spriteHp = abs((renderH / transY)).toInt()
            val drawStartY = max(-spriteHp / 2 + renderH / 2, 0)
            val drawEndY = min(spriteHp / 2 + renderH / 2, renderH - 1)
            val spriteWp = spriteHp
            val drawStartX = max(-spriteWp / 2 + spriteScreenX, 0)
            val drawEndX = min(spriteWp / 2 + spriteScreenX, renderW - 1)
            val flash = e.hitFlash > 0
            val sf = (1.0 / (1.0 + transY * 0.15)).coerceIn(0.2, 1.0)
            for (sx in drawStartX..drawEndX) {
                if (transY >= zBuffer[sx]) continue
                val texX = ((sx - (-spriteWp / 2 + spriteScreenX)).toLong() * spriteW / spriteWp)
                    .toInt().coerceIn(0, spriteW - 1)
                for (sy in drawStartY..drawEndY) {
                    val d = sy * 256 - renderH * 128 + spriteHp * 128
                    val texY = ((d.toLong() * spriteH) / spriteHp / 256).toInt()
                        .coerceIn(0, spriteH - 1)
                    val c = enemySpritePixels[texY * spriteW + texX]
                    if ((c ushr 24) == 0) continue
                    pixels[sy * renderW + sx] = if (flash) 0xFFFFFFFF.toInt() else shade(c, sf)
                }
            }
        }
    }

    private fun renderGun(canvas: Canvas) {
        val cx = screenW / 2f
        val topY = screenH - screenH * 0.35f
        val bodyW = screenW * 0.25f
        val bodyH = screenH * 0.22f
        uiPaint.color = 0xFF3a3a3a.toInt()
        canvas.drawRect(cx - bodyW / 2, topY, cx + bodyW / 2, screenH.toFloat(), uiPaint)
        uiPaint.color = 0xFF1a1a1a.toInt()
        canvas.drawRect(cx - bodyW * 0.12f, topY - bodyH * 0.7f, cx + bodyW * 0.12f, topY, uiPaint)
        if (muzzleFrames > 0) {
            uiPaint.color = 0xFFFFEE55.toInt()
            val r = (bodyW * 0.28f) * (muzzleFrames / 4f)
            canvas.drawCircle(cx, topY - bodyH * 0.7f, r, uiPaint)
        }
    }

    private fun renderHud(canvas: Canvas) {
        val s = screenH * 0.07f
        hudPaint.textSize = s; hudShadowPaint.textSize = s
        fun txt(str: String, x: Float, y: Float) {
            canvas.drawText(str, x + 3, y + 3, hudShadowPaint)
            canvas.drawText(str, x, y, hudPaint)
        }
        val pad = screenH * 0.04f
        txt("HP $hp", pad, pad + s)
        txt("AMMO $ammo", pad, pad + s * 2 + 10f)
        val km = "KILLS $kills"
        val kw = hudPaint.measureText(km)
        txt(km, screenW - kw - pad, pad + s)

        // Левый джойстик
        if (leftPointerId != -1) {
            uiPaint.color = 0x55FFFFFF
            uiPaint.style = Paint.Style.STROKE
            uiPaint.strokeWidth = 4f
            canvas.drawCircle(leftStartX, leftStartY, joystickRadius(), uiPaint)
            uiPaint.style = Paint.Style.FILL
            uiPaint.color = 0xAAFFFFFF.toInt()
            val r = joystickRadius()
            val dx = (leftCurX - leftStartX).coerceIn(-r, r)
            val dy = (leftCurY - leftStartY).coerceIn(-r, r)
            canvas.drawCircle(leftStartX + dx, leftStartY + dy, r * 0.35f, uiPaint)
        }

        // Кнопка огня
        uiPaint.style = Paint.Style.FILL
        uiPaint.color = if (firePointerId != -1) 0xCCff5555.toInt() else 0x88cc3333.toInt()
        val fbx = screenW - fireBtnMargin()
        val fby = screenH - fireBtnMargin()
        val fr = fireBtnRadius()
        canvas.drawCircle(fbx, fby, fr, uiPaint)
        hudPaint.textAlign = Paint.Align.CENTER
        hudShadowPaint.textAlign = Paint.Align.CENTER
        hudPaint.textSize = fr * 0.45f
        hudShadowPaint.textSize = fr * 0.45f
        canvas.drawText("FIRE", fbx + 2, fby + fr * 0.15f + 2, hudShadowPaint)
        canvas.drawText("FIRE", fbx, fby + fr * 0.15f, hudPaint)
        hudPaint.textAlign = Paint.Align.LEFT
        hudShadowPaint.textAlign = Paint.Align.LEFT
    }

    private fun shade(color: Int, f: Double): Int {
        val ff = f.coerceIn(0.0, 1.0)
        val a = (color ushr 24) and 0xFF
        val r = (((color shr 16) and 0xFF) * ff).toInt()
        val g = (((color shr 8) and 0xFF) * ff).toInt()
        val b = ((color and 0xFF) * ff).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun buildEnemySprite(): Bitmap {
        val w = 32; val h = 48
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val body = Paint().apply { color = 0xFF7a1d1d.toInt(); isAntiAlias = true }
        val bodyDark = Paint().apply { color = 0xFF4a0d0d.toInt(); isAntiAlias = true }
        val head = Paint().apply { color = 0xFFa02a2a.toInt(); isAntiAlias = true }
        val eye = Paint().apply { color = 0xFFffff00.toInt(); isAntiAlias = true }
        val horn = Paint().apply { color = 0xFF2a0a0a.toInt(); isAntiAlias = true }
        // тело
        c.drawRect(6f, 20f, 26f, 44f, body)
        c.drawRect(6f, 36f, 26f, 44f, bodyDark)
        // руки
        c.drawRect(2f, 22f, 6f, 38f, body)
        c.drawRect(26f, 22f, 30f, 38f, body)
        // голова
        c.drawCircle(16f, 14f, 9f, head)
        // глаза
        c.drawCircle(12f, 13f, 1.8f, eye)
        c.drawCircle(20f, 13f, 1.8f, eye)
        // рога
        c.drawRect(9f, 3f, 11f, 9f, horn)
        c.drawRect(21f, 3f, 23f, 9f, horn)
        return bmp
    }
}
