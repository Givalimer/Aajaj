package com.givalimer.aajaj

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Chunk(val cx: Int, val cz: Int) {

    companion object {
        const val SIZE = 16
        const val HEIGHT = 128
        const val ATLAS_SIZE = 4 // 4x4 texture atlas
    }

    val blocks = IntArray(SIZE * HEIGHT * SIZE) // blockType id
    var dirty = true
    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0

    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return BlockType.AIR
        return BlockType.fromId(blocks[index(x, y, z)])
    }

    fun setBlock(x: Int, y: Int, z: Int, type: BlockType) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return
        blocks[index(x, y, z)] = type.id
        dirty = true
    }

    private fun index(x: Int, y: Int, z: Int) = y * SIZE * SIZE + z * SIZE + x

    fun buildMesh(getNeighborBlock: (worldX: Int, worldY: Int, worldZ: Int) -> BlockType) {
        val verts = mutableListOf<Float>()
        val worldXOffset = cx * SIZE
        val worldZOffset = cz * SIZE

        for (y in 0 until HEIGHT) {
            for (z in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val block = getBlock(x, y, z)
                    if (block == BlockType.AIR) continue

                    val wx = worldXOffset + x
                    val wz = worldZOffset + z

                    // Check each face
                    for (face in Face.values()) {
                        val nx = x + face.dx
                        val ny = y + face.dy
                        val nz = z + face.dz

                        val neighbor = if (nx in 0 until SIZE && ny in 0 until HEIGHT && nz in 0 until SIZE) {
                            getBlock(nx, ny, nz)
                        } else {
                            getNeighborBlock(wx + face.dx, ny, wz + face.dz)
                        }

                        if (!neighbor.solid) {
                            val texId = when (face) {
                                Face.TOP -> block.topTex
                                Face.BOTTOM -> block.bottomTex
                                else -> block.sideTex
                            }
                            addFace(verts, wx.toFloat(), y.toFloat(), wz.toFloat(), face, texId)
                        }
                    }
                }
            }
        }

        // 6 floats per vertex: x, y, z, u, v, shade
        vertexCount = verts.size / 6
        val buffer = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(verts.toFloatArray())
        buffer.position(0)
        vertexBuffer = buffer
        dirty = false
    }

    private fun addFace(verts: MutableList<Float>, x: Float, y: Float, z: Float, face: Face, texId: Int) {
        val ts = 1.0f / ATLAS_SIZE
        val tx = (texId % ATLAS_SIZE) * ts
        val ty = (texId / ATLAS_SIZE) * ts

        val shade = when (face) {
            Face.TOP -> 1.0f
            Face.BOTTOM -> 0.5f
            Face.NORTH, Face.SOUTH -> 0.7f
            Face.EAST, Face.WEST -> 0.8f
        }

        // Each face = 2 triangles = 6 vertices
        val positions = when (face) {
            Face.TOP -> floatArrayOf(
                x, y + 1, z,     x, y + 1, z + 1,  x + 1, y + 1, z + 1,
                x, y + 1, z,     x + 1, y + 1, z + 1, x + 1, y + 1, z
            )
            Face.BOTTOM -> floatArrayOf(
                x, y, z + 1,     x, y, z,         x + 1, y, z,
                x, y, z + 1,     x + 1, y, z,     x + 1, y, z + 1
            )
            Face.NORTH -> floatArrayOf(
                x + 1, y, z,     x, y, z,         x, y + 1, z,
                x + 1, y, z,     x, y + 1, z,     x + 1, y + 1, z
            )
            Face.SOUTH -> floatArrayOf(
                x, y, z + 1,     x + 1, y, z + 1, x + 1, y + 1, z + 1,
                x, y, z + 1,     x + 1, y + 1, z + 1, x, y + 1, z + 1
            )
            Face.EAST -> floatArrayOf(
                x + 1, y, z + 1, x + 1, y, z,     x + 1, y + 1, z,
                x + 1, y, z + 1, x + 1, y + 1, z, x + 1, y + 1, z + 1
            )
            Face.WEST -> floatArrayOf(
                x, y, z,         x, y, z + 1,     x, y + 1, z + 1,
                x, y, z,         x, y + 1, z + 1, x, y + 1, z
            )
        }

        val uvs = floatArrayOf(
            tx, ty + ts,       tx, ty,           tx + ts, ty,
            tx, ty + ts,       tx + ts, ty,      tx + ts, ty + ts
        )

        for (i in 0 until 6) {
            verts.add(positions[i * 3])
            verts.add(positions[i * 3 + 1])
            verts.add(positions[i * 3 + 2])
            verts.add(uvs[i * 2])
            verts.add(uvs[i * 2 + 1])
            verts.add(shade)
        }
    }

    fun render(posHandle: Int, texCoordHandle: Int, shadeHandle: Int) {
        val buffer = vertexBuffer ?: return
        if (vertexCount == 0) return

        val stride = 6 * 4 // 6 floats * 4 bytes

        buffer.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(posHandle)

        buffer.position(3)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        buffer.position(5)
        GLES20.glVertexAttribPointer(shadeHandle, 1, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(shadeHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisableVertexAttribArray(shadeHandle)
    }
}
