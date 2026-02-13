package com.cw2.cw_1kito.error

/**
 * 应用错误基类
 * 所有应用级错误都继承自此密封类
 */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * OCR 错误
     */
    open class OcrError(
        val code: OcrErrorCode,
        val details: Map<String, Any>? = null,
        cause: Throwable? = null
    ) : AppError(code.message, cause) {
        /**
         * 获取详细错误信息
         */
        fun getDetailedMessage(): String {
            val sb = StringBuilder("[$code] ${code.message}")
            details?.forEach { (key, value) ->
                sb.append("\n  $key: $value")
            }
            cause?.let {
                sb.append("\nCaused by: ${it.message}")
            }
            return sb.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OcrError) return false
            return code == other.code && details == other.details && cause == other.cause
        }

        override fun hashCode(): Int {
            var result = code.hashCode()
            result = 31 * result + (details?.hashCode() ?: 0)
            result = 31 * result + (cause?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * 翻译错误
     */
    open class TranslationError(
        val code: TranslationErrorCode,
        val details: Map<String, Any>? = null,
        cause: Throwable? = null
    ) : AppError(code.message, cause) {
        /**
         * 获取详细错误信息
         */
        fun getDetailedMessage(): String {
            val sb = StringBuilder("[$code] ${code.message}")
            details?.forEach { (key, value) ->
                sb.append("\n  $key: $value")
            }
            cause?.let {
                sb.append("\nCaused by: ${it.message}")
            }
            return sb.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TranslationError) return false
            return code == other.code && details == other.details && cause == other.cause
        }

        override fun hashCode(): Int {
            var result = code.hashCode()
            result = 31 * result + (details?.hashCode() ?: 0)
            result = 31 * result + (cause?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * 系统错误
     */
    open class SystemError(
        val code: SystemErrorCode,
        val details: Map<String, Any>? = null,
        cause: Throwable? = null
    ) : AppError(code.message, cause) {
        /**
         * 获取详细错误信息
         */
        fun getDetailedMessage(): String {
            val sb = StringBuilder("[$code] ${code.message}")
            details?.forEach { (key, value) ->
                sb.append("\n  $key: $value")
            }
            cause?.let {
                sb.append("\nCaused by: ${it.message}")
            }
            return sb.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SystemError) return false
            return code == other.code && details == other.details && cause == other.cause
        }

        override fun hashCode(): Int {
            var result = code.hashCode()
            result = 31 * result + (details?.hashCode() ?: 0)
            result = 31 * result + (cause?.hashCode() ?: 0)
            return result
        }
    }
}

// ==================== 具体异常类 ====================

/**
 * OCR 初始化异常
 */
class OcrInitializationException(
    message: String = "OCR 模型初始化失败",
    cause: Throwable? = null
) : AppError.OcrError(OcrErrorCode.OCR_INIT_FAILED, null, cause)

/**
 * OCR 运行时异常
 */
class OcrRuntimeException(
    message: String = "OCR 推理运行时错误",
    cause: Throwable? = null
) : AppError.OcrError(OcrErrorCode.OCR_RUNTIME_ERROR, null, cause)

/**
 * OCR 无效图像异常
 */
class OcrInvalidImageException(
    message: String = "无效图像输入",
    cause: Throwable? = null
) : AppError.OcrError(OcrErrorCode.OCR_INVALID_IMAGE, null, cause)

/**
 * OCR 模型未加载异常
 */
class OcrModelNotLoadedException(
    message: String = "OCR 模型未加载"
) : AppError.OcrError(OcrErrorCode.OCR_MODEL_NOT_LOADED, null, null)

// ==================== 翻译异常类 ====================

/**
 * 翻译初始化异常
 */
class TranslationException(
    message: String = "翻译引擎初始化失败",
    cause: Throwable? = null
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_INIT_FAILED, null, cause)

/**
 * 本地翻译失败异常
 */
class LocalTranslationFailedException(
    message: String = "本地翻译失败",
    cause: Throwable? = null
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_LOCAL_FAILED, null, cause)

/**
 * 远程翻译失败异常
 */
class RemoteTranslationFailedException(
    message: String = "远程翻译失败",
    cause: Throwable? = null
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_REMOTE_FAILED, null, cause)

/**
 * 翻译超时异常
 */
class TranslationTimeoutException(
    message: String = "翻译超时"
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_TIMEOUT)

/**
 * API Key 无效异常
 */
class ApiKeyInvalidException(
    message: String = "API Key 无效"
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_API_KEY_INVALID)

/**
 * 配额超限异常
 */
class RateLimitException(
    message: String = "API 配额超限"
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_RATE_LIMIT_EXCEEDED)

/**
 * 网络异常
 */
class NetworkException(
    message: String = "网络错误",
    cause: Throwable? = null
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_NETWORK_ERROR, null, cause)

/**
 * 不支持的语言对异常
 */
class UnsupportedLanguageException(
    message: String = "不支持的语言对"
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_UNSUPPORTED_LANGUAGE)

/**
 * 翻译模型不可用异常
 */
class TranslationModelNotAvailableException(
    message: String = "翻译模型不可用"
) : AppError.TranslationError(TranslationErrorCode.TRANSLATION_MODEL_NOT_AVAILABLE)

// ==================== 系统异常类 ====================

/**
 * 权限拒绝异常
 */
class PermissionDeniedException(
    message: String = "权限被拒绝",
    cause: Throwable? = null
) : AppError.SystemError(SystemErrorCode.PERMISSION_DENIED, null, cause)

/**
 * 权限过期异常
 */
class PermissionExpiredException(
    message: String = "权限已过期"
) : AppError.SystemError(SystemErrorCode.PERMISSION_EXPIRED)

/**
 * 存储空间不足异常
 */
class StorageInsufficientException(
    message: String = "存储空间不足"
) : AppError.SystemError(SystemErrorCode.STORAGE_INSUFFICIENT)

/**
 * 内存不足异常
 */
class OutOfMemoryException(
    message: String = "内存不足",
    cause: Throwable? = null
) : AppError.SystemError(SystemErrorCode.OUT_OF_MEMORY, null, cause)

/**
 * 屏幕截图失败异常
 */
class ScreenCaptureFailedException(
    message: String = "屏幕截图失败",
    cause: Throwable? = null
) : AppError.SystemError(SystemErrorCode.SCREEN_CAPTURE_FAILED, null, cause)

/**
 * 服务未运行异常
 */
class ServiceNotRunningException(
    message: String = "服务未运行"
) : AppError.SystemError(SystemErrorCode.SERVICE_NOT_RUNNING)
