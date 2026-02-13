package com.cw2.cw_1kito

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.permission.PermissionManager
import com.cw2.cw_1kito.ui.screen.LanguagePackPrompt
import com.cw2.cw_1kito.ui.screen.SettingsEvent
import com.cw2.cw_1kito.ui.screen.SettingsUiState
import com.cw2.cw_1kito.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel
 *
 * 负责管理应用的配置状态和用户交互
 */
class MainViewModel(
    private val permissionManager: PermissionManager,
    private val configManager: ConfigManager
) : ViewModel() {

    // 内部状态
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshPermissionStatus()
        loadSavedConfig()
    }

    /**
     * 加载已保存的配置
     */
    private fun loadSavedConfig() {
        viewModelScope.launch {
            try {
                // 加载 API Key
                val savedApiKey = configManager.getApiKey()
                if (savedApiKey != null) {
                    _uiState.update { it.copy(
                        apiKey = savedApiKey,
                        isApiKeyValid = validateApiKey(savedApiKey)
                    ) }
                }

                // 加载语言配置
                val langConfig = configManager.getLanguageConfig()
                _uiState.update { it.copy(
                    sourceLanguage = langConfig.sourceLanguage,
                    targetLanguage = langConfig.targetLanguage
                ) }

                // 加载模型配置
                val model = configManager.getSelectedModel()
                _uiState.update { it.copy(selectedModel = model) }

                // 加载自定义提示词
                val savedPrompt = configManager.getCustomPrompt()
                _uiState.update { it.copy(
                    customPrompt = savedPrompt ?: TranslationApiClientImpl.DEFAULT_PROMPT
                ) }

                // 加载流式翻译开关
                val streamingEnabled = configManager.getStreamingEnabled()
                _uiState.update { it.copy(streamingEnabled = streamingEnabled) }

                // 加载主题配置
                val themeConfig = configManager.getThemeConfig()
                _uiState.update { it.copy(themeConfig = themeConfig) }

                // 加载文本合并配置
                val textMergingEnabled = configManager.getTextMergingEnabled()
                val savedMergingPrompt = configManager.getTextMergingPrompt()
                _uiState.update { it.copy(
                    textMergingEnabled = textMergingEnabled,
                    textMergingPrompt = savedMergingPrompt ?: TranslationApiClientImpl.DEFAULT_MERGING_PROMPT
                ) }

                // 加载 OCR 语言配置
                val ocrLanguage = configManager.getOcrLanguage()
                _uiState.update { it.copy(ocrLanguage = ocrLanguage) }

                // 加载本地 OCR 开关
                val enableLocalOcr = configManager.getEnableLocalOcr()
                _uiState.update { it.copy(enableLocalOcr = enableLocalOcr) }

                // 加载使用本地 OCR 方案
                val useLocalOcrScheme = configManager.getUseLocalOcrScheme()
                _uiState.update { it.copy(useLocalOcrScheme = useLocalOcrScheme) }

                // 加载本地 OCR 翻译模式
                val localOcrTranslationMode = configManager.getLocalOcrTranslationMode()
                _uiState.update { it.copy(localOcrTranslationMode = localOcrTranslationMode) }

                // 加载云端 LLM 模型
                val cloudLlmModelId = configManager.getCloudLlmModel()
                val cloudLlmModel = LlmModel.fromModelId(cloudLlmModelId) ?: LlmModel.DEFAULT
                _uiState.update { it.copy(cloudLlmModel = cloudLlmModel) }

                // 加载云端 LLM 提示词
                val cloudLlmPrompt = configManager.getCustomTranslationPrompt()
                _uiState.update { it.copy(cloudLlmPrompt = cloudLlmPrompt) }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "加载配置失败", e)
            }
        }
    }

    /**
     * 刷新权限状态
     */
    fun refreshPermissionStatus() {
        val permissionStatus = permissionManager.getAllPermissionStatus()
        _uiState.update { it.copy(
            hasOverlayPermission = permissionStatus.hasOverlayPermission,
            hasBatteryOptimizationDisabled = permissionStatus.hasBatteryOptimizationDisabled,
            hasScreenCapturePermission = permissionStatus.hasScreenCapturePermission,
            canStartService = permissionStatus.canStartService
        ) }
    }

    /**
     * 处理设置界面事件
     */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ApiKeyChanged -> {
                val apiKey = event.apiKey
                _uiState.update { it.copy(
                    apiKey = apiKey,
                    isApiKeyValid = validateApiKey(apiKey)
                ) }
                // 保存到 DataStore
                viewModelScope.launch {
                    try {
                        configManager.saveApiKey(apiKey)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存 API Key 失败", e)
                    }
                }
            }
            is SettingsEvent.SourceLanguageChanged -> {
                _uiState.update { it.copy(sourceLanguage = event.language) }
                // 保存到 DataStore
                viewModelScope.launch {
                    try {
                        configManager.saveLanguageConfig(
                            com.cw2.cw_1kito.model.LanguageConfig(
                                sourceLanguage = event.language,
                                targetLanguage = _uiState.value.targetLanguage
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存语言配置失败", e)
                    }
                }
            }
            is SettingsEvent.TargetLanguageChanged -> {
                _uiState.update { it.copy(targetLanguage = event.language) }
                // 保存到 DataStore
                viewModelScope.launch {
                    try {
                        configManager.saveLanguageConfig(
                            com.cw2.cw_1kito.model.LanguageConfig(
                                sourceLanguage = _uiState.value.sourceLanguage,
                                targetLanguage = event.language
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存语言配置失败", e)
                    }
                }
            }
            is SettingsEvent.ModelChanged -> {
                _uiState.update { it.copy(selectedModel = event.model) }
                // 保存到 DataStore
                viewModelScope.launch {
                    try {
                        configManager.saveSelectedModel(event.model)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存模型配置失败", e)
                    }
                }
            }
            is SettingsEvent.PromptChanged -> {
                _uiState.update { it.copy(customPrompt = event.prompt) }
                viewModelScope.launch {
                    try {
                        // 如果和默认一致则清除持久化
                        if (event.prompt == TranslationApiClientImpl.DEFAULT_PROMPT) {
                            configManager.saveCustomPrompt(null)
                        } else {
                            configManager.saveCustomPrompt(event.prompt)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存提示词失败", e)
                    }
                }
            }
            SettingsEvent.ResetPrompt -> {
                _uiState.update { it.copy(customPrompt = TranslationApiClientImpl.DEFAULT_PROMPT) }
                viewModelScope.launch {
                    try {
                        configManager.saveCustomPrompt(null)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "重置提示词失败", e)
                    }
                }
            }
            SettingsEvent.RequestOverlayPermission -> {
                // 由 Activity 处理实际的权限请求
                _uiState.update { it.copy(errorMessage = "请在系统设置中授予悬浮窗权限") }
            }
            SettingsEvent.RequestBatteryOptimization -> {
                // 由 Activity 处理实际的权限请求
            }
            SettingsEvent.RequestScreenCapturePermission -> {
                // 由 Activity 处理实际的权限请求
            }
            SettingsEvent.StartService -> {
                startFloatingService()
            }
            SettingsEvent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is SettingsEvent.StreamingEnabledChanged -> {
                _uiState.update { it.copy(streamingEnabled = event.enabled) }
                viewModelScope.launch {
                    try {
                        configManager.saveStreamingEnabled(event.enabled)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存流式翻译设置失败", e)
                    }
                }
            }
            is SettingsEvent.ThemeHueChanged -> {
                val newConfig = _uiState.value.themeConfig.copy(hue = event.hue)
                _uiState.update { it.copy(themeConfig = newConfig) }
                viewModelScope.launch {
                    try {
                        configManager.saveThemeConfig(newConfig)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存主题配置失败", e)
                    }
                }
            }
            is SettingsEvent.DarkModeChanged -> {
                val newConfig = _uiState.value.themeConfig.copy(darkMode = event.darkMode)
                _uiState.update { it.copy(themeConfig = newConfig) }
                viewModelScope.launch {
                    try {
                        configManager.saveThemeConfig(newConfig)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存主题配置失败", e)
                    }
                }
            }
            SettingsEvent.ResetTheme -> {
                val defaultConfig = ThemeConfig.DEFAULT
                _uiState.update { it.copy(themeConfig = defaultConfig) }
                viewModelScope.launch {
                    try {
                        configManager.saveThemeConfig(defaultConfig)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "重置主题配置失败", e)
                    }
                }
            }
            SettingsEvent.NavigateToLab -> {
                // 导航由 MainScreen 状态控制，无需处理
            }
            is SettingsEvent.PromptTabChanged -> {
                _uiState.update { it.copy(selectedPromptTab = event.tab) }
            }
            is SettingsEvent.TextMergingPromptChanged -> {
                _uiState.update { it.copy(textMergingPrompt = event.prompt) }
                viewModelScope.launch {
                    try {
                        // 如果和默认一致则清除持久化
                        if (event.prompt == TranslationApiClientImpl.DEFAULT_MERGING_PROMPT) {
                            configManager.saveTextMergingPrompt(null)
                        } else {
                            configManager.saveTextMergingPrompt(event.prompt)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存文本合并提示词失败", e)
                    }
                }
            }
            SettingsEvent.ResetTextMergingPrompt -> {
                _uiState.update { it.copy(textMergingPrompt = TranslationApiClientImpl.DEFAULT_MERGING_PROMPT) }
                viewModelScope.launch {
                    try {
                        configManager.saveTextMergingPrompt(null)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "重置文本合并提示词失败", e)
                    }
                }
            }
            is SettingsEvent.TextMergingEnabledChanged -> {
                _uiState.update { it.copy(textMergingEnabled = event.enabled) }
                viewModelScope.launch {
                    try {
                        configManager.saveTextMergingEnabled(event.enabled)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存文本合并开关状态失败", e)
                    }
                }
            }
            is SettingsEvent.OcrLanguageChanged -> {
                _uiState.update { it.copy(ocrLanguage = event.language) }
                viewModelScope.launch {
                    try {
                        configManager.saveOcrLanguage(event.language)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存 OCR 语言失败", e)
                    }
                }
            }
            is SettingsEvent.EnableLocalOcrChanged -> {
                _uiState.update { it.copy(enableLocalOcr = event.enabled) }
                viewModelScope.launch {
                    try {
                        configManager.saveEnableLocalOcr(event.enabled)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存本地OCR开关状态失败", e)
                    }
                }
            }
            is SettingsEvent.UseLocalOcrSchemeChanged -> {
                _uiState.update { it.copy(useLocalOcrScheme = event.useLocalOcr) }
                viewModelScope.launch {
                    try {
                        configManager.saveUseLocalOcrScheme(event.useLocalOcr)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存翻译方案选择失败", e)
                    }
                }
            }
            // 语言包相关事件
            SettingsEvent.ShowLanguagePackGuide -> {
                _uiState.update { it.copy(showLanguagePackGuide = true) }
            }
            SettingsEvent.DismissLanguagePackGuide -> {
                _uiState.update { it.copy(
                    showLanguagePackGuide = false,
                    languagePackPrompt = null,
                    hasCheckedLanguagePacks = true
                ) }
            }
            is SettingsEvent.DownloadLanguagePack -> {
                // 下载请求由 MainActivity 处理（需要 Android 特定实现）
                _uiState.update { it.copy(isLoading = true) }
            }
            SettingsEvent.LanguagePackCheckComplete -> {
                _uiState.update { it.copy(hasCheckedLanguagePacks = true) }
            }
            // ========== 本地 OCR 新增事件处理 ==========
            is SettingsEvent.LocalOcrTranslationModeChanged -> {
                _uiState.update { it.copy(localOcrTranslationMode = event.mode) }
                viewModelScope.launch {
                    try {
                        configManager.saveLocalOcrTranslationMode(event.mode)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存本地OCR翻译模式失败", e)
                    }
                }
            }
            is SettingsEvent.CloudLlmModelChanged -> {
                _uiState.update { it.copy(cloudLlmModel = event.model) }
                viewModelScope.launch {
                    try {
                        configManager.saveCloudLlmModel(event.model.modelId)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存云端LLM模型失败", e)
                    }
                }
            }
            is SettingsEvent.CloudLlmPromptChanged -> {
                _uiState.update { it.copy(cloudLlmPrompt = event.prompt) }
                viewModelScope.launch {
                    try {
                        configManager.saveCustomTranslationPrompt(event.prompt)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "保存云端LLM提示词失败", e)
                    }
                }
            }
            SettingsEvent.ResetCloudLlmPrompt -> {
                _uiState.update { it.copy(cloudLlmPrompt = null) }
                viewModelScope.launch {
                    try {
                        configManager.saveCustomTranslationPrompt(null)
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "重置云端LLM提示词失败", e)
                    }
                }
            }
            SettingsEvent.NavigateToLanguagePackManagement -> {
                _uiState.update { it.copy(showLanguagePackManagement = true) }
            }
            SettingsEvent.DismissLanguagePackManagement -> {
                _uiState.update { it.copy(showLanguagePackManagement = false) }
            }
            is SettingsEvent.DownloadLanguagePackFor -> {
                // 下载指定语言包，由 MainActivity 处理实际下载
                _uiState.update { it.copy(isLoading = true) }
            }
            is SettingsEvent.DeleteLanguagePackFor -> {
                // 删除指定语言包，由 MainActivity 处理实际删除
            }
        }
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingService() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // TODO: 实际启动服务的逻辑将在 MainActivity 中处理
                // 这里只更新状态
                _uiState.update { it.copy(
                    isLoading = false,
                    canStartService = true
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "启动服务失败: ${e.message}"
                ) }
            }
        }
    }

    /**
     * 验证 API Key 格式
     * 硅基流动的 API Key 通常以 "sk-" 开头
     */
    private fun validateApiKey(apiKey: String): Boolean {
        return apiKey.startsWith("sk-") && apiKey.length > 10
    }

    /**
     * 更新权限状态（从 Activity 回调）
     */
    fun updatePermissionStatus(
        hasOverlayPermission: Boolean? = null,
        hasBatteryOptimizationDisabled: Boolean? = null,
        hasScreenCapturePermission: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                hasOverlayPermission = hasOverlayPermission ?: state.hasOverlayPermission,
                hasBatteryOptimizationDisabled = hasBatteryOptimizationDisabled ?: state.hasBatteryOptimizationDisabled,
                hasScreenCapturePermission = hasScreenCapturePermission ?: state.hasScreenCapturePermission,
                canStartService = (hasOverlayPermission ?: state.hasOverlayPermission) &&
                    (hasScreenCapturePermission ?: state.hasScreenCapturePermission)
            )
        }
    }

    /**
     * 开始翻译（由悬浮球点击触发）
     */
    fun startTranslation() {
        viewModelScope.launch {
            val apiKey = _uiState.value.apiKey
            if (!validateApiKey(apiKey)) {
                _uiState.update { it.copy(errorMessage = "请先配置有效的 API Key") }
                return@launch
            }

            // TODO: 实现屏幕截图和翻译逻辑
            android.util.Log.d("MainViewModel", "开始翻译流程...")
            android.util.Log.d("MainViewModel", "API Key: ${apiKey.take(10)}...")
            android.util.Log.d("MainViewModel", "源语言: ${_uiState.value.sourceLanguage.displayName}")
            android.util.Log.d("MainViewModel", "目标语言: ${_uiState.value.targetLanguage.displayName}")
            android.util.Log.d("MainViewModel", "文本合并模式: ${_uiState.value.textMergingEnabled}")

            // 构建翻译配置，传入 textMergingEnabled 状态
            val config = com.cw2.cw_1kito.model.TranslationConfig(
                model = _uiState.value.selectedModel,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = _uiState.value.targetLanguage,
                useMergingPrompt = _uiState.value.textMergingEnabled
            )
            android.util.Log.d("MainViewModel", "翻译配置: $config")
        }
    }

    /**
     * 设置语言包下载提示信息
     *
     * 此方法由 Android 特定的启动检查器调用，用于显示下载引导对话框。
     *
     * @param prompt 语言包下载提示信息
     */
    fun setLanguagePackPrompt(prompt: LanguagePackPrompt?) {
        _uiState.update { it.copy(
            languagePackPrompt = prompt,
            showLanguagePackGuide = prompt != null
        ) }
    }

    /**
     * 设置语言包检查加载状态
     *
     * @param isLoading 是否正在加载
     */
    fun setLanguagePackLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    /**
     * 设置语言包下载错误消息
     *
     * @param error 错误消息
     */
    fun setLanguagePackError(error: String?) {
        _uiState.update { it.copy(
            isLoading = false,
            errorMessage = error
        ) }
    }

    /**
     * 设置加载状态
     *
     * @param isLoading 是否正在加载
     */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    /**
     * 设置错误消息
     *
     * @param error 错误消息
     */
    fun setError(error: String?) {
        _uiState.update { it.copy(
            isLoading = false,
            errorMessage = error
        ) }
    }

    /**
     * 更新语言包状态列表
     *
     * @param states 新的语言包状态列表
     */
    fun updateLanguagePackStates(states: List<com.cw2.cw_1kito.model.LanguagePackState>) {
        _uiState.update { it.copy(languagePackStates = states) }
    }

    /**
     * 更新单个语言对的状态
     *
     * 用于在下载/删除操作时即时更新 UI，提供用户反馈。
     *
     * @param source 源语言
     * @param target 目标语言
     * @param status 新状态
     * @param errorMessage 错误信息（可选）
     */
    fun updateSingleLanguagePackStatus(
        source: com.cw2.cw_1kito.model.Language,
        target: com.cw2.cw_1kito.model.Language,
        status: com.cw2.cw_1kito.model.LanguagePackStatus,
        errorMessage: String? = null
    ) {
        _uiState.update { state ->
            val currentStates = state.languagePackStates
                ?: com.cw2.cw_1kito.model.getDefaultLanguagePackStates()
            val updatedStates = currentStates.map { pack ->
                if (pack.sourceLang == source && pack.targetLang == target) {
                    pack.copy(status = status, errorMessage = errorMessage)
                } else {
                    pack
                }
            }
            state.copy(languagePackStates = updatedStates)
        }
    }
}
