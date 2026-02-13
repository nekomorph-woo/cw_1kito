package com.cw2.cw_1kito.util

import com.cw2.cw_1kito.error.AppError

/**
 * 日志工具类
 * 提供统一的日志接口，支持 Timber 和文件日志
 */
object Logger {

    const val TAG = "KitoOCR"

    /**
     * 当前日志级别
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    /**
     * 设置最低日志级别
     */
    var minLevel: Level = Level.DEBUG

    /**
     * DEBUG 日志
     */
    fun d(message: String, vararg args: Any?) {
        if (minLevel <= Level.DEBUG) {
            println("D/$TAG: ${message.format(*args)}")
        }
    }

    /**
     * DEBUG 日志（带异常）
     */
    fun d(throwable: Throwable, message: String, vararg args: Any?) {
        if (minLevel <= Level.DEBUG) {
            println("D/$TAG: ${message.format(*args)}")
            println(throwable.stackTraceToString())
        }
    }

    /**
     * INFO 日志
     */
    fun i(message: String, vararg args: Any?) {
        if (minLevel <= Level.INFO) {
            println("I/$TAG: ${message.format(*args)}")
        }
    }

    /**
     * INFO 日志（带异常）
     */
    fun i(throwable: Throwable, message: String, vararg args: Any?) {
        if (minLevel <= Level.INFO) {
            println("I/$TAG: ${message.format(*args)}")
            println(throwable.stackTraceToString())
        }
    }

    /**
     * WARN 日志
     */
    fun w(message: String, vararg args: Any?) {
        if (minLevel <= Level.WARN) {
            println("W/$TAG: ${message.format(*args)}")
        }
    }

    /**
     * WARN 日志（带异常）
     */
    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        if (minLevel <= Level.WARN) {
            println("W/$TAG: ${message.format(*args)}")
            println(throwable.stackTraceToString())
        }
    }

    /**
     * ERROR 日志
     */
    fun e(message: String, vararg args: Any?) {
        if (minLevel <= Level.ERROR) {
            println("E/$TAG: ${message.format(*args)}")
        }
    }

    /**
     * ERROR 日志（带异常）
     */
    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        if (minLevel <= Level.ERROR) {
            println("E/$TAG: ${message.format(*args)}")
            println(throwable.stackTraceToString())
        }
    }

    /**
     * 记录 AppError
     */
    fun error(error: AppError, message: String? = null) {
        when (error) {
            is AppError.OcrError -> {
                e(error, message ?: "OCR Error: [${error.code}] ${error.code.message}")
            }
            is AppError.TranslationError -> {
                e(error, message ?: "Translation Error: [${error.code}] ${error.code.message}")
            }
            is AppError.SystemError -> {
                e(error, message ?: "System Error: [${error.code}] ${error.code.message}")
            }
        }
    }

    /**
     * 性能日志
     * @param ocrMs OCR 耗时（毫秒）
     * @param mergeMs 文本合并耗时（毫秒）
     * @param translateMs 翻译耗时（毫秒）
     * @param totalMs 总耗时（毫秒）
     */
    fun logPerformance(
        ocrMs: Long,
        mergeMs: Long = 0,
        translateMs: Long,
        totalMs: Long
    ) {
        val parts = mutableListOf<String>()
        parts.add("OCR=${ocrMs}ms")
        if (mergeMs > 0) parts.add("Merge=${mergeMs}ms")
        parts.add("Translate=${translateMs}ms")
        parts.add("Total=${totalMs}ms")

        i("[Performance] ${parts.joinToString(", ")}")
    }

    /**
     * OCR 操作日志
     */
    fun ocrStart() {
        d("[OCR] Starting OCR recognition...")
    }

    fun ocrSuccess(boxCount: Int, latencyMs: Long) {
        i("[OCR] Recognized $boxCount boxes in ${latencyMs}ms")
    }

    fun ocrError(error: AppError.OcrError, latencyMs: Long = 0) {
        e(error, "[OCR] Failed after ${latencyMs}ms")
    }

    /**
     * 翻译操作日志
     */
    fun translationStart(segmentCount: Int) {
        d("[Translation] Starting translation of $segmentCount segments...")
    }

    fun translationStart(textLength: Int, sourceLang: String, targetLang: String) {
        d("[Translation] Translating $textLength chars from $sourceLang to $targetLang...")
    }

    fun translationSuccess(segmentCount: Int, latencyMs: Long) {
        i("[Translation] Translated $segmentCount segments in ${latencyMs}ms")
    }

    fun translationError(error: AppError.TranslationError, latencyMs: Long = 0) {
        e(error, "[Translation] Failed after ${latencyMs}ms")
    }

    /**
     * 文本合并日志
     */
    fun mergeStart(detectionCount: Int) {
        d("[Merge] Starting merge of $detectionCount detections...")
    }

    fun mergeSuccess(originalCount: Int, mergedCount: Int, latencyMs: Long) {
        i("[Merge] Merged $originalCount detections into $mergedCount segments in ${latencyMs}ms")
    }

    fun mergeWarning(message: String) {
        w("[Merge] $message")
    }

    /**
     * 覆盖层日志
     */
    fun overlayCreated(resultCount: Int) {
        d("[Overlay] Created overlay with $resultCount results")
    }

    fun overlayDismissed() {
        d("[Overlay] Overlay dismissed by user")
    }

    /**
     * 权限日志
     */
    fun permissionRequest(permission: String) {
        d("[Permission] Requesting $permission")
    }

    fun permissionGranted(permission: String) {
        i("[Permission] Granted: $permission")
    }

    fun permissionDenied(permission: String) {
        w("[Permission] Denied: $permission")
    }

    /**
     * 配置日志
     */
    fun configChanged(key: String, value: Any?) {
        d("[Config] $key = $value")
    }

    /**
     * 缓存命中
     */
    fun cacheHit() {
        d("[Cache] Cache hit")
    }

    /**
     * 缓存未命中
     */
    fun cacheMiss() {
        d("[Cache] Cache miss")
    }

    /**
     * 调试日志
     */
    fun debug(message: String) {
        d("[Debug] $message")
    }

    /**
     * 格式化字符串（简单实现）
     */
    private fun String.format(vararg args: Any?): String {
        var result = this
        args.forEach { arg ->
            result = result.replaceFirst("%s", arg?.toString() ?: "null")
            result = result.replaceFirst("%d", arg?.toString() ?: "null")
        }
        return result
    }
}

/**
 * Timber 日志工具（仅在 Android 平台可用）
 * 这是一个平台特定的实现，在 commonMain 中使用 expect/actual
 */
expect object TimberLogger {

    fun d(tag: String, message: String, vararg args: Any?)

    fun i(tag: String, message: String, vararg args: Any?)

    fun w(tag: String, message: String, vararg args: Any?)

    fun e(tag: String, message: String, vararg args: Any?)

    fun e(tag: String, throwable: Throwable, message: String, vararg args: Any?)
}

/**
 * 性能统计数据
 */
data class PerformanceMetrics(
    val ocrMs: Long = 0,
    val mergeMs: Long = 0,
    val translateMs: Long = 0,
    val totalMs: Long = 0
) {
    /**
     * 转换为 CSV 格式（用于性能日志文件）
     */
    fun toCsv(): String {
        return "${System.currentTimeMillis()},$ocrMs,$mergeMs,$translateMs,$totalMs"
    }

    /**
     * 获取可读的字符串表示
     */
    fun toReadableString(): String {
        val parts = mutableListOf<String>()
        if (ocrMs > 0) parts.add("OCR=${ocrMs}ms")
        if (mergeMs > 0) parts.add("Merge=${mergeMs}ms")
        if (translateMs > 0) parts.add("Translate=${translateMs}ms")
        parts.add("Total=${totalMs}ms")
        return parts.joinToString(", ")
    }

    companion object {
        /**
         * CSV 文件头
         */
        const val CSV_HEADER = "timestamp,ocr_ms,merge_ms,translate_ms,total_ms"

        /**
         * 从 CSV 行解析
         */
        fun fromCsv(line: String): PerformanceMetrics? {
            val parts = line.split(",")
            if (parts.size != 5) return null

            return try {
                PerformanceMetrics(
                    ocrMs = parts[1].toLongOrNull() ?: 0,
                    mergeMs = parts[2].toLongOrNull() ?: 0,
                    translateMs = parts[3].toLongOrNull() ?: 0,
                    totalMs = parts[4].toLongOrNull() ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 扩展函数：记录操作耗时
 */
inline fun <T> logPerformance(
    operationName: String,
    block: () -> T
): T {
    val startTime = System.currentTimeMillis()
    return block().also { result ->
        val elapsed = System.currentTimeMillis() - startTime
        Logger.i("[$operationName] Completed in ${elapsed}ms")
    }
}

/**
 * 扩展函数：记录操作耗时（带结果）
 */
inline fun <T> logPerformanceWithResult(
    operationName: String,
    block: () -> T
): Pair<T, Long> {
    val startTime = System.currentTimeMillis()
    val result = block()
    val elapsed = System.currentTimeMillis() - startTime
    Logger.i("[$operationName] Completed in ${elapsed}ms")
    return result to elapsed
}

/**
 * 扩展函数：记录可能失败的操作
 */
inline fun <T> logOperation(
    operationName: String,
    block: () -> AppResult<T>
): AppResult<T> {
    Logger.d("[$operationName] Starting...")
    return try {
        block().also { result ->
            when (result) {
                is AppResult.Success -> Logger.i("[$operationName] Success")
                is AppResult.Error -> Logger.error(result.error, "[$operationName] Failed")
            }
        }
    } catch (e: Exception) {
        val error = e.toAppError()
        Logger.error(error, "[$operationName] Crashed")
        AppResult.Error(error)
    }
}
