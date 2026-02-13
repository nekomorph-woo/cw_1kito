package com.cw2.cw_1kito.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cw2.cw_1kito.model.OcrDetection
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.PerformanceMode.BALANCED
import com.cw2.cw_1kito.model.PerformanceMode.FAST
import com.cw2.cw_1kito.model.PerformanceMode.QUALITY
import com.cw2.cw_1kito.error.OcrInitializationException
import com.cw2.cw_1kito.error.OcrModelNotLoadedException
import com.cw2.cw_1kito.error.OcrRuntimeException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit OCR 引擎管理器
 *
 * 基于 Google ML Kit Text Recognition API，实现高效的本地 OCR 识别。
 *
 * ## 核心特性
 * - **零配置**: 无需下载模型文件，开箱即用
 * - **高性能**: 移动端 CPU 推理速度 <400ms（平衡模式）
 * - **多语言**: 支持中文、日文、韩文、英文等多种语言
 * - **低内存**: 占用更少内存
 * - **设备端**: 所有识别在设备上完成，保护隐私
 *
 * ## 性能模式
 * - **FAST**: 极速模式，短边 672px，<150ms
 * - **BALANCED**: 平衡模式，短边 896px，<250ms（推荐）
 * - **QUALITY**: 高精模式，短边 1080px，<400ms
 *
 * @property context 应用上下文
 * @property performanceMode 性能模式（默认 BALANCED）
 * @property language OCR 语言（默认中文）
 */
class MLKitOCRManager(
    private val context: Context,
    private var performanceMode: PerformanceMode = BALANCED,
    private var language: OcrLanguage = OcrLanguage.CHINESE
) : IOcrEngine {

    companion object {
        private const val TAG = "MLKitOCRManager"
        private const val ENGINE_VERSION = "ML-Kit-Text-Recognition-v2"
    }

    private var textRecognizer: TextRecognizer? = null

    @Volatile
    private var initialized: Boolean = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            Log.w(TAG, "OCR engine already initialized")
            return@withContext true
        }

        try {
            Log.d(TAG, "Initializing MLKit OCR engine...")
            Log.d(TAG, "Language: ${language.displayName}")
            Log.d(TAG, "Performance mode: $performanceMode")

            // 根据语言选择对应的识别器
            textRecognizer = when (language) {
                OcrLanguage.CHINESE -> {
                    Log.d(TAG, "Using Chinese text recognizer")
                    TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                }
                OcrLanguage.JAPANESE -> {
                    Log.d(TAG, "Using Japanese text recognizer")
                    TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                }
                OcrLanguage.KOREAN -> {
                    Log.d(TAG, "Using Korean text recognizer")
                    TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                }
                OcrLanguage.LATIN -> {
                    Log.d(TAG, "Using Latin text recognizer")
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                }
            }

            initialized = true
            Log.i(TAG, "MLKit OCR engine initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            initialized = false
            throw OcrInitializationException("初始化失败: ${e.message}", e)
        }
    }

    override suspend fun recognize(bitmap: Bitmap): List<OcrDetection> =
        recognizeInternal(bitmap)

    override suspend fun recognizeWithAngle(bitmap: Bitmap): List<OcrDetection> =
        recognizeInternal(bitmap)

    private suspend fun recognizeInternal(bitmap: Bitmap): List<OcrDetection> =
        withContext(Dispatchers.Default) {
            if (!initialized || textRecognizer == null) {
                throw OcrModelNotLoadedException("OCR 引擎未初始化，请先调用 initialize()")
            }

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                throw OcrRuntimeException("无效图像尺寸: ${bitmap.width}x${bitmap.height}")
            }

            try {
                Log.d(TAG, "Recognizing image...")
                Log.d(TAG, "Original bitmap: ${bitmap.width}x${bitmap.height}")

                val processedBitmap = preprocessBitmap(bitmap)
                Log.d(TAG, "Processed bitmap: ${processedBitmap.width}x${processedBitmap.height}")

                val image = InputImage.fromBitmap(processedBitmap, 0)

                val startTime = System.currentTimeMillis()
                val text = textRecognizer!!.process(image).await()
                val elapsed = System.currentTimeMillis() - startTime

                Log.d(TAG, "Recognition completed in ${elapsed}ms")

                val results = parseTextResult(text, processedBitmap)

                Log.d(TAG, "Found ${results.size} text blocks")

                if (processedBitmap != bitmap) {
                    processedBitmap.recycle()
                }

                results
            } catch (e: OcrModelNotLoadedException) {
                throw e
            } catch (e: OcrRuntimeException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                throw OcrRuntimeException("识别失败: ${e.message}", e)
            }
        }

    private fun parseTextResult(text: Text, bitmap: Bitmap): List<OcrDetection> {
        val results = mutableListOf<OcrDetection>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox ?: continue

                val rect = com.cw2.cw_1kito.model.RectF(
                    left = boundingBox.left.toFloat(),
                    top = boundingBox.top.toFloat(),
                    right = boundingBox.right.toFloat(),
                    bottom = boundingBox.bottom.toFloat()
                )

                val detection = OcrDetection(
                    text = line.text,
                    boundingBox = rect,
                    confidence = 1.0f,
                    angle = null
                )

                results.add(detection)
                Log.v(TAG, "Detected: \"${line.text}\" at $rect")
            }
        }

        return results
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val needConvert = bitmap.config != Bitmap.Config.ARGB_8888
        val needScale = shouldScaleImage(bitmap)

        if (!needConvert && !needScale) {
            return bitmap
        }

        val targetWidth: Int
        val targetHeight: Int

        if (needScale) {
            val targetShortSide = when (performanceMode) {
                FAST -> 672
                BALANCED -> 896
                QUALITY -> 1080
            }
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            if (bitmap.width < bitmap.height) {
                targetHeight = targetShortSide
                targetWidth = (targetShortSide * aspectRatio).toInt()
            } else {
                targetWidth = targetShortSide
                targetHeight = (targetShortSide / aspectRatio).toInt()
            }
        } else {
            targetWidth = bitmap.width
            targetHeight = bitmap.height
        }

        val scaledBitmap = if (needScale) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val convertedBitmap = if (needConvert) {
            scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
                .also { if (it != scaledBitmap && needScale) scaledBitmap.recycle() }
        } else {
            scaledBitmap
        }

        return convertedBitmap
    }

    private fun shouldScaleImage(bitmap: Bitmap): Boolean {
        val shortSide = minOf(bitmap.width, bitmap.height)
        val targetSize = when (performanceMode) {
            FAST -> 672
            BALANCED -> 896
            QUALITY -> 1080
        }
        return shortSide > targetSize * 1.1f || shortSide < targetSize * 0.9f
    }

    override fun release() {
        if (textRecognizer != null) {
            Log.d(TAG, "Releasing OCR engine...")
            try {
                textRecognizer!!.close()
                Log.i(TAG, "OCR engine released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release OCR engine", e)
            } finally {
                textRecognizer = null
                initialized = false
            }
        }
    }

    override fun isInitialized(): Boolean = initialized && textRecognizer != null

    override fun getVersion(): String = if (initialized) ENGINE_VERSION else "not initialized"

    fun setPerformanceMode(mode: PerformanceMode) {
        Log.d(TAG, "Performance mode changed: $performanceMode -> $mode")
        performanceMode = mode
    }

    fun getPerformanceMode(): PerformanceMode = performanceMode

    fun setLanguage(lang: OcrLanguage) {
        Log.d(TAG, "Language changed: ${language.displayName} -> ${lang.displayName}")
        if (language != lang) {
            language = lang
            // 语言变更需要重新初始化识别器
            release()
            initialized = false
        }
    }

    fun getLanguage(): OcrLanguage = language
}

// 扩展函数：将 Task 转换为协程
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
