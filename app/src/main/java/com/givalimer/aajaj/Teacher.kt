package com.givalimer.aajaj

import java.util.ArrayDeque

/**
 * Hunts the player through the maze using BFS on the grid.
 * Moves continuously in world space toward the next grid cell on the computed path,
 * and replans whenever it reaches the next cell or the player moves to a new cell.
 */
class Teacher(
    var x: Double,
    var y: Double,
    private val world: World
) {
    var speed: Double = 1.4 // cells per second - slow at first, scales with notebooks
    private var path: List<Pair<Int, Int>> = emptyList()
    private var lastPlayerCell: Pair<Int, Int> = Pair(-1, -1)

    fun update(dt: Double, playerX: Double, playerY: Double) {
        val myCell = Pair(x.toInt(), y.toInt())
        val playerCell = Pair(playerX.toInt(), playerY.toInt())

        // Replan if target moved or we have no plan
        if (playerCell != lastPlayerCell || path.isEmpty()) {
            path = bfs(myCell, playerCell)
            lastPlayerCell = playerCell
        }

        if (path.isEmpty()) return
        // Walk toward the center of the next cell in the path
        val next = path.first()
        val targetX = next.first + 0.5
        val targetY = next.second + 0.5
        val dx = targetX - x
        val dy = targetY - y
        val dist = Math.hypot(dx, dy)
        val step = speed * dt
        if (dist <= step) {
            x = targetX
            y = targetY
            path = path.drop(1)
        } else {
            x += dx / dist * step
            y += dy / dist * step
        }
    }

    private fun bfs(start: Pair<Int, Int>, goal: Pair<Int, Int>): List<Pair<Int, Int>> {
        if (start == goal) return emptyList()
        if (world.isBlocking(goal.first, goal.second)) return emptyList()

        val visited = HashSet<Pair<Int, Int>>()
        val parent = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        val q = ArrayDeque<Pair<Int, Int>>()
        q.add(start)
        visited.add(start)

        val dirs = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0),
            intArrayOf(0, 1), intArrayOf(0, -1)
        )

        var found = false
        while (q.isNotEmpty()) {
            val cur = q.poll() ?: break
            if (cur == goal) { found = true; break }
            for (d in dirs) {
                val nx = cur.first + d[0]
                val ny = cur.second + d[1]
                val np = Pair(nx, ny)
                if (np in visited) continue
                if (world.isBlocking(nx, ny)) continue
                visited.add(np)
                parent[np] = cur
                q.add(np)
            }
        }
        if (!found) return emptyList()

        val rev = ArrayDeque<Pair<Int, Int>>()
        var cur: Pair<Int, Int>? = goal
        while (cur != null && cur != start) {
            rev.addFirst(cur)
            cur = parent[cur]
        }
        return rev.toList()
    }
}
