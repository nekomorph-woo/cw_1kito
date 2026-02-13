package com.cw2.cw_1kito.util

import com.cw2.cw_1kito.error.AppError

/**
 * 通用结果封装
 * 用于包装可能失败的操作结果
 */
sealed class AppResult<out T> {
    /**
     * 成功结果
     */
    data class Success<T>(val data: T) : AppResult<T>()

    /**
     * 错误结果
     */
    data class Error(val error: AppError) : AppResult<Nothing>()

    /**
     * 检查是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 检查是否失败
     */
    val isError: Boolean get() = this is Error

    /**
     * 获取数据（如果失败则返回 null）
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * 获取数据（如果失败则抛出异常）
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error
    }

    /**
     * 映射成功值
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /**
     * 扁平映射
     */
    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    /**
     * 获取数据或默认值
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> defaultValue
    }

    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * 失败时执行操作
     */
    inline fun onError(action: (AppError) -> Unit): AppResult<T> {
        if (this is Error) {
            action(error)
        }
        return this
    }

    companion object {
        /**
         * 捕获异常并转换为 Error
         */
        inline fun <T> catch(action: () -> T): AppResult<T> = try {
            Success(action())
        } catch (e: AppError) {
            Error(e)
        } catch (e: Exception) {
            Error(AppError.SystemError(
                code = com.cw2.cw_1kito.error.SystemErrorCode.UNKNOWN_ERROR,
                cause = e
            ))
        }
    }
}

/**
 * OCR 操作结果
 */
sealed class OcrResult {
    /**
     * OCR 成功
     * @param detections 检测到的文本列表
     * @param latencyMs OCR 耗时（毫秒）
     */
    data class Success(
        val detections: List<com.cw2.cw_1kito.model.OcrDetection>,
        val latencyMs: Long
    ) : OcrResult()

    /**
     * OCR 失败
     * @param error 错误信息
     */
    data class Error(
        val error: AppError.OcrError,
        val latencyMs: Long = 0
    ) : OcrResult()

    /**
     * 检查是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 检查是否失败
     */
    val isError: Boolean get() = this is Error

    /**
     * 获取检测结果
     */
    fun getDetectionsOrNull(): List<com.cw2.cw_1kito.model.OcrDetection>? = when (this) {
        is Success -> detections
        is Error -> null
    }
}

/**
 * 翻译操作结果
 */
sealed class TranslateResult {
    /**
     * 翻译成功
     * @param translatedText 翻译后的文本
     * @param latencyMs 翻译耗时（毫秒）
     */
    data class Success(
        val translatedText: String,
        val latencyMs: Long
    ) : TranslateResult()

    /**
     * 翻译失败
     * @param code 错误码
     * @param message 错误消息
     */
    data class Error(
        val code: com.cw2.cw_1kito.error.TranslationErrorCode,
        val message: String,
        val latencyMs: Long = 0
    ) : TranslateResult()

    /**
     * 检查是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 检查是否失败
     */
    val isError: Boolean get() = this is Error

    /**
     * 获取翻译文本
     */
    fun getTextOrNull(): String? = when (this) {
        is Success -> translatedText
        is Error -> null
    }
}

/**
 * 将 OcrResult 转换为 AppResult
 */
fun OcrResult.toAppResult(): AppResult<List<com.cw2.cw_1kito.model.OcrDetection>> = when (this) {
    is OcrResult.Success -> AppResult.Success(detections)
    is OcrResult.Error -> AppResult.Error(error)
}

/**
 * 将 TranslateResult 转换为 AppResult
 */
fun TranslateResult.toAppResult(): AppResult<String> = when (this) {
    is TranslateResult.Success -> AppResult.Success(translatedText)
    is TranslateResult.Error -> AppResult.Error(
        AppError.TranslationError(code, null)
    )
}

/**
 * 扩展函数：将 Throwable 转换为 AppError
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.net.UnknownHostException, is java.net.ConnectException ->
        AppError.TranslationError(
            com.cw2.cw_1kito.error.TranslationErrorCode.TRANSLATION_NETWORK_ERROR,
            cause = this
        )
    is java.net.SocketTimeoutException ->
        AppError.TranslationError(
            com.cw2.cw_1kito.error.TranslationErrorCode.TRANSLATION_TIMEOUT,
            cause = this
        )
    is OutOfMemoryError ->
        AppError.SystemError(
            com.cw2.cw_1kito.error.SystemErrorCode.OUT_OF_MEMORY,
            cause = this
        )
    else ->
        AppError.SystemError(
            com.cw2.cw_1kito.error.SystemErrorCode.UNKNOWN_ERROR,
            cause = this
        )
}
