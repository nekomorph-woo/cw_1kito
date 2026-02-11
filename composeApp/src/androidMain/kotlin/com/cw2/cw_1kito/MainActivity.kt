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
import com.cw2.cw_1kito.data.config.AndroidConfigManagerImpl
import com.cw2.cw_1kito.permission.PermissionManagerImpl
import com.cw2.cw_1kito.service.capture.ScreenCaptureManager
import com.cw2.cw_1kito.service.floating.FloatingService
import com.cw2.cw_1kito.ui.screen.MainScreen
import com.cw2.cw_1kito.ui.screen.SettingsEvent
import com.cw2.cw_1kito.ui.theme.KitoTheme

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
            else -> {
                viewModel.onEvent(event)
            }
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
        val intent = Intent(this, FloatingService::class.java).apply {
            action = FloatingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
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
