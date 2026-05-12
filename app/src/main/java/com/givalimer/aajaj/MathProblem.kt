package com.givalimer.aajaj

import kotlin.random.Random

/**
 * Simple math problem with up to three answer choices. One is correct.
 */
data class MathProblem(
    val question: String,
    val choices: List<Int>,
    val correctIndex: Int
) {
    companion object {
        fun random(difficulty: Int, rng: Random = Random.Default): MathProblem {
            val maxVal = 5 + difficulty * 5
            val a = rng.nextInt(1, maxVal)
            val b = rng.nextInt(1, maxVal)
            val opIdx = rng.nextInt(if (difficulty >= 2) 3 else 2)
            val (answer, text) = when (opIdx) {
                0 -> Pair(a + b, "$a + $b = ?")
                1 -> {
                    val hi = maxOf(a, b); val lo = minOf(a, b)
                    Pair(hi - lo, "$hi - $lo = ?")
                }
                else -> Pair(a * b, "$a × $b = ?")
            }
            val choices = mutableSetOf(answer)
            while (choices.size < 3) {
                val delta = rng.nextInt(-4, 5)
                if (delta == 0) continue
                val c = answer + delta
                if (c < 0) continue
                choices.add(c)
            }
            val list = choices.toMutableList()
            list.shuffle(rng)
            return MathProblem(text, list, list.indexOf(answer))
        }
    }
}
