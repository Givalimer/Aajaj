package com.givalimer.aajaj

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var view: SandboxView
    private val buttons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        view = findViewById(R.id.sandboxView)
        val palette = findViewById<LinearLayout>(R.id.paletteRow)

        // Build a button per material in the palette order defined in Material enum,
        // skipping EMPTY (it's "air" - drawn via erase).
        val materialsForUi = Material.values().filter { it != Material.EMPTY }

        materialsForUi.forEach { mat ->
            val btn = Button(this).apply {
                text = mat.displayName
                tag = mat
                isAllCaps = false
                setTextColor(readableTextColor(mat.color))
                background = makeButtonBg(mat.color)
                val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                lp.marginEnd = dp(6)
                layoutParams = lp
                minWidth = dp(80)
                setOnClickListener {
                    view.currentMaterial = mat
                    refreshSelection(this)
                }
            }
            buttons.add(btn)
            palette.addView(btn)
        }

        // Clear button
        val clear = Button(this).apply {
            text = getString(R.string.action_clear)
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = makeButtonBg(0xFF8A1A1A.toInt())
            setOnClickListener { view.clearWorld() }
        }
        palette.addView(clear)

        // Initial selection = sand (fallback to first button if somehow missing)
        val sandBtn = buttons.firstOrNull { it.tag == Material.SAND } ?: buttons.first()
        view.currentMaterial = (sandBtn.tag as? Material) ?: Material.SAND
        refreshSelection(sandBtn)

        // Brush size slider
        val slider = findViewById<SeekBar>(R.id.brushSlider)
        slider.max = 12
        slider.progress = 3
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                view.brushRadius = progress.coerceAtLeast(1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onResume() { super.onResume(); view.resumeLoop() }
    override fun onPause() { super.onPause(); view.pauseLoop() }

    private fun refreshSelection(active: Button) {
        for (b in buttons) {
            val mat = b.tag as? Material
            val c = mat?.color ?: 0x444444
            b.background = if (b === active) makeButtonBgSelected(c) else makeButtonBg(c)
        }
    }

    private fun makeButtonBg(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(color)
            setStroke(dp(1), 0x33FFFFFF.toInt())
        }
    }

    private fun makeButtonBgSelected(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(color)
            setStroke(dp(3), 0xFFFFFFFF.toInt())
        }
    }

    private fun readableTextColor(bg: Int): Int {
        // If color is transparent or very dark, use white; else black.
        if ((bg ushr 24) and 0xFF < 32) return Color.WHITE
        val r = (bg ushr 16) and 0xFF
        val g = (bg ushr 8) and 0xFF
        val b = bg and 0xFF
        val luma = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luma > 150) Color.BLACK else Color.WHITE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
