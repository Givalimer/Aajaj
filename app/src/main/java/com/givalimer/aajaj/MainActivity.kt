package com.givalimer.aajaj

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private lateinit var etPrompt: EditText
    private lateinit var imgResult: ImageView
    private lateinit var placeholderLayout: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var actionButtons: LinearLayout
    private lateinit var btnGenerate: TextView
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvTimer: TextView

    private var currentBitmap: Bitmap? = null
    private var timerJob: Job? = null

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
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        tvTimer = findViewById(R.id.tvTimer)
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

    private fun startTimer() {
        var seconds = 0
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                tvTimer.text = "⏱ $seconds сек"
                when {
                    seconds < 10 -> tvLoadingStatus.text = "Отправляю запрос нейросети..."
                    seconds < 30 -> tvLoadingStatus.text = "Нейросеть рисует изображение..."
                    seconds < 60 -> tvLoadingStatus.text = "Почти готово, ещё немного..."
                    seconds < 90 -> tvLoadingStatus.text = "Сложный запрос, подождите..."
                    else -> tvLoadingStatus.text = "Долгая генерация, ждём ответ..."
                }
                delay(1000)
                seconds++
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun generateImage(prompt: String) {
        showLoading(true)
        startTimer()

        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=${System.currentTimeMillis()}"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 120_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentType = connection.contentType ?: ""

                    if (!contentType.startsWith("image")) {
                        // Сервер вернул не картинку
                        val errorBody = connection.inputStream.bufferedReader().readText()
                        connection.disconnect()
                        withContext(Dispatchers.Main) {
                            stopTimer()
                            showLoading(false)
                            showError("Сервер вернул не картинку!\nТип: $contentType\nОтвет: ${errorBody.take(200)}")
                        }
                        return@launch
                    }

                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    connection.disconnect()

                    if (bitmap != null) {
                        currentBitmap = bitmap
                        withContext(Dispatchers.Main) {
                            stopTimer()
                            imgResult.setImageBitmap(bitmap)
                            showLoading(false)
                            showImage(true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            stopTimer()
                            showLoading(false)
                            showError("Ошибка: не удалось декодировать изображение.\nВозможно сервер вернул пустой ответ.")
                        }
                    }
                } else {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: "нет данных"
                    } catch (e: Exception) { "не удалось прочитать" }
                    connection.disconnect()
                    withContext(Dispatchers.Main) {
                        stopTimer()
                        showLoading(false)
                        showError("Ошибка сервера: HTTP $responseCode\n$errorBody")
                    }
                }
            } catch (e: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    stopTimer()
                    showLoading(false)
                    showError("⏰ Таймаут! Сервер не ответил за 2 минуты.\nПопробуйте короче описание или повторите позже.")
                }
            } catch (e: UnknownHostException) {
                withContext(Dispatchers.Main) {
                    stopTimer()
                    showLoading(false)
                    showError("❌ Нет интернета!\nПроверьте подключение к сети.\n\n${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    stopTimer()
                    showLoading(false)
                    showError("❌ Ошибка: ${e.javaClass.simpleName}\n${e.message}\n\nПопробуйте ещё раз.")
                }
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Также показываем в статусе загрузки на 5 секунд
        tvLoadingStatus.text = message
        tvLoadingStatus.setTextColor(0xFFFF6B6B.toInt())
        loadingOverlay.visibility = View.VISIBLE
        tvTimer.text = "Нажмите 'Создать' чтобы попробовать снова"

        lifecycleScope.launch {
            delay(5000)
            loadingOverlay.visibility = View.GONE
            tvLoadingStatus.setTextColor(0xB3FFFFFF.toInt())
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        btnGenerate.isEnabled = !show
        btnGenerate.alpha = if (show) 0.6f else 1.0f
        if (show) {
            tvLoadingStatus.setTextColor(0xB3FFFFFF.toInt())
        }
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
        val bitmap = currentBitmap ?: return

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
        val bitmap = currentBitmap ?: return

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

    private fun shakeView(view: View) {
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        view.startAnimation(shake)
    }
}
