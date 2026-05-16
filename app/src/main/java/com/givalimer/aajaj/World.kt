package com.givalimer.aajaj

import android.opengl.Matrix
import kotlin.math.floor

class World {

    companion object {
        const val RENDER_DISTANCE = 2 // chunks (small for mobile)
        const val SEA_LEVEL = 32
    }

    val player = Player()
    private val chunks = mutableMapOf<Long, Chunk>()
    private val noise = SimplexNoise(seed = 12345L)
    @Volatile var isReady = false

    fun generate() {
        // Generate initial chunks around spawn
        for (cx in -RENDER_DISTANCE..RENDER_DISTANCE) {
            for (cz in -RENDER_DISTANCE..RENDER_DISTANCE) {
                getOrCreateChunk(cx, cz)
            }
        }

        // Place player on surface
        val spawnY = getHeightAt(0, 0) + 2
        player.x = 0.5f
        player.y = spawnY.toFloat()
        player.z = 0.5f

        isReady = true
    }

    fun getOrCreateChunk(cx: Int, cz: Int): Chunk {
        val key = chunkKey(cx, cz)
        return chunks.getOrPut(key) {
            val chunk = Chunk(cx, cz)
            generateTerrain(chunk)
            chunk
        }
    }

    private fun generateTerrain(chunk: Chunk) {
        val worldX = chunk.cx * Chunk.SIZE
        val worldZ = chunk.cz * Chunk.SIZE

        for (lx in 0 until Chunk.SIZE) {
            for (lz in 0 until Chunk.SIZE) {
                val wx = worldX + lx
                val wz = worldZ + lz

                // Generate height using multiple octaves of noise
                val height = getHeightAt(wx, wz)

                for (y in 0 until Chunk.HEIGHT) {
                    val block = when {
                        y == 0 -> BlockType.BEDROCK
                        y < height - 4 -> {
                            // Underground with ores
                            val oreNoise = noise.noise(wx * 0.1, y * 0.1, wz * 0.1)
                            when {
                                oreNoise > 0.85 && y < 30 -> BlockType.IRON_ORE
                                oreNoise > 0.8 && y < 50 -> BlockType.COAL_ORE
                                else -> BlockType.STONE
                            }
                        }
                        y < height -> BlockType.DIRT
                        y == height -> {
                            if (height <= SEA_LEVEL + 1) BlockType.SAND else BlockType.GRASS
                        }
                        y <= SEA_LEVEL -> BlockType.WATER
                        else -> BlockType.AIR
                    }
                    chunk.setBlock(lx, y, lz, block)
                }

                // Trees
                if (height > SEA_LEVEL + 2 && lx in 2..13 && lz in 2..13) {
                    val treeNoise = noise.noise(wx * 0.5, 0.0, wz * 0.5)
                    if (treeNoise > 0.9) {
                        placeTree(chunk, lx, height + 1, lz)
                    }
                }
            }
        }
    }

    private fun placeTree(chunk: Chunk, x: Int, y: Int, z: Int) {
        val trunkHeight = 4 + (Math.random() * 3).toInt()
        // Trunk
        for (ty in 0 until trunkHeight) {
            chunk.setBlock(x, y + ty, z, BlockType.LOG)
        }
        // Leaves
        val leafStart = y + trunkHeight - 2
        for (ly in leafStart..y + trunkHeight + 1) {
            val radius = if (ly >= y + trunkHeight) 1 else 2
            for (lx in -radius..radius) {
                for (lz in -radius..radius) {
                    if (lx == 0 && lz == 0 && ly < y + trunkHeight) continue
                    val nx = x + lx
                    val nz = z + lz
                    if (nx in 0 until Chunk.SIZE && nz in 0 until Chunk.SIZE &&
                        chunk.getBlock(nx, ly, nz) == BlockType.AIR) {
                        chunk.setBlock(nx, ly, nz, BlockType.LEAVES)
                    }
                }
            }
        }
    }

    fun getHeightAt(wx: Int, wz: Int): Int {
        val n1 = noise.noise(wx * 0.01, wz * 0.01) * 20.0
        val n2 = noise.noise(wx * 0.05, wz * 0.05) * 5.0
        val n3 = noise.noise(wx * 0.1, wz * 0.1) * 2.0
        return (SEA_LEVEL + n1 + n2 + n3).toInt().coerceIn(5, Chunk.HEIGHT - 10)
    }

    private fun rebuildAllMeshes() {
        for (chunk in chunks.values) {
            chunk.buildMesh { wx, wy, wz -> getBlock(wx, wy, wz) }
        }
    }

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < 0 || wy >= Chunk.HEIGHT) return BlockType.AIR
        val cx = floor(wx / Chunk.SIZE.toDouble()).toInt()
        val cz = floor(wz / Chunk.SIZE.toDouble()).toInt()
        val chunk = chunks[chunkKey(cx, cz)] ?: return BlockType.AIR
        val lx = ((wx % Chunk.SIZE) + Chunk.SIZE) % Chunk.SIZE
        val lz = ((wz % Chunk.SIZE) + Chunk.SIZE) % Chunk.SIZE
        return chunk.getBlock(lx, wy, lz)
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, type: BlockType) {
        if (wy < 0 || wy >= Chunk.HEIGHT) return
        val cx = floor(wx / Chunk.SIZE.toDouble()).toInt()
        val cz = floor(wz / Chunk.SIZE.toDouble()).toInt()
        val chunk = chunks[chunkKey(cx, cz)] ?: return
        val lx = ((wx % Chunk.SIZE) + Chunk.SIZE) % Chunk.SIZE
        val lz = ((wz % Chunk.SIZE) + Chunk.SIZE) % Chunk.SIZE
        chunk.setBlock(lx, wy, lz, type)
        chunk.buildMesh { x, y, z -> getBlock(x, y, z) }

        // Rebuild neighbor chunks if on edge
        if (lx == 0) chunks[chunkKey(cx - 1, cz)]?.apply { dirty = true; buildMesh { x, y, z -> getBlock(x, y, z) } }
        if (lx == Chunk.SIZE - 1) chunks[chunkKey(cx + 1, cz)]?.apply { dirty = true; buildMesh { x, y, z -> getBlock(x, y, z) } }
        if (lz == 0) chunks[chunkKey(cx, cz - 1)]?.apply { dirty = true; buildMesh { x, y, z -> getBlock(x, y, z) } }
        if (lz == Chunk.SIZE - 1) chunks[chunkKey(cx, cz + 1)]?.apply { dirty = true; buildMesh { x, y, z -> getBlock(x, y, z) } }
    }

    fun renderChunks(posHandle: Int, texCoordHandle: Int, shadeHandle: Int, mvpHandle: Int, vpMatrix: FloatArray) {
        for (chunk in chunks.values) {
            if (chunk.dirty) {
                chunk.buildMesh { wx, wy, wz -> getBlock(wx, wy, wz) }
            }
            chunk.render(posHandle, texCoordHandle, shadeHandle)
        }
    }

    data class RayHit(val x: Int, val y: Int, val z: Int, val face: Face?)

    fun raycast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float): RayHit? {
        // DDA raycasting
        var t = 0f
        val step = 0.05f
        var lastX = floor(ox).toInt()
        var lastY = floor(oy).toInt()
        var lastZ = floor(oz).toInt()

        while (t < maxDist) {
            t += step
            val px = ox + dx * t
            val py = oy + dy * t
            val pz = oz + dz * t
            val bx = floor(px).toInt()
            val by = floor(py).toInt()
            val bz = floor(pz).toInt()

            val block = getBlock(bx, by, bz)
            if (block != BlockType.AIR && block.solid) {
                // Determine face
                val fdx = bx - lastX
                val fdy = by - lastY
                val fdz = bz - lastZ
                val face = when {
                    fdy == 1 -> Face.BOTTOM
                    fdy == -1 -> Face.TOP
                    fdx == 1 -> Face.WEST
                    fdx == -1 -> Face.EAST
                    fdz == 1 -> Face.NORTH
                    fdz == -1 -> Face.SOUTH
                    else -> Face.TOP
                }
                return RayHit(bx, by, bz, face)
            }
            lastX = bx
            lastY = by
            lastZ = bz
        }
        return null
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
