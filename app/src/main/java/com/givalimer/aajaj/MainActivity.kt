package com.givalimer.aajaj

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var etPrompt: EditText
    private lateinit var imgResult: ImageView
    private lateinit var placeholderLayout: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var actionButtons: LinearLayout
    private lateinit var btnGenerate: TextView

    private var currentImageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        setupChips()
    }

    private fun initViews() {
        etPrompt = findViewById(R.id.etPrompt)
        imgResult = findViewById(R.id.imgResult)
        placeholderLayout = findViewById(R.id.placeholderLayout)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        actionButtons = findViewById(R.id.actionButtons)
        btnGenerate = findViewById(R.id.btnGenerate)
    }

    private fun setupClickListeners() {
        btnGenerate.setOnClickListener {
            val prompt = etPrompt.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty), Toast.LENGTH_SHORT).show()
                shakeView(etPrompt)
                return@setOnClickListener
            }
            generateImage(prompt)
        }

        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            saveImage()
        }

        findViewById<TextView>(R.id.btnShare).setOnClickListener {
            shareImage()
        }
    }

    private fun setupChips() {
        val chips = mapOf(
            R.id.chip1 to "A beautiful cosmic landscape with nebulas, stars and planets, ultra detailed, 8k",
            R.id.chip2 to "A majestic dragon sitting on top of misty mountains, fantasy art, cinematic lighting, detailed scales",
            R.id.chip3 to "A futuristic cyberpunk city at night with neon lights, flying cars, rain reflections, ultra detailed",
            R.id.chip4 to "A peaceful Japanese garden with cherry blossoms, koi pond, wooden bridge, soft morning light",
            R.id.chip5 to "A surreal dreamscape painting with melting clocks, floating islands, impossible geometry, vibrant colors"
        )

        chips.forEach { (id, prompt) ->
            findViewById<TextView>(id).setOnClickListener {
                etPrompt.setText(prompt)
            }
        }
    }

    private fun generateImage(prompt: String) {
        showLoading(true)

        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=${System.currentTimeMillis()}"
        currentImageUrl = imageUrl

        lifecycleScope.launch {
            try {
                imgResult.load(imageUrl) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                    listener(
                        onSuccess = { _, _ ->
                            showLoading(false)
                            showImage(true)
                        },
                        onError = { _, _ ->
                            showLoading(false)
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.error_network),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, getString(R.string.error_network), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        btnGenerate.isEnabled = !show
        btnGenerate.alpha = if (show) 0.6f else 1.0f
    }

    private fun showImage(show: Boolean) {
        imgResult.visibility = if (show) View.VISIBLE else View.GONE
        placeholderLayout.visibility = if (show) View.GONE else View.VISIBLE
        actionButtons.visibility = if (show) View.VISIBLE else View.GONE

        if (show) {
            imgResult.alpha = 0f
            imgResult.animate().alpha(1f).setDuration(500).start()
            actionButtons.alpha = 0f
            actionButtons.animate().alpha(1f).setDuration(300).setStartDelay(200).start()
        }
    }

    private fun saveImage() {
        val bitmap = getBitmapFromImageView() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "ai_art_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Art")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        }
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Art")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, "ai_art_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareImage() {
        val bitmap = getBitmapFromImageView() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cachePath = File(cacheDir, "shared_images")
                cachePath.mkdirs()
                val file = File(cachePath, "ai_art_share.png")
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    file
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Поделиться изображением"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getBitmapFromImageView(): Bitmap? {
        val drawable = imgResult.drawable ?: return null
        return (drawable as? BitmapDrawable)?.bitmap
    }

    private fun shakeView(view: View) {
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        view.startAnimation(shake)
    }
}
