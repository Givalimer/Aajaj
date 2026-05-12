package com.givalimer.aajaj

import kotlin.random.Random

/**
 * Cellular-automaton world.
 *
 * - `grid[y * cols + x]` stores the material ordinal.
 * - `life[i]`     stores a per-cell counter: burn timer, smoke age, ant direction, etc.
 * - `moved[i]`    is a frame-local flag preventing a cell from being stepped twice.
 *
 * Simulation is stepped bottom-up so falling bodies don't get processed twice.
 * Per row, sweep direction alternates left/right each frame to remove drift bias.
 */
class World(val cols: Int, val rows: Int) {

    val grid = ByteArray(cols * rows)  // Material.ordinal
    val life = ByteArray(cols * rows)  // state byte per cell
    private val moved = BooleanArray(cols * rows)
    private val rng = Random.Default
    private val materials = Material.values()

    private var frame = 0

    fun idx(x: Int, y: Int) = y * cols + x

    fun inBounds(x: Int, y: Int) = x in 0 until cols && y in 0 until rows

    fun getMat(x: Int, y: Int): Material {
        if (!inBounds(x, y)) return Material.STONE  // out of bounds acts as wall
        return materials[grid[idx(x, y)].toInt()]
    }

    fun setMat(x: Int, y: Int, m: Material, lifeValue: Int = 0) {
        if (!inBounds(x, y)) return
        val i = idx(x, y)
        grid[i] = m.ordinal.toByte()
        life[i] = lifeValue.toByte()
    }

    fun clear() {
        grid.fill(0); life.fill(0); moved.fill(false)
    }

    /** Paint a circular brush at (cx, cy) with the given material. */
    fun paintBrush(cx: Int, cy: Int, radius: Int, m: Material) {
        val mat = if (m == Material.ERASE) Material.EMPTY else m
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val x = cx + dx; val y = cy + dy
                if (!inBounds(x, y)) continue
                // Leave stone in place when painting anything lightweight? No - let users redesign freely.
                val lifeInit = when (m) {
                    Material.FIRE -> 40 + rng.nextInt(30)    // ticks before it dies
                    Material.SMOKE -> 120 + rng.nextInt(80)
                    Material.STEAM -> 100 + rng.nextInt(60)
                    Material.ANT -> rng.nextInt(4)           // direction 0..3
                    Material.PLANT -> 0
                    else -> 0
                }
                setMat(x, y, mat, lifeInit)
            }
        }
    }

    fun step() {
        moved.fill(false)
        frame++

        // Iterate bottom-up. Alternate scan direction per row to avoid bias.
        for (y in rows - 1 downTo 0) {
            val leftToRight = (y + frame) and 1 == 0
            val xRange = if (leftToRight) 0 until cols else cols - 1 downTo 0
            for (x in xRange) {
                val i = idx(x, y)
                if (moved[i]) continue
                val mat = materials[grid[i].toInt()]
                when (mat) {
                    Material.EMPTY, Material.STONE -> {}
                    Material.SAND, Material.SUGAR, Material.GUNPOWDER -> stepPowder(x, y, mat)
                    Material.WATER, Material.OIL, Material.ACID, Material.LAVA -> stepLiquid(x, y, mat)
                    Material.FIRE -> stepFire(x, y)
                    Material.SMOKE, Material.STEAM -> stepGas(x, y, mat)
                    Material.WOOD -> stepWood(x, y)
                    Material.PLANT -> stepPlant(x, y)
                    Material.ICE -> stepIce(x, y)
                    Material.ANT -> stepAnt(x, y)
                    Material.ERASE -> {}
                }
            }
        }
    }

    // ---------- individual material rules ----------

    private fun stepPowder(x: Int, y: Int, mat: Material) {
        // Gunpowder ignites from neighboring fire/lava
        if (mat == Material.GUNPOWDER && hasNeighbor(x, y, Material.FIRE, Material.LAVA)) {
            explode(x, y, 4)
            return
        }
        val selfD = mat.density
        // try down, then diag-down
        if (tryFall(x, y, 0, 1, selfD)) return
        val dir = if (rng.nextBoolean()) 1 else -1
        if (tryFall(x, y, dir, 1, selfD)) return
        tryFall(x, y, -dir, 1, selfD)
    }

    private fun stepLiquid(x: Int, y: Int, mat: Material) {
        // Lava solidifies on contact with water (and makes steam)
        if (mat == Material.LAVA) {
            if (neighborOf(x, y, Material.WATER)) {
                setMat(x, y, Material.STONE)
                replaceFirstNeighbor(x, y, Material.WATER, Material.STEAM, 120)
                return
            }
            // Lava ignites flammables around it
            igniteNeighbors(x, y, chance = 0.12f)
        }
        // Acid dissolves organic neighbors
        if (mat == Material.ACID) {
            tryDissolve(x, y)
        }
        // Oil on lava/fire catches fire
        if (mat == Material.OIL && hasNeighbor(x, y, Material.FIRE, Material.LAVA)) {
            setMat(x, y, Material.FIRE, 30 + rng.nextInt(30))
            return
        }
        // Water puts fire out, becomes steam
        if (mat == Material.WATER && hasNeighbor(x, y, Material.FIRE)) {
            replaceFirstNeighbor(x, y, Material.FIRE, Material.STEAM, 100)
            setMat(x, y, Material.STEAM, 100)
            return
        }

        val selfD = mat.density
        if (tryFall(x, y, 0, 1, selfD)) return
        val dir = if (rng.nextBoolean()) 1 else -1
        if (tryFall(x, y, dir, 1, selfD)) return
        if (tryFall(x, y, -dir, 1, selfD)) return
        // spread sideways
        if (tryFall(x, y, dir, 0, selfD)) return
        tryFall(x, y, -dir, 0, selfD)
    }

    private fun stepGas(x: Int, y: Int, mat: Material) {
        // Age + vanish
        val l = (life[idx(x, y)].toInt() and 0xFF) - 1
        if (l <= 0) { setMat(x, y, Material.EMPTY); return }
        life[idx(x, y)] = l.toByte()

        // Gases rise with sideways drift
        if (tryFall(x, y, 0, -1, mat.density, requireLower = true)) return
        val dir = if (rng.nextBoolean()) 1 else -1
        if (tryFall(x, y, dir, -1, mat.density, requireLower = true)) return
        tryFall(x, y, -dir, 0, mat.density, requireLower = true)
    }

    private fun stepFire(x: Int, y: Int) {
        val i = idx(x, y)
        var l = (life[i].toInt() and 0xFF) - 1
        if (l <= 0) {
            // burns out. Small chance to become smoke.
            if (rng.nextInt(3) == 0) setMat(x, y, Material.SMOKE, 80 + rng.nextInt(60))
            else setMat(x, y, Material.EMPTY)
            return
        }
        life[i] = l.toByte()
        // Ignite flammable neighbors
        igniteNeighbors(x, y, chance = 0.22f)
        // Try to float up like a gas
        tryFall(x, y, 0, -1, Material.FIRE.density, requireLower = true) ||
            tryFall(x, y, if (rng.nextBoolean()) 1 else -1, -1, Material.FIRE.density, requireLower = true)
    }

    private fun stepWood(x: Int, y: Int) {
        // Wood is static; fire / lava neighbors will turn it into fire.
        if (hasNeighbor(x, y, Material.FIRE, Material.LAVA)) {
            if (rng.nextFloat() < 0.10f) setMat(x, y, Material.FIRE, 60 + rng.nextInt(40))
        }
    }

    private fun stepPlant(x: Int, y: Int) {
        // Plants grow upward when watered.
        if (hasNeighbor(x, y, Material.FIRE, Material.LAVA)) {
            if (rng.nextFloat() < 0.15f) setMat(x, y, Material.FIRE, 30 + rng.nextInt(30))
            return
        }
        // Water adjacent → chance to grow up.
        if (hasNeighbor(x, y, Material.WATER) && y > 0 && rng.nextFloat() < 0.01f) {
            // grow into empty cell above, consume one water neighbor
            if (getMat(x, y - 1) == Material.EMPTY) {
                setMat(x, y - 1, Material.PLANT)
                replaceFirstNeighbor(x, y, Material.WATER, Material.EMPTY, 0)
            }
        }
    }

    private fun stepIce(x: Int, y: Int) {
        // Melt near fire/lava
        if (hasNeighbor(x, y, Material.FIRE, Material.LAVA) && rng.nextFloat() < 0.15f) {
            setMat(x, y, Material.WATER)
        }
    }

    private fun stepAnt(x: Int, y: Int) {
        val i = idx(x, y)
        // life stores direction 0..3
        var dir = (life[i].toInt() and 0xFF) % 4
        // sense sugar in a 5x5 area and aim toward it
        val sugarDir = smellSugar(x, y)
        if (sugarDir != -1) dir = sugarDir

        // Occasionally turn randomly to look alive
        if (rng.nextInt(30) == 0) dir = rng.nextInt(4)

        val (dx, dy) = dirs[dir]
        val nx = x + dx; val ny = y + dy
        if (!inBounds(nx, ny)) {
            life[i] = ((dir + 2) and 3).toByte()
            return
        }
        val target = getMat(nx, ny)
        when (target) {
            Material.EMPTY -> {
                setMat(nx, ny, Material.ANT, dir)
                setMat(x, y, Material.EMPTY)
                moved[idx(nx, ny)] = true
            }
            Material.SUGAR -> {
                // eat sugar and move into the tile
                setMat(nx, ny, Material.ANT, dir)
                setMat(x, y, Material.EMPTY)
                moved[idx(nx, ny)] = true
            }
            Material.WATER, Material.ACID, Material.LAVA, Material.FIRE -> {
                // dies
                setMat(x, y, Material.EMPTY)
            }
            else -> {
                // turn
                life[i] = ((dir + 1) and 3).toByte()
            }
        }
        // Ants drown/burn also when stepped onto by hazards via other code paths above.
        // Also: fall if nothing is below and cell below is empty
        if (inBounds(x, y + 1) && getMat(x, y + 1) == Material.EMPTY) {
            setMat(x, y + 1, Material.ANT, dir)
            setMat(x, y, Material.EMPTY)
            moved[idx(x, y + 1)] = true
        }
    }

    // ---------- helpers ----------

    private val dirs = arrayOf(
        Pair(1, 0), Pair(0, 1), Pair(-1, 0), Pair(0, -1)
    )

    private fun smellSugar(x: Int, y: Int): Int {
        for (r in 1..3) {
            for (dy in -r..r) for (dx in -r..r) {
                if (dx == 0 && dy == 0) continue
                if (getMat(x + dx, y + dy) == Material.SUGAR) {
                    // pick primary axis
                    return if (Math.abs(dx) >= Math.abs(dy)) {
                        if (dx > 0) 0 else 2
                    } else {
                        if (dy > 0) 1 else 3
                    }
                }
            }
        }
        return -1
    }

    private fun hasNeighbor(x: Int, y: Int, vararg mats: Material): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val m = getMat(x + dx, y + dy)
            if (m in mats) return true
        }
        return false
    }

    private fun neighborOf(x: Int, y: Int, mat: Material): Boolean = hasNeighbor(x, y, mat)

    private fun replaceFirstNeighbor(x: Int, y: Int, target: Material, replacement: Material, life: Int) {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            if (getMat(x + dx, y + dy) == target) {
                setMat(x + dx, y + dy, replacement, life)
                return
            }
        }
    }

    private fun igniteNeighbors(x: Int, y: Int, chance: Float) {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx; val ny = y + dy
            val m = getMat(nx, ny)
            if (m.flammable && rng.nextFloat() < chance) {
                setMat(nx, ny, Material.FIRE, 30 + rng.nextInt(40))
            }
        }
    }

    private fun tryDissolve(x: Int, y: Int) {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val m = getMat(x + dx, y + dy)
            when (m) {
                Material.WOOD, Material.PLANT, Material.SAND, Material.SUGAR,
                Material.ANT, Material.ICE -> {
                    if (rng.nextFloat() < 0.08f) {
                        setMat(x + dx, y + dy, Material.EMPTY)
                        if (rng.nextFloat() < 0.15f) setMat(x, y, Material.EMPTY) // acid gets consumed
                        return
                    }
                }
                else -> {}
            }
        }
    }

    private fun explode(x: Int, y: Int, radius: Int) {
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val d2 = dx * dx + dy * dy
            if (d2 > radius * radius) continue
            val nx = x + dx; val ny = y + dy
            if (!inBounds(nx, ny)) continue
            val m = getMat(nx, ny)
            // stone survives
            if (m == Material.STONE) continue
            setMat(nx, ny, Material.FIRE, 20 + rng.nextInt(40))
        }
    }

    /**
     * Try to move cell (x, y) by (dx, dy) when the target is less dense (we sink into it)
     * or empty. If requireLower is true we only move into strictly-lower-density cells
     * (used by gases going up - they only push into lighter stuff).
     */
    private fun tryFall(
        x: Int, y: Int, dx: Int, dy: Int, selfDensity: Int, requireLower: Boolean = false
    ): Boolean {
        val nx = x + dx; val ny = y + dy
        if (!inBounds(nx, ny)) return false
        val i = idx(x, y); val j = idx(nx, ny)
        if (moved[j]) return false
        val other = materials[grid[j].toInt()]
        val otherD = other.density
        val canMove = if (requireLower) selfDensity < otherD else selfDensity > otherD
        if (other == Material.EMPTY || canMove) {
            // swap cells (including life bytes)
            val g = grid[i]; grid[i] = grid[j]; grid[j] = g
            val l = life[i]; life[i] = life[j]; life[j] = l
            moved[j] = true
            return true
        }
        return false
    }
}
