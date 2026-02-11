package com.cw2.cw_1kito.service.floating

import android.util.Log
import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.CoordinateMode
import com.cw2.cw_1kito.model.TranslationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 流式模式下的单条翻译结果解析器
 *
 * 与非流式模式的 parseTranslationResults() 隔离，
 * 仅共用坐标转换的基础逻辑。
 */
object StreamingResultParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 解析单个 JSON 对象字符串为 TranslationResult
     *
     * @param jsonStr 完整的单个 JSON 对象 (如 {"original_text":..., "coordinates":[...]})
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @param mode 坐标模式（PIXEL 或 NORMALIZED_1000）
     * @return 解析后的 TranslationResult，无效时返回 null
     */
    fun parseOne(
        jsonStr: String,
        screenWidth: Int,
        screenHeight: Int,
        mode: CoordinateMode
    ): TranslationResult? {
        return try {
            val raw = json.decodeFromString<JsonTranslationResult>(jsonStr)
            if (raw.coordinates.size < 4) return null

            val (left, top, right, bottom) = convertCoordinates(
                raw.coordinates, screenWidth, screenHeight, mode
            )

            // 过滤无效框
            val boxWidth = right - left
            val boxHeight = bottom - top
            if (boxWidth <= 0 || boxHeight <= 0) return null
            if (boxWidth < 5 || boxHeight < 5) return null

            // 存为归一化 0-1 坐标
            TranslationResult(
                originalText = raw.original_text,
                translatedText = raw.translated_text,
                boundingBox = BoundingBox(
                    left = left.toFloat() / screenWidth,
                    top = top.toFloat() / screenHeight,
                    right = right.toFloat() / screenWidth,
                    bottom = bottom.toFloat() / screenHeight
                )
            )
        } catch (e: Exception) {
            Log.w("StreamingResultParser", "Failed to parse: ${e.message}")
            null
        }
    }

    /**
     * 根据坐标模式转换原始坐标为像素值
     */
    private fun convertCoordinates(
        coords: List<Float>,
        screenWidth: Int,
        screenHeight: Int,
        mode: CoordinateMode
    ): List<Int> {
        val rawLeft = coords[0]
        val rawTop = coords[1]
        val rawRight = coords[2]
        val rawBottom = coords[3]

        return when (mode) {
            CoordinateMode.NORMALIZED_1000 -> listOf(
                (rawLeft / 1000f * screenWidth).toInt().coerceIn(0, screenWidth),
                (rawTop / 1000f * screenHeight).toInt().coerceIn(0, screenHeight),
                (rawRight / 1000f * screenWidth).toInt().coerceIn(0, screenWidth),
                (rawBottom / 1000f * screenHeight).toInt().coerceIn(0, screenHeight)
            )
            else -> listOf(
                rawLeft.toInt().coerceIn(0, screenWidth),
                rawTop.toInt().coerceIn(0, screenHeight),
                rawRight.toInt().coerceIn(0, screenWidth),
                rawBottom.toInt().coerceIn(0, screenHeight)
            )
        }
    }
}

/**
 * JSON 翻译结果格式（单条）
 */
@Serializable
data class JsonTranslationResult(
    val original_text: String,
    val translated_text: String,
    val coordinates: List<Float>
)
