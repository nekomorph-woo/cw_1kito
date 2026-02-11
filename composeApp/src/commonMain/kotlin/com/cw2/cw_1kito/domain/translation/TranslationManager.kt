package com.cw2.cw_1kito.domain.translation

import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationConfig
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.TranslationResponse
import com.cw2.cw_1kito.model.VlmModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 翻译管理器接口
 *
 * 协调完整的翻译流程，包括截图、API 调用和结果处理。
 */
interface TranslationManager {
    /**
     * 翻译状态流
     */
    val translationState: StateFlow<TranslationState>

    /**
     * 执行翻译
     *
     * @param imageBytes 截图数据
     * @param config 翻译配置
     */
    suspend fun translate(imageBytes: ByteArray, config: TranslationConfig)

    /**
     * 取消当前翻译
     */
    fun cancelTranslation()

    /**
     * 重试上次翻译
     */
    suspend fun retryLastTranslation()

    /**
     * 重置翻译状态
     */
    fun reset()
}

/**
 * 翻译状态
 */
sealed class TranslationState {
    /** 空闲状态 */
    data object Idle : TranslationState()

    /** 处理中 */
    data object Processing : TranslationState()

    /** 成功 */
    data class Success(
        val results: List<TranslationResult>,
        val resultId: String,
        val model: VlmModel
    ) : TranslationState()

    /** 错误 */
    data class Error(val error: TranslationError) : TranslationState()
}

/**
 * 翻译错误
 */
sealed class TranslationError : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)

    /** 没有 API Key */
    data object NoApiKey : TranslationError("未配置 API Key")

    /** 网络不可用 */
    data object NetworkUnavailable : TranslationError("网络连接不可用")

    /** 截图失败 */
    data class CaptureFailed(val reason: String) : TranslationError("截图失败: $reason")

    /** API 错误 */
    data class ApiError(val code: Int, val internalMessage: String) : TranslationError("API 错误 ($code): $internalMessage")

    /** 解析错误 */
    data class ParseError(val internalMessage: String) : TranslationError("解析响应失败: $internalMessage")

    /** 未知错误 */
    data class Unknown(val internalCause: Throwable?) : TranslationError("未知错误: ${internalCause?.message ?: "未知原因"}", internalCause)
}

/**
 * 翻译请求
 */
data class TranslationRequest(
    val imageBytes: ByteArray,
    val config: TranslationConfig
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TranslationRequest

        if (!imageBytes.contentEquals(other.imageBytes)) return false
        return config == other.config
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + config.hashCode()
        return result
    }
}

/**
 * 翻译进度
 */
data class TranslationProgress(
    /** 当前阶段 */
    val stage: ProgressStage,

    /** 进度百分比 (0-100) */
    val percentage: Int,

    /** 状态消息 */
    val message: String
) {
    companion object {
        /** 初始状态 */
        val INITIAL = TranslationProgress(ProgressStage.Idle, 0, "准备就绪")

        /** 开始处理 */
        fun starting() = TranslationProgress(ProgressStage.Processing, 10, "开始处理...")

        /** 截图中 */
        fun capturing() = TranslationProgress(ProgressStage.Capturing, 20, "截取屏幕...")

        /** 上传中 */
        fun uploading() = TranslationProgress(ProgressStage.Uploading, 40, "上传图片...")

        /** 处理中 */
        fun processing() = TranslationProgress(ProgressStage.Processing, 60, "翻译中...")

        /** 处理完成 */
        fun completing() = TranslationProgress(ProgressStage.Completing, 90, "处理结果...")
    }
}

/**
 * 进度阶段
 */
enum class ProgressStage {
    Idle,
    Capturing,
    Uploading,
    Processing,
    Completing
}
