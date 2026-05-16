package com.givalimer.aajaj

import kotlin.math.floor

/**
 * Simple Perlin-like noise implementation for terrain generation
 */
class SimplexNoise(seed: Long = 0L) {

    private val perm = IntArray(512)

    init {
        val p = IntArray(256)
        val rng = java.util.Random(seed)
        for (i in 0 until 256) p[i] = i
        for (i in 255 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = p[i]
            p[i] = p[j]
            p[j] = tmp
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    fun noise(x: Double, z: Double): Double {
        return perlin2D(x, z)
    }

    fun noise(x: Double, y: Double, z: Double): Double {
        return perlin3D(x, y, z)
    }

    private fun perlin2D(x: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val zf = z - floor(z)
        val u = fade(xf)
        val v = fade(zf)

        val aa = perm[perm[xi] + zi]
        val ab = perm[perm[xi] + zi + 1]
        val ba = perm[perm[xi + 1] + zi]
        val bb = perm[perm[xi + 1] + zi + 1]

        val x1 = lerp(grad2D(aa, xf, zf), grad2D(ba, xf - 1, zf), u)
        val x2 = lerp(grad2D(ab, xf, zf - 1), grad2D(bb, xf - 1, zf - 1), u)
        return lerp(x1, x2, v)
    }

    private fun perlin3D(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)
        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val aaa = perm[perm[perm[xi] + yi] + zi]
        val aba = perm[perm[perm[xi] + yi + 1] + zi]
        val aab = perm[perm[perm[xi] + yi] + zi + 1]
        val abb = perm[perm[perm[xi] + yi + 1] + zi + 1]
        val baa = perm[perm[perm[xi + 1] + yi] + zi]
        val bba = perm[perm[perm[xi + 1] + yi + 1] + zi]
        val bab = perm[perm[perm[xi + 1] + yi] + zi + 1]
        val bbb = perm[perm[perm[xi + 1] + yi + 1] + zi + 1]

        val x1 = lerp(grad3D(aaa, xf, yf, zf), grad3D(baa, xf - 1, yf, zf), u)
        val x2 = lerp(grad3D(aba, xf, yf - 1, zf), grad3D(bba, xf - 1, yf - 1, zf), u)
        val y1 = lerp(x1, x2, v)
        val x3 = lerp(grad3D(aab, xf, yf, zf - 1), grad3D(bab, xf - 1, yf, zf - 1), u)
        val x4 = lerp(grad3D(abb, xf, yf - 1, zf - 1), grad3D(bbb, xf - 1, yf - 1, zf - 1), u)
        val y2 = lerp(x3, x4, v)
        return lerp(y1, y2, w)
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)

    private fun grad2D(hash: Int, x: Double, z: Double): Double {
        return when (hash and 3) {
            0 -> x + z
            1 -> -x + z
            2 -> x - z
            3 -> -x - z
            else -> 0.0
        }
    }

    private fun grad3D(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if (h and 1 == 0) u else -u) + (if (h and 2 == 0) v else -v)
    }
}
