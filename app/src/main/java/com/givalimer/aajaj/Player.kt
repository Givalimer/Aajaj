package com.givalimer.aajaj

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class Player {
    var x = 0f
    var y = 50f
    var z = 0f

    var yaw = 0f   // horizontal rotation (degrees)
    var pitch = 0f  // vertical rotation (degrees)

    var vx = 0f
    var vy = 0f
    var vz = 0f

    var moveX = 0f  // joystick input -1..1 (strafe)
    var moveZ = 0f  // joystick input -1..1 (forward/back)
    var lookDeltaX = 0f
    var lookDeltaY = 0f
    var jumping = false

    var onGround = false
    val eyeHeight = 1.62f
    private val playerHeight = 1.8f
    private val playerWidth = 0.6f
    private val speed = 4.3f
    private val gravity = -20f
    private val jumpVelocity = 8.5f

    var selectedBlock = BlockType.DIRT
    val hotbar = arrayOf(
        BlockType.DIRT, BlockType.STONE, BlockType.COBBLESTONE,
        BlockType.WOOD_PLANKS, BlockType.LOG, BlockType.GLASS,
        BlockType.BRICK, BlockType.SAND, BlockType.LEAVES
    )
    var selectedSlot = 0

    fun lookDirX(): Float {
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        return -sin(yawRad) * cos(pitchRad)
    }

    fun lookDirY(): Float {
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        return -sin(pitchRad)
    }

    fun lookDirZ(): Float {
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        return cos(yawRad) * cos(pitchRad)
    }

    fun update(dt: Float, world: World) {
        // Apply look
        yaw += lookDeltaX
        pitch = (pitch + lookDeltaY).coerceIn(-89f, 89f)
        lookDeltaX = 0f
        lookDeltaY = 0f

        // Calculate movement direction
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val forwardX = -sin(yawRad)
        val forwardZ = cos(yawRad)
        val rightX = cos(yawRad)
        val rightZ = sin(yawRad)

        // Move input (moveZ is forward/back, moveX is strafe)
        val inputX = -moveZ * forwardX + moveX * rightX
        val inputZ = -moveZ * forwardZ + moveX * rightZ

        vx = inputX * speed
        vz = inputZ * speed

        // Gravity
        if (!onGround) {
            vy += gravity * dt
        }

        // Jump
        if (jumping && onGround) {
            vy = jumpVelocity
            onGround = false
        }

        // Move with collision
        moveWithCollision(dt, world)
    }

    private fun moveWithCollision(dt: Float, world: World) {
        // X axis
        x += vx * dt
        if (checkCollision(world)) {
            x -= vx * dt
            vx = 0f
        }

        // Z axis
        z += vz * dt
        if (checkCollision(world)) {
            z -= vz * dt
            vz = 0f
        }

        // Y axis
        y += vy * dt
        if (checkCollision(world)) {
            if (vy < 0) onGround = true
            y -= vy * dt
            vy = 0f
        } else {
            onGround = false
        }
    }

    private fun checkCollision(world: World): Boolean {
        val hw = playerWidth / 2f
        val minX = floor(x - hw).toInt()
        val maxX = floor(x + hw).toInt()
        val minY = floor(y).toInt()
        val maxY = floor(y + playerHeight).toInt()
        val minZ = floor(z - hw).toInt()
        val maxZ = floor(z + hw).toInt()

        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    val block = world.getBlock(bx, by, bz)
                    if (block.solid) {
                        // AABB collision
                        if (x + hw > bx && x - hw < bx + 1 &&
                            y + playerHeight > by && y < by + 1 &&
                            z + hw > bz && z - hw < bz + 1) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun collidesWith(bx: Int, by: Int, bz: Int): Boolean {
        val hw = playerWidth / 2f
        return x + hw > bx && x - hw < bx + 1 &&
               y + playerHeight > by && y < by + 1 &&
               z + hw > bz && z - hw < bz + 1
    }
}
