package com.cw2.cw_1kito

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.cw2.cw_1kito.data.config.AndroidConfigManagerImpl
import com.cw2.cw_1kito.engine.translation.local.DownloadResult
import com.cw2.cw_1kito.engine.translation.local.LanguagePackStartupChecker
import com.cw2.cw_1kito.engine.translation.local.MLKitLanguagePackManager
import com.cw2.cw_1kito.permission.PermissionManagerImpl
import com.cw2.cw_1kito.service.capture.ScreenCaptureManager
import com.cw2.cw_1kito.service.floating.FloatingService
import com.cw2.cw_1kito.ui.screen.MainScreen
import com.cw2.cw_1kito.ui.screen.SettingsEvent
import com.cw2.cw_1kito.ui.theme.KitoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 主 Activity
 *
 * 应用入口，提供设置界面和启动悬浮窗服务的入口
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_SCREEN_CAPTURE = 1001
    }

    // 权限请求结果
    private var currentRequestType: RequestType? = null

    private enum class RequestType {
        OVERLAY_PERMISSION,
        BATTERY_OPTIMIZATION,
        SCREEN_CAPTURE
    }

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshPermissionStatus()
    }

    // 电池优化忽略请求
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshPermissionStatus()
    }

    // 创建 ViewModel
    private val viewModel: MainViewModel by viewModels {
        val permissionManager = PermissionManagerImpl(applicationContext)
        val configManager = AndroidConfigManagerImpl(applicationContext)
        MainViewModelFactory(permissionManager, configManager)
    }

    // 语言包管理器
    private val languagePackManager by lazy {
        MLKitLanguagePackManager(applicationContext)
    }

    // 语言包启动检查器（懒加载，仅在需要时初始化）
    private val languagePackStartupChecker by lazy {
        LanguagePackStartupChecker(
            context = applicationContext,
            configManager = AndroidConfigManagerImpl(applicationContext),
            languagePackManager = languagePackManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 ScreenCaptureManager
        ScreenCaptureManager.init(applicationContext)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            KitoTheme(themeConfig = uiState.themeConfig) {
                MainScreen(
                    uiState = uiState,
                    onEvent = { event -> handleEvent(event) }
                )
            }
        }

        // 刷新权限状态
        refreshPermissionStatus()

        // 检查是否从 FloatingService 跳转过来请求重新授权
        handleReAuthIntent(intent)

        // 启动语言包检查（仅首次启动）
        checkLanguagePacksOnStartup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReAuthIntent(intent)
    }

    /**
     * 处理从 FloatingService 跳转来的重新授权请求
     */
    private fun handleReAuthIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("request_screen_capture", false) == true) {
            Log.d(TAG, "Re-auth requested from FloatingService, launching screen capture permission")
            // 清除 flag 防止重复触发
            intent.removeExtra("request_screen_capture")
            requestScreenCapturePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回时刷新权限状态
        refreshPermissionStatus()
    }

    /**
     * 启动时检查语言包
     *
     * 仅在首次安装或更新版本时执行检查，避免每次启动都弹出提示。
     */
    private fun checkLanguagePacksOnStartup() {
        lifecycleScope.launch {
            try {
                // 检查是否应该执行启动检查
                if (!languagePackStartupChecker.shouldPerformStartupCheck(applicationContext)) {
                    Log.d(TAG, "语言包启动检查已完成，跳过")
                    viewModel.onEvent(SettingsEvent.LanguagePackCheckComplete)
                    return@launch
                }

                Log.d(TAG, "执行语言包启动检查...")

                // 执行检查
                val prompt = languagePackStartupChecker.checkLanguagePacksOnStartup()

                if (prompt != null) {
                    // 需要下载语言包，设置提示信息
                    viewModel.setLanguagePackPrompt(prompt)
                } else {
                    // 语言包已就绪或不需要
                    viewModel.onEvent(SettingsEvent.LanguagePackCheckComplete)
                }

                // 标记检查已完成
                languagePackStartupChecker.markCheckCompleted(applicationContext)

            } catch (e: Exception) {
                Log.e(TAG, "语言包启动检查失败", e)
                // 出错时也标记完成，避免每次启动都检查
                viewModel.onEvent(SettingsEvent.LanguagePackCheckComplete)
            }
        }
    }

    /**
     * 处理 UI 事件
     */
    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.RequestOverlayPermission -> {
                requestOverlayPermission()
            }
            is SettingsEvent.RequestBatteryOptimization -> {
                requestBatteryOptimization()
            }
            is SettingsEvent.RequestScreenCapturePermission -> {
                requestScreenCapturePermission()
            }
            is SettingsEvent.StartService -> {
                startFloatingService()
            }
            is SettingsEvent.DownloadLanguagePack -> {
                downloadLanguagePack(event.requireWifi)
            }
            is SettingsEvent.DownloadLanguagePackFor -> {
                downloadLanguagePackFor(event.source, event.target)
            }
            is SettingsEvent.DeleteLanguagePackFor -> {
                deleteLanguagePackFor(event.source, event.target)
            }
            is SettingsEvent.DownloadLanguageModel -> {
                downloadLanguageModel(event.language)
            }
            is SettingsEvent.DeleteLanguageModel -> {
                deleteLanguageModel(event.language)
            }
            is SettingsEvent.NavigateToLanguagePackManagement -> {
                // 先刷新语言包状态，再显示管理页面
                navigateToLanguagePackManagement()
            }
            else -> {
                viewModel.onEvent(event)
            }
        }
    }

    /**
     * 下载语言包
     *
     * @param requireWifi 是否要求 Wi-Fi 环境
     */
    private fun downloadLanguagePack(requireWifi: Boolean) {
        lifecycleScope.launch {
            try {
                viewModel.setLanguagePackLoading(true)

                val result = languagePackStartupChecker.downloadCurrentLanguagePair(requireWifi)

                when (result) {
                    is DownloadResult.Success -> {
                        val sizeMB = result.sizeBytes / (1024 * 1024)
                        Log.d(TAG, "语言包下载成功，大小: ${sizeMB}MB")
                        viewModel.setLanguagePackError(null)
                        viewModel.onEvent(SettingsEvent.DismissLanguagePackGuide)
                    }
                    is DownloadResult.WifiRequired -> {
                        Log.w(TAG, "需要 Wi-Fi 连接")
                        viewModel.setLanguagePackError("当前非 Wi-Fi 环境，请连接 Wi-Fi 后重试")
                    }
                    is DownloadResult.Failed -> {
                        Log.e(TAG, "语言包下载失败", result.error)
                        viewModel.setLanguagePackError("下载失败: ${result.error.message}")
                    }
                    is DownloadResult.UnsupportedLanguage -> {
                        Log.w(TAG, "不支持的语言对")
                        viewModel.setLanguagePackError("当前语言对不支持本地翻译")
                    }
                    is DownloadResult.Cancelled -> {
                        Log.d(TAG, "语言包下载已取消")
                        viewModel.setLanguagePackLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "语言包下载异常", e)
                viewModel.setLanguagePackError("下载异常: ${e.message}")
            }
        }
    }

    /**
     * 下载指定语言对的语言包
     *
     * @param source 源语言
     * @param target 目标语言
     */
    private fun downloadLanguagePackFor(source: com.cw2.cw_1kito.model.Language, target: com.cw2.cw_1kito.model.Language) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始下载语言包: ${source.code} -> ${target.code}")

                // 立即将该语言对的 UI 状态更新为"下载中"，让用户看到反馈
                viewModel.updateSingleLanguagePackStatus(
                    source, target, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADING
                )

                val result = languagePackManager.downloadLanguagePair(source, target, requireWifi = false)

                when (result) {
                    is DownloadResult.Success -> {
                        val sizeMB = result.sizeBytes / (1024 * 1024)
                        Log.d(TAG, "语言包下载成功，大小: ${sizeMB}MB")
                        // 刷新语言包状态列表
                        refreshLanguagePackStates()
                    }
                    is DownloadResult.WifiRequired -> {
                        Log.w(TAG, "需要 Wi-Fi 连接")
                        viewModel.updateSingleLanguagePackStatus(
                            source, target, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "当前非 Wi-Fi 环境，请连接 Wi-Fi 后重试"
                        )
                    }
                    is DownloadResult.Failed -> {
                        Log.e(TAG, "语言包下载失败", result.error)
                        viewModel.updateSingleLanguagePackStatus(
                            source, target, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "下载失败: ${result.error.message}"
                        )
                    }
                    is DownloadResult.UnsupportedLanguage -> {
                        Log.w(TAG, "不支持的语言对")
                        viewModel.updateSingleLanguagePackStatus(
                            source, target, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "该语言对不支持本地翻译"
                        )
                    }
                    is DownloadResult.Cancelled -> {
                        Log.d(TAG, "语言包下载已取消")
                        refreshLanguagePackStates()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "语言包下载异常", e)
                viewModel.updateSingleLanguagePackStatus(
                    source, target, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                    errorMessage = "下载异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 删除指定语言对的语言包
     *
     * @param source 源语言
     * @param target 目标语言
     */
    private fun deleteLanguagePackFor(source: com.cw2.cw_1kito.model.Language, target: com.cw2.cw_1kito.model.Language) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始删除语言包: ${source.code} -> ${target.code}")
                languagePackManager.deleteLanguagePair(source, target)
                Log.d(TAG, "语言包删除成功")
                // 刷新语言包状态列表
                refreshLanguagePackStates()
            } catch (e: Exception) {
                Log.e(TAG, "语言包删除失败", e)
                viewModel.setError("删除失败: ${e.message}")
            }
        }
    }

    /**
     * 下载单个语言模型
     */
    private fun downloadLanguageModel(language: com.cw2.cw_1kito.model.Language) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始下载语言模型: ${language.code}")
                viewModel.updateSingleLanguageModelStatus(
                    language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADING
                )

                val result = languagePackManager.downloadLanguageModel(language, requireWifi = false)

                when (result) {
                    is DownloadResult.Success -> {
                        Log.d(TAG, "语言模型下载成功: ${language.code}")
                        // 乐观更新：立即将该模型状态设为 DOWNLOADED
                        viewModel.updateSingleLanguageModelStatus(
                            language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADED
                        )
                    }
                    is DownloadResult.WifiRequired -> {
                        viewModel.updateSingleLanguageModelStatus(
                            language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "当前非 Wi-Fi 环境，请连接 Wi-Fi 后重试"
                        )
                    }
                    is DownloadResult.Failed -> {
                        Log.e(TAG, "语言模型下载失败", result.error)
                        viewModel.updateSingleLanguageModelStatus(
                            language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "下载失败: ${result.error.message}"
                        )
                    }
                    is DownloadResult.UnsupportedLanguage -> {
                        viewModel.updateSingleLanguageModelStatus(
                            language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                            errorMessage = "该语言不支持本地翻译"
                        )
                    }
                    is DownloadResult.Cancelled -> {
                        viewModel.updateSingleLanguageModelStatus(
                            language, com.cw2.cw_1kito.model.LanguagePackStatus.NOT_DOWNLOADED
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "语言模型下载异常", e)
                viewModel.updateSingleLanguageModelStatus(
                    language, com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED,
                    errorMessage = "下载异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 删除单个语言模型
     */
    private fun deleteLanguageModel(language: com.cw2.cw_1kito.model.Language) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始删除语言模型: ${language.code}")
                val success = languagePackManager.deleteLanguageModel(language)
                if (success) {
                    Log.d(TAG, "语言模型删除成功: ${language.code}")
                    viewModel.updateSingleLanguageModelStatus(
                        language, com.cw2.cw_1kito.model.LanguagePackStatus.NOT_DOWNLOADED
                    )
                } else {
                    Log.w(TAG, "语言模型无法删除（可能为系统内置）: ${language.code}")
                    viewModel.setError("该语言模型为系统内置，无法删除")
                }
            } catch (e: Exception) {
                Log.e(TAG, "语言模型删除失败", e)
                viewModel.setError("删除失败: ${e.message}")
            }
        }
    }

    /**
     * 刷新语言包状态并更新 UI
     */
    private suspend fun refreshLanguagePackStates() {
        try {
            // 刷新管理器内部状态（重新查询 ML Kit）
            languagePackManager.refreshStates()

            // 获取所有语言对的状态并转换为 UI 模型
            // 使用 first() 只获取当前值，避免 collect 无限挂起
            val states = languagePackManager.getLanguagePackStates().first()

            // 转换为 UI 使用的 LanguagePackState
            val uiStates = states.map { state ->
                com.cw2.cw_1kito.model.LanguagePackState(
                    sourceLang = state.sourceLang,
                    targetLang = state.targetLang,
                    status = when (state.status) {
                        com.cw2.cw_1kito.engine.translation.local.DownloadStatus.DOWNLOADED ->
                            com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADED
                        com.cw2.cw_1kito.engine.translation.local.DownloadStatus.DOWNLOADING ->
                            com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADING
                        com.cw2.cw_1kito.engine.translation.local.DownloadStatus.NOT_DOWNLOADED ->
                            com.cw2.cw_1kito.model.LanguagePackStatus.NOT_DOWNLOADED
                        com.cw2.cw_1kito.engine.translation.local.DownloadStatus.FAILED ->
                            com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOAD_FAILED
                        com.cw2.cw_1kito.engine.translation.local.DownloadStatus.PAUSED ->
                            com.cw2.cw_1kito.model.LanguagePackStatus.NOT_DOWNLOADED
                    },
                    sizeBytes = state.sizeBytes,
                    downloadProgress = state.progress
                )
            }
            viewModel.updateLanguagePackStates(uiStates)
            Log.d(TAG, "语言包状态已更新: ${uiStates.count { it.status == com.cw2.cw_1kito.model.LanguagePackStatus.DOWNLOADED }} 个已下载")

        } catch (e: Exception) {
            Log.e(TAG, "刷新语言包状态失败", e)
        }
    }

    /**
     * 刷新语言模型状态并更新 UI
     */
    private suspend fun refreshLanguageModelStates() {
        try {
            val modelStates = languagePackManager.getLanguageModelStates()
            viewModel.updateLanguageModelStates(modelStates)
            Log.d(TAG, "语言模型状态已更新: ${modelStates.count { it.isDownloaded }} 个已下载")
        } catch (e: Exception) {
            Log.e(TAG, "刷新语言模型状态失败", e)
        }
    }

    /**
     * 导航到语言包管理页面
     *
     * 先刷新语言模型真实状态，再显示管理页面，确保用户看到的是最新状态。
     */
    private fun navigateToLanguagePackManagement() {
        lifecycleScope.launch {
            try {
                refreshLanguageModelStates()
            } catch (e: Exception) {
                Log.e(TAG, "刷新语言模型状态失败", e)
            }
            // 无论刷新是否成功，都显示管理页面
            viewModel.onEvent(SettingsEvent.NavigateToLanguagePackManagement)
        }
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        val intent = PermissionManagerImpl.createOverlayPermissionIntent(this)
        overlayPermissionLauncher.launch(intent)
    }

    /**
     * 请求忽略电池优化
     */
    private fun requestBatteryOptimization() {
        val intent = PermissionManagerImpl.createBatteryOptimizationIntent(this)
        batteryOptimizationLauncher.launch(intent)
    }

    /**
     * 请求录屏权限
     */
    private fun requestScreenCapturePermission() {
        // 确保 ScreenCaptureManager 已初始化（可能之前被 release() 清空了）
        ScreenCaptureManager.init(applicationContext)
        val intent = ScreenCaptureManager.requestPermission(this, REQUEST_SCREEN_CAPTURE)
        currentRequestType = RequestType.SCREEN_CAPTURE
        startActivityForResult(intent, REQUEST_SCREEN_CAPTURE)
    }

    /**
     * 处理 Activity 结果
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_SCREEN_CAPTURE -> {
                Log.d(TAG, "Screen capture result: resultCode=$resultCode")
                // 将权限结果保存到 ScreenCaptureManager
                ScreenCaptureManager.setPermissionResult(resultCode, data)
                // 刷新权限状态
                refreshPermissionStatus()
            }
        }
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingService() {
        try {
            val intent = Intent(this, FloatingService::class.java).apply {
                action = FloatingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                startService(intent)
            }
            // 重置加载状态
            viewModel.setLoading(false)
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮窗服务失败", e)
            viewModel.setError("启动服务失败: ${e.message}")
        }
    }

    /**
     * 刷新权限状态
     */
    private fun refreshPermissionStatus() {
        val permissionManager = PermissionManagerImpl(applicationContext)
        viewModel.updatePermissionStatus(
            hasOverlayPermission = permissionManager.hasOverlayPermission(),
            hasBatteryOptimizationDisabled = permissionManager.isBatteryOptimizationDisabled(),
            hasScreenCapturePermission = ScreenCaptureManager.hasPermission()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不在这里释放 ScreenCaptureManager，因为服务可能还在使用
    }
}

/**
 * ViewModel Factory
 */
class MainViewModelFactory(
    private val permissionManager: PermissionManagerImpl,
    private val configManager: com.cw2.cw_1kito.data.config.ConfigManager
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(permissionManager, configManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
