package com.givalimer.aajaj

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils

object TextureManager {

    var atlasTextureId = 0
    private const val ATLAS_SIZE = 4 // 4x4 grid
    private const val TEX_SIZE = 16 // 16x16 pixels per texture
    private const val ATLAS_PIXELS = ATLAS_SIZE * TEX_SIZE // 64x64 atlas

    fun loadTextures(context: Context) {
        val atlas = generateAtlas()
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        atlasTextureId = texIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlas, 0)
        atlas.recycle()
    }

    private fun generateAtlas(): Bitmap {
        val bitmap = Bitmap.createBitmap(ATLAS_PIXELS, ATLAS_PIXELS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Generate procedural textures for each block
        // Index 0: Grass top (green)
        drawTexture(canvas, paint, 0, ::drawGrassTop)
        // Index 1: Grass side
        drawTexture(canvas, paint, 1, ::drawGrassSide)
        // Index 2: Dirt
        drawTexture(canvas, paint, 2, ::drawDirt)
        // Index 3: Stone
        drawTexture(canvas, paint, 3, ::drawStone)
        // Index 4: Cobblestone
        drawTexture(canvas, paint, 4, ::drawCobblestone)
        // Index 5: Wood planks
        drawTexture(canvas, paint, 5, ::drawPlanks)
        // Index 6: Log top
        drawTexture(canvas, paint, 6, ::drawLogTop)
        // Index 7: Log side
        drawTexture(canvas, paint, 7, ::drawLogSide)
        // Index 8: Leaves
        drawTexture(canvas, paint, 8, ::drawLeaves)
        // Index 9: Sand
        drawTexture(canvas, paint, 9, ::drawSand)
        // Index 10: Water
        drawTexture(canvas, paint, 10, ::drawWater)
        // Index 11: Bedrock
        drawTexture(canvas, paint, 11, ::drawBedrock)
        // Index 12: Coal ore
        drawTexture(canvas, paint, 12, ::drawCoalOre)
        // Index 13: Iron ore
        drawTexture(canvas, paint, 13, ::drawIronOre)
        // Index 14: Glass
        drawTexture(canvas, paint, 14, ::drawGlass)
        // Index 15: Brick
        drawTexture(canvas, paint, 15, ::drawBrick)

        return bitmap
    }

    private fun drawTexture(canvas: Canvas, paint: Paint, index: Int, drawer: (Canvas, Paint, Int, Int) -> Unit) {
        val x = (index % ATLAS_SIZE) * TEX_SIZE
        val y = (index / ATLAS_SIZE) * TEX_SIZE
        drawer(canvas, paint, x, y)
    }

    private fun drawGrassTop(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val g = 100 + (Math.random() * 55).toInt()
                paint.color = Color.rgb(30, g, 20)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawGrassSide(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                if (py < 4) {
                    val g = 100 + (Math.random() * 55).toInt()
                    paint.color = Color.rgb(30, g, 20)
                } else {
                    val b = 120 + (Math.random() * 40).toInt()
                    paint.color = Color.rgb(b, (b * 0.7).toInt(), (b * 0.5).toInt())
                }
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawDirt(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val b = 120 + (Math.random() * 40).toInt()
                paint.color = Color.rgb(b, (b * 0.7).toInt(), (b * 0.5).toInt())
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawStone(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val v = 100 + (Math.random() * 50).toInt()
                paint.color = Color.rgb(v, v, v)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawCobblestone(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val v = 80 + (Math.random() * 70).toInt()
                paint.color = Color.rgb(v, v, v + 5)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawPlanks(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val base = if (py % 4 == 0) 140 else 180
                val v = base + (Math.random() * 20).toInt()
                paint.color = Color.rgb(v, (v * 0.75).toInt(), (v * 0.45).toInt())
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawLogTop(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val dx = px - 8
                val dy = py - 8
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                val v = if (dist < 5) 140 + (Math.random() * 30).toInt() else 80 + (Math.random() * 30).toInt()
                paint.color = Color.rgb(v, (v * 0.7).toInt(), (v * 0.4).toInt())
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawLogSide(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val base = if (px % 3 == 0) 60 else 90
                val v = base + (Math.random() * 20).toInt()
                paint.color = Color.rgb(v, (v * 0.65).toInt(), (v * 0.3).toInt())
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawLeaves(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val g = 60 + (Math.random() * 80).toInt()
                val a = if (Math.random() > 0.2) 255 else 0
                paint.color = Color.argb(a, 20, g, 10)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawSand(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val v = 200 + (Math.random() * 40).toInt()
                paint.color = Color.rgb(v, (v * 0.9).toInt(), (v * 0.6).toInt())
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawWater(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val b = 150 + (Math.random() * 60).toInt()
                paint.color = Color.argb(180, 30, 60, b)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawBedrock(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val v = 30 + (Math.random() * 50).toInt()
                paint.color = Color.rgb(v, v, v)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawCoalOre(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val isOre = (px + py * 3) % 7 == 0
                val v = if (isOre) 30 + (Math.random() * 20).toInt() else 100 + (Math.random() * 50).toInt()
                paint.color = Color.rgb(v, v, v)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawIronOre(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val isOre = (px * 2 + py) % 7 == 0
                val v = 100 + (Math.random() * 50).toInt()
                paint.color = if (isOre) Color.rgb(200, 180, 150) else Color.rgb(v, v, v)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawGlass(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val isBorder = px == 0 || px == 15 || py == 0 || py == 15
                paint.color = if (isBorder) Color.argb(255, 200, 220, 240) else Color.argb(60, 200, 230, 255)
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }

    private fun drawBrick(canvas: Canvas, paint: Paint, ox: Int, oy: Int) {
        for (px in 0 until TEX_SIZE) {
            for (py in 0 until TEX_SIZE) {
                val isMortar = py % 4 == 0 || (px + (py / 4) * 8) % 8 == 0
                if (isMortar) {
                    paint.color = Color.rgb(200, 200, 190)
                } else {
                    val v = 150 + (Math.random() * 30).toInt()
                    paint.color = Color.rgb(v, (v * 0.5).toInt(), (v * 0.4).toInt())
                }
                canvas.drawPoint((ox + px).toFloat(), (oy + py).toFloat(), paint)
            }
        }
    }
}
