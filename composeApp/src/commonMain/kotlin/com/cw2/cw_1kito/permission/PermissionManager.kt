package com.cw2.cw_1kito.permission

/**
 * 权限管理器接口
 */
interface PermissionManager {

    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(): Boolean

    /**
     * 检查电池优化是否已关闭
     */
    fun isBatteryOptimizationDisabled(): Boolean

    /**
     * 检查是否有录屏权限
     */
    fun hasScreenCapturePermission(): Boolean

    /**
     * 获取所有必需权限的状态
     */
    fun getAllPermissionStatus(): PermissionStatus
}

/**
 * 权限状态
 */
data class PermissionStatus(
    val hasOverlayPermission: Boolean,
    val hasBatteryOptimizationDisabled: Boolean,
    val hasScreenCapturePermission: Boolean = false,
    val canStartService: Boolean
) {
    companion object {
        fun create(
            hasOverlayPermission: Boolean,
            hasBatteryOptimizationDisabled: Boolean,
            hasScreenCapturePermission: Boolean = false
        ): PermissionStatus {
            return PermissionStatus(
                hasOverlayPermission = hasOverlayPermission,
                hasBatteryOptimizationDisabled = hasBatteryOptimizationDisabled,
                hasScreenCapturePermission = hasScreenCapturePermission,
                canStartService = hasOverlayPermission && hasScreenCapturePermission
                // 电池优化不是必需的，但建议关闭
            )
        }
    }
}
