package com.givalimer.aajaj

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = findViewById<SandboxView>(R.id.sandboxView)

        findViewById<Button>(R.id.btnSand).setOnClickListener {
            view.currentMaterial = Material.SAND
        }
        findViewById<Button>(R.id.btnWater).setOnClickListener {
            view.currentMaterial = Material.WATER
        }
        findViewById<Button>(R.id.btnStone).setOnClickListener {
            view.currentMaterial = Material.STONE
        }
        findViewById<Button>(R.id.btnErase).setOnClickListener {
            view.currentMaterial = Material.EMPTY
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            view.clearGrid()
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<SandboxView>(R.id.sandboxView).resumeLoop()
    }

    override fun onPause() {
        super.onPause()
        findViewById<SandboxView>(R.id.sandboxView).pauseLoop()
    }
}
