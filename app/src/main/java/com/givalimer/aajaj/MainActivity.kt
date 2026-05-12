package com.givalimer.aajaj

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        gameView = findViewById(R.id.gameView)
        gameView.listener = this
    }

    override fun onResume() { super.onResume(); gameView.resumeLoop() }
    override fun onPause() { super.onPause(); gameView.pauseLoop() }

    override fun onShowMathProblem(problem: MathProblem, onResult: (Boolean) -> Unit) {
        runOnUiThread {
            gameView.setWaitingForAnswer(true)
            val b = AlertDialog.Builder(this)
                .setTitle("Математика")
                .setMessage(problem.question)
                .setCancelable(false)
            val labels = problem.choices.map { it.toString() }.toTypedArray()
            b.setItems(labels) { d: DialogInterface, which: Int ->
                d.dismiss()
                val correct = which == problem.correctIndex
                Toast.makeText(
                    this,
                    if (correct) "Правильно!" else "Неправильно!",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(correct)
            }
            b.show()
        }
    }

    override fun onWin() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Победа!")
                .setMessage("Ты собрал все тетради и сбежал из школы!")
                .setPositiveButton("Заново") { _, _ -> recreate() }
                .setCancelable(false)
                .show()
        }
    }

    override fun onLose() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Пойман!")
                .setMessage("Учитель догнал тебя...")
                .setPositiveButton("Ещё раз") { _, _ -> recreate() }
                .setCancelable(false)
                .show()
        }
    }

    override fun onHudUpdate(collected: Int, total: Int) {
        // HUD is drawn inside the view; nothing to do here.
    }
}
