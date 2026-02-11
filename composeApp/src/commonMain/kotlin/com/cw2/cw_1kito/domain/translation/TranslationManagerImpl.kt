package com.cw2.cw_1kito.domain.translation

import com.cw2.cw_1kito.domain.coordinate.CoordinateValidator
import com.cw2.cw_1kito.domain.coordinate.CoordinateValidatorImpl
import com.cw2.cw_1kito.domain.layout.TextLayoutEngine
import com.cw2.cw_1kito.domain.layout.TextLayoutEngineImpl
import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationConfig
import com.cw2.cw_1kito.model.TranslationResponse
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.VlmModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 翻译管理器实现
 *
 * 协调完整的翻译流程：
 * 1. 接收截图数据
 * 2. 调用 API 翻译
 * 3. 验证和调整坐标
 * 4. 计算文本布局
 * 5. 返回结果
 */
class TranslationManagerImpl(
    private val coordinateValidator: CoordinateValidator = CoordinateValidatorImpl(),
    private val textLayoutEngine: TextLayoutEngine = TextLayoutEngineImpl()
) : TranslationManager {

    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    override val translationState: StateFlow<TranslationState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var lastRequest: TranslationRequest? = null
    private var lastResults: List<TranslationResult>? = null

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun translate(
        imageBytes: ByteArray,
        config: TranslationConfig
    ) {
        // 检查是否正在处理
        if (_state.value is TranslationState.Processing) {
            return
        }

        // 保存请求用于重试
        lastRequest = TranslationRequest(imageBytes, config)

        // 更新状态为处理中
        _state.value = TranslationState.Processing

        try {
            // 注意：实际实现需要依赖 TranslationApiClient
            // 这里提供框架，具体的 API 调用需要在使用时注入
            val response = performTranslation(imageBytes, config)

            // 处理结果
            val processedResults = processResults(response, config)

            // 保存结果用于重试
            lastResults = processedResults

            // 更新状态为成功
            _state.value = TranslationState.Success(
                results = processedResults,
                resultId = response.resultId,
                model = response.model
            )

        } catch (e: CancellationException) {
            _state.value = TranslationState.Idle
            throw e
        } catch (e: Exception) {
            val error = when (e) {
                is TranslationError -> e
                else -> TranslationError.Unknown(e)
            }
            _state.value = TranslationState.Error(error)
        }
    }

    override fun cancelTranslation() {
        currentJob?.cancel()
        currentJob = null
        _state.value = TranslationState.Idle
    }

    override suspend fun retryLastTranslation() {
        val request = lastRequest
        if (request != null) {
            translate(request.imageBytes, request.config)
        } else {
            _state.value = TranslationState.Error(
                TranslationError.Unknown(null)
            )
        }
    }

    override fun reset() {
        cancelTranslation()
        lastRequest = null
        lastResults = null
        _state.value = TranslationState.Idle
    }

    /**
     * 执行翻译（需要注入 API 客户端）
     *
     * 注意：这是一个占位实现。实际使用时需要注入 TranslationApiClient。
     * 可以通过继承此类并重写此方法，或使用依赖注入。
     */
    open suspend fun performTranslation(
        imageBytes: ByteArray,
        config: TranslationConfig
    ): TranslationResponse {
        // 占位实现 - 返回模拟响应
        // 实际实现需要调用 TranslationApiClient

        // 模拟网络延迟
        delay(100)

        // 生成唯一 ID
        @OptIn(ExperimentalUuidApi::class)
        val resultId = Uuid.random().toString()

        // 返回空响应（实际应该调用 API）
        return TranslationResponse(
            results = emptyList(),
            resultId = resultId,
            model = config.model,
            usage = null
        )
    }

    /**
     * 处理翻译结果
     *
     * 1. 验证坐标
     * 2. 过滤无效结果
     * 3. 计算文本布局
     */
    private suspend fun processResults(
        response: TranslationResponse,
        config: TranslationConfig,
        screenWidth: Int = 1080,
        screenHeight: Int = 1920
    ): List<TranslationResult> {
        return withContext(Dispatchers.Default) {
            val results = response.results

            // 1. 验证和调整坐标
            val boundingBoxes = results.map { it.boundingBox }
            val validatedBoxes = coordinateValidator.validateAndAdjustAll(
                boxes = boundingBoxes,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )

            // 2. 组合结果
            results.mapIndexed { index, result ->
                val validatedBox = validatedBoxes.getOrNull(index) ?: result.boundingBox

                // 可以在这里计算文本布局（如果需要）
                // val layout = textLayoutEngine.calculateLayout(...)

                result.copy(boundingBox = validatedBox)
            }.filter { it.isValid }
        }
    }
}

/**
 * 带有 API 客户端的翻译管理器
 *
 * 使用示例：
 * ```kotlin
 * val manager = TranslationManagerWithApiClient(
 *     apiClient = myApiClient,
 *     coordinateValidator = CoordinateValidatorImpl(),
 *     textLayoutEngine = TextLayoutEngineImpl()
 * )
 * ```
 */
class TranslationManagerWithApiClient(
    private val apiClient: TranslationApiClient,
    coordinateValidator: CoordinateValidator = CoordinateValidatorImpl(),
    textLayoutEngine: TextLayoutEngine = TextLayoutEngineImpl()
) : TranslationManager {

    private val delegate = TranslationManagerImpl(coordinateValidator, textLayoutEngine)

    override val translationState = delegate.translationState

    override suspend fun translate(imageBytes: ByteArray, config: TranslationConfig) {
        // 注入 API 客户端的实际实现
        delegate.translateWithApiClient(imageBytes, config, apiClient)
    }

    override fun cancelTranslation() {
        delegate.cancelTranslation()
    }

    override suspend fun retryLastTranslation() {
        delegate.retryLastTranslation()
    }

    override fun reset() {
        delegate.reset()
    }
}

/**
 * 扩展函数：使用 API 客户端执行翻译
 */
private suspend fun TranslationManagerImpl.translateWithApiClient(
    imageBytes: ByteArray,
    config: TranslationConfig,
    apiClient: TranslationApiClient
) {
    // 调用内部方法并注入 API 客户端
    // 这里需要修改 TranslationManagerImpl 以支持注入 API 客户端
    // 暂时使用占位实现

    // 检查是否正在处理
    if (translationState.value is TranslationState.Processing) {
        return
    }

    // 实际实现需要调用 apiClient.translate()
    // 这里是简化版本
}

/**
 * API 客户端接口（简化版）
 *
 * 实际使用时应该是 data 层的接口
 */
interface TranslationApiClient {
    suspend fun translate(request: TranslationApiRequest): TranslationResponse
}

/**
 * 翻译 API 请求
 */
data class TranslationApiRequest(
    val model: VlmModel,
    val imageData: String,        // Base64 编码的图像
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)
