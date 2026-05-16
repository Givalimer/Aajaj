package com.givalimer.aajaj

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer: GameRenderer
    val inputHandler: InputHandler

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer = GameRenderer(context)
        inputHandler = InputHandler()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!renderer.world.isReady) return true
        inputHandler.handleTouch(event, width, height)
        renderer.world.player.apply {
            moveX = inputHandler.moveX
            moveZ = inputHandler.moveZ
            lookDeltaX = inputHandler.lookDeltaX
            lookDeltaY = inputHandler.lookDeltaY
            jumping = inputHandler.jumping
        }
        inputHandler.lookDeltaX = 0f
        inputHandler.lookDeltaY = 0f

        if (inputHandler.doBreak) {
            renderer.breakBlock()
            inputHandler.doBreak = false
        }
        if (inputHandler.doPlace) {
            renderer.placeBlock()
            inputHandler.doPlace = false
        }

        return true
    }
}
