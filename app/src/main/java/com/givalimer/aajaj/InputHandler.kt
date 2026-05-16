package com.givalimer.aajaj

import android.view.MotionEvent

class InputHandler {

    var moveX = 0f
    var moveZ = 0f
    var lookDeltaX = 0f
    var lookDeltaY = 0f
    var jumping = false
    var doBreak = false
    var doPlace = false

    // Touch tracking
    private var leftPointerId = -1
    private var rightPointerId = -1
    private var leftStartX = 0f
    private var leftStartY = 0f
    private var rightLastX = 0f
    private var rightLastY = 0f
    private var rightStartTime = 0L
    private var rightMoved = false

    private val joystickRadius = 120f
    private val lookSensitivity = 0.3f
    private val tapThreshold = 15f

    fun handleTouch(event: MotionEvent, screenWidth: Int, screenHeight: Int) {
        val halfWidth = screenWidth / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val id = event.getPointerId(idx)

                if (x < halfWidth) {
                    // Left side = joystick
                    leftPointerId = id
                    leftStartX = x
                    leftStartY = y
                    moveX = 0f
                    moveZ = 0f
                } else {
                    // Right side = look + tap to break/place
                    rightPointerId = id
                    rightLastX = x
                    rightLastY = y
                    rightStartTime = System.currentTimeMillis()
                    rightMoved = false
                }

                // Jump = bottom right corner
                if (x > screenWidth * 0.85f && y > screenHeight * 0.75f) {
                    jumping = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)

                    if (id == leftPointerId) {
                        val dx = (x - leftStartX).coerceIn(-joystickRadius, joystickRadius)
                        val dy = (y - leftStartY).coerceIn(-joystickRadius, joystickRadius)
                        moveX = dx / joystickRadius
                        moveZ = dy / joystickRadius
                    } else if (id == rightPointerId) {
                        val dx = x - rightLastX
                        val dy = y - rightLastY
                        if (Math.abs(dx) > 2f || Math.abs(dy) > 2f) {
                            rightMoved = true
                        }
                        lookDeltaX += dx * lookSensitivity
                        lookDeltaY += dy * lookSensitivity
                        rightLastX = x
                        rightLastY = y
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)

                if (id == leftPointerId) {
                    leftPointerId = -1
                    moveX = 0f
                    moveZ = 0f
                    jumping = false
                } else if (id == rightPointerId) {
                    val elapsed = System.currentTimeMillis() - rightStartTime
                    if (!rightMoved && elapsed < 300) {
                        // Short tap = break block
                        doBreak = true
                    } else if (!rightMoved && elapsed >= 300 && elapsed < 800) {
                        // Long tap = place block
                        doPlace = true
                    }
                    rightPointerId = -1
                }
            }
        }
    }
}
