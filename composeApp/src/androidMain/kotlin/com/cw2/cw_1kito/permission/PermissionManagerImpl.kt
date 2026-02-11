package com.cw2.cw_1kito.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.cw2.cw_1kito.service.capture.ScreenCaptureManager

/**
 * Android 平台的权限管理器实现
 */
class PermissionManagerImpl(
    private val context: Context
) : PermissionManager {

    companion object {
        /**
         * 创建悬浮窗权限请求 Intent
         */
        fun createOverlayPermissionIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }

        /**
         * 创建电池优化忽略请求 Intent
         */
        fun createBatteryOptimizationIntent(context: Context): Intent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            }
            // 对于低于 M 的版本，跳转到电池优化设置页面
            return Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }

        /**
         * 检查是否有悬浮窗权限
         */
        fun checkOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        /**
         * 检查电池优化是否已禁用
         */
        fun checkBatteryOptimizationDisabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            // 低于 M 版本默认返回 true
            return true
        }
    }

    /**
     * 检查悬浮窗权限
     */
    override fun hasOverlayPermission(): Boolean {
        return checkOverlayPermission(context)
    }

    /**
     * 检查电池优化是否已关闭
     */
    override fun isBatteryOptimizationDisabled(): Boolean {
        return checkBatteryOptimizationDisabled(context)
    }

    /**
     * 检查是否有录屏权限
     *
     * 注意：录屏权限需要通过 MediaProjectionManager 请求，
     * 无法直接检查。这里返回 false 表示需要用户操作。
     */
    override fun hasScreenCapturePermission(): Boolean {
        // 检查 ScreenCaptureManager 是否有权限
        return ScreenCaptureManager.hasPermission()
    }

    /**
     * 获取所有必需权限的状态
     */
    override fun getAllPermissionStatus(): PermissionStatus {
        return PermissionStatus.create(
            hasOverlayPermission = hasOverlayPermission(),
            hasBatteryOptimizationDisabled = isBatteryOptimizationDisabled(),
            hasScreenCapturePermission = hasScreenCapturePermission()
        )
    }

    /**
     * 请求悬浮窗权限（需要跳转到设置页面）
     */
    fun requestOverlayPermission(activity: Activity, requestCode: Int) {
        val intent = createOverlayPermissionIntent(context)
        activity.startActivityForResult(intent, requestCode)
    }

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimization(activity: Activity, requestCode: Int) {
        val intent = createBatteryOptimizationIntent(context)
        activity.startActivityForResult(intent, requestCode)
    }
}
