package com.givalimer.aajaj

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Procedurally-generated textures. Each texture is a 64x64 ARGB int array
 * indexed as [y * SIZE + x]. Built once at startup so the renderer can just
 * sample pixels without any per-frame allocation.
 */
object Textures {
    const val SIZE = 64

    val wall: IntArray = makeWall()
    val exitWall: IntArray = makeExitWall()
    val notebook: IntArray = makeNotebook()
    val teacher: IntArray = makeTeacher()

    private fun rgb(r: Int, g: Int, b: Int): Int {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a.coerceIn(0, 255) shl 24) or
                (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or
                b.coerceIn(0, 255)
    }

    /** Classic school brick wall: beige bricks with darker mortar lines. */
    private fun makeWall(): IntArray {
        val out = IntArray(SIZE * SIZE)
        val brickH = 16
        val brickW = 32
        for (y in 0 until SIZE) {
            val row = y / brickH
            val offset = if (row % 2 == 0) 0 else brickW / 2
            for (x in 0 until SIZE) {
                val xx = (x + offset) % brickW
                val onMortarX = xx == 0 || xx == brickW - 1
                val onMortarY = y % brickH == 0 || y % brickH == brickH - 1
                val noise = ((x * 13 + y * 7 + x * y) and 15) - 8
                val c = if (onMortarX || onMortarY) {
                    rgb(60 + noise, 55 + noise, 48 + noise)
                } else {
                    rgb(210 + noise, 185 + noise, 140 + noise)
                }
                out[y * SIZE + x] = c
            }
        }
        return out
    }

    /** Bright green exit door with a panel and handle. */
    private fun makeExitWall(): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val border = x < 3 || x >= SIZE - 3 || y < 3 || y >= SIZE - 3
                val innerBorder = x in 8..9 || x in SIZE - 10..SIZE - 9 ||
                        y in 8..9 || y in SIZE - 10..SIZE - 9
                val handle = (x in 48..52) && (y in 30..34)
                val noise = ((x * 5 + y * 3) and 7) - 3
                val c = when {
                    handle -> rgb(230, 210, 80)
                    border -> rgb(30, 90, 40)
                    innerBorder -> rgb(40, 140, 55)
                    else -> rgb(70 + noise, 190 + noise, 90 + noise)
                }
                out[y * SIZE + x] = c
            }
        }
        // "EXIT" label - simple block letters
        val label = rgb(255, 255, 255)
        // Draw letters by setting pixels in a small area near top
        val letters = arrayOf(
            // E
            intArrayOf(10,18, 10,19, 10,20, 10,21, 11,18, 12,18, 11,19, 11,20, 11,21, 12,21),
            // X
            intArrayOf(14,18, 16,18, 15,19, 14,20, 16,20, 14,21, 16,21),
            // I
            intArrayOf(18,18, 19,18, 20,18, 19,19, 19,20, 18,21, 19,21, 20,21),
            // T
            intArrayOf(22,18, 23,18, 24,18, 23,19, 23,20, 23,21)
        )
        for (glyph in letters) {
            var i = 0
            while (i < glyph.size) {
                val px = glyph[i]; val py = glyph[i + 1]
                if (px in 0 until SIZE && py in 0 until SIZE) {
                    out[py * SIZE + px] = label
                }
                i += 2
            }
        }
        return out
    }

    /** Notebook sprite: yellow cover with red spiral and dark lines. */
    private fun makeNotebook(): IntArray {
        val out = IntArray(SIZE * SIZE)
        val transparent = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val nx = (x - 32) / 24.0
                val ny = (y - 32) / 28.0
                val inside = abs(nx) < 1.0 && abs(ny) < 1.0
                if (!inside) { out[y * SIZE + x] = transparent; continue }
                val edge = abs(nx) > 0.92 || abs(ny) > 0.92
                val spiral = (x in 9..12) && ((y - 8) % 8 in 0..4) && y in 10..54
                val lines = !edge && y % 7 == 3 && x in 16..54
                val c = when {
                    edge -> rgb(140, 100, 30)
                    spiral -> rgb(210, 50, 50)
                    lines -> rgb(180, 140, 60)
                    else -> rgb(245, 215, 90)
                }
                out[y * SIZE + x] = c
            }
        }
        return out
    }

    /**
     * Teacher sprite: 64x64. Transparent around a simple Baldi-like character -
     * bald head, white shirt, green sweater vest, red tie, angry eyes/brow.
     */
    private fun makeTeacher(): IntArray {
        val out = IntArray(SIZE * SIZE) { 0 }
        val skin = rgb(240, 205, 170)
        val skinShade = rgb(210, 175, 145)
        val sweater = rgb(40, 140, 55)
        val sweaterDark = rgb(25, 95, 38)
        val shirt = rgb(245, 245, 245)
        val tie = rgb(200, 30, 30)
        val eye = rgb(20, 20, 20)
        val brow = rgb(40, 25, 10)
        val mouth = rgb(100, 10, 10)

        fun fillCircle(cx: Int, cy: Int, r: Int, c: Int) {
            for (y in (cy - r)..(cy + r)) {
                for (x in (cx - r)..(cx + r)) {
                    if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                    val dx = x - cx; val dy = y - cy
                    if (dx * dx + dy * dy <= r * r) out[y * SIZE + x] = c
                }
            }
        }
        fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in y0..y1) for (x in x0..x1) {
                if (x in 0 until SIZE && y in 0 until SIZE) out[y * SIZE + x] = c
            }
        }

        // body (sweater vest + shirt collar)
        fillRect(18, 34, 46, 63, sweater)
        // vest shading on sides
        fillRect(18, 34, 22, 63, sweaterDark)
        fillRect(42, 34, 46, 63, sweaterDark)
        // shirt V-neck
        for (y in 34..42) {
            val w = (y - 34)
            fillRect(32 - w, y, 32 + w, y, shirt)
        }
        // tie
        fillRect(30, 38, 34, 41, tie)       // knot
        for (y in 42..55) {                  // triangle widening
            val w = (y - 42) / 3
            fillRect(32 - 1 - w, y, 32 + 1 + w, y, tie)
        }

        // neck
        fillRect(28, 28, 36, 34, skin)
        fillRect(28, 33, 36, 34, skinShade)

        // head (bald, slightly egg-shaped)
        fillCircle(32, 18, 13, skin)
        // head shadow on right
        for (y in 6..30) {
            for (x in 33..45) {
                val dx = x - 32; val dy = y - 18
                if (dx * dx + dy * dy <= 13 * 13 && dx > 4) {
                    out[y * SIZE + x] = skinShade
                }
            }
        }

        // eyebrows - angry, slanted toward center
        fillRect(23, 14, 29, 15, brow)
        fillRect(22, 13, 24, 14, brow)
        fillRect(35, 14, 41, 15, brow)
        fillRect(40, 13, 42, 14, brow)

        // eyes
        fillCircle(26, 19, 2, eye)
        fillCircle(38, 19, 2, eye)
        // eye whites highlight
        out[18 * SIZE + 26] = rgb(255, 255, 255)
        out[18 * SIZE + 38] = rgb(255, 255, 255)

        // mouth - stern line
        fillRect(28, 26, 36, 27, mouth)

        // ears
        fillCircle(20, 19, 2, skinShade)
        fillCircle(44, 19, 2, skinShade)

        return out
    }
}
