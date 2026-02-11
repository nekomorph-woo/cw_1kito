package com.cw2.cw_1kito

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.permission.PermissionManager
import com.cw2.cw_1kito.ui.screen.SettingsEvent
import com.cw2.cw_1kito.ui.screen.SettingsUiState
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
        }
    }
}
