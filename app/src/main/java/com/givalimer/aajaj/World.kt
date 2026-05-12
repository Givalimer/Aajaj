package com.givalimer.aajaj

import kotlin.random.Random

/**
 * The school world: a grid of cells. 1 = wall, 0 = floor, 2 = notebook pickup.
 * Starts as a simple room-and-corridor layout and is deterministic for a given seed.
 */
class World(val width: Int = 21, val height: Int = 21, seed: Long = 42L) {

    companion object {
        const val FLOOR = 0
        const val WALL = 1
        const val NOTEBOOK = 2
        const val EXIT = 3
    }

    private val cells = IntArray(width * height)
    val notebookPositions = mutableListOf<Pair<Int, Int>>()
    var playerStart = Pair(1, 1)
        private set
    var teacherStart = Pair(width - 2, height - 2)
        private set
    var exitPosition = Pair(width - 2, 1)
        private set

    init {
        generate(seed)
    }

    fun get(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return WALL
        return cells[y * width + x]
    }

    fun set(x: Int, y: Int, v: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        cells[y * width + x] = v
    }

    fun isBlocking(x: Int, y: Int): Boolean {
        val c = get(x, y)
        return c == WALL
    }

    /**
     * Simple maze-ish layout: rooms carved out of a solid block and connected by
     * straight corridors. Not a perfect maze but good enough for a school feel.
     */
    private fun generate(seed: Long) {
        val rng = Random(seed)
        // fill with walls
        for (i in cells.indices) cells[i] = WALL

        // carve rooms
        val rooms = mutableListOf<IntArray>() // x, y, w, h
        val tries = 18
        repeat(tries) {
            val rw = 3 + rng.nextInt(3)
            val rh = 3 + rng.nextInt(3)
            val rx = 1 + rng.nextInt((width - rw - 2).coerceAtLeast(1))
            val ry = 1 + rng.nextInt((height - rh - 2).coerceAtLeast(1))
            // check overlap with 1-cell padding
            val overlaps = rooms.any { r ->
                !(rx + rw + 1 < r[0] || rx > r[0] + r[2] + 1 ||
                        ry + rh + 1 < r[1] || ry > r[1] + r[3] + 1)
            }
            if (!overlaps) {
                for (y in ry until ry + rh) {
                    for (x in rx until rx + rw) {
                        set(x, y, FLOOR)
                    }
                }
                rooms.add(intArrayOf(rx, ry, rw, rh))
            }
        }

        // connect rooms with L-shaped corridors
        for (i in 1 until rooms.size) {
            val a = rooms[i - 1]
            val b = rooms[i]
            val ax = a[0] + a[2] / 2
            val ay = a[1] + a[3] / 2
            val bx = b[0] + b[2] / 2
            val by = b[1] + b[3] / 2
            if (rng.nextBoolean()) {
                carveHLine(ax, bx, ay)
                carveVLine(ay, by, bx)
            } else {
                carveVLine(ay, by, ax)
                carveHLine(ax, bx, by)
            }
        }

        if (rooms.isNotEmpty()) {
            val first = rooms.first()
            playerStart = Pair(first[0] + 1, first[1] + 1)
            val last = rooms.last()
            teacherStart = Pair(last[0] + last[2] - 2, last[1] + last[3] - 2)
            // exit in the middle-ish room
            val mid = rooms[rooms.size / 2]
            exitPosition = Pair(mid[0] + mid[2] / 2, mid[1] + mid[3] / 2)
            set(exitPosition.first, exitPosition.second, EXIT)
        }

        // scatter notebooks in rooms (not on player start / exit)
        val needed = 5
        val placed = mutableListOf<Pair<Int, Int>>()
        var guard = 0
        while (placed.size < needed && guard < 500) {
            guard++
            if (rooms.isEmpty()) break
            val r = rooms[rng.nextInt(rooms.size)]
            val nx = r[0] + rng.nextInt(r[2])
            val ny = r[1] + rng.nextInt(r[3])
            val pos = Pair(nx, ny)
            if (get(nx, ny) != FLOOR) continue
            if (pos == playerStart || pos == exitPosition || pos == teacherStart) continue
            if (placed.contains(pos)) continue
            set(nx, ny, NOTEBOOK)
            placed.add(pos)
        }
        notebookPositions.addAll(placed)
    }

    private fun carveHLine(x1: Int, x2: Int, y: Int) {
        val from = minOf(x1, x2)
        val to = maxOf(x1, x2)
        for (x in from..to) if (get(x, y) == WALL) set(x, y, FLOOR)
    }

    private fun carveVLine(y1: Int, y2: Int, x: Int) {
        val from = minOf(y1, y2)
        val to = maxOf(y1, y2)
        for (y in from..to) if (get(x, y) == WALL) set(x, y, FLOOR)
    }
}
