package com.cw2.cw_1kito.data.config

import android.content.Context

/**
 * 配置管理器单例提供者
 *
 * 确保整个应用使用同一个 [AndroidConfigManagerImpl] 实例，
 * 这样配置变更事件（通过 Flow）可以正确传递给所有监听者。
 *
 * ## 使用方式
 * ```kotlin
 * // 在 Application.onCreate() 中初始化
 * ConfigManagerProvider.init(applicationContext)
 *
 * // 在任何地方获取实例
 * val configManager = ConfigManagerProvider.get()
 * ```
 */
object ConfigManagerProvider {

    private var _instance: AndroidConfigManagerImpl? = null

    /**
     * 初始化配置管理器
     *
     * 应在 Application.onCreate() 或 MainActivity.onCreate() 中调用。
     *
     * @param context 应用上下文
     */
    fun init(context: Context) {
        if (_instance == null) {
            _instance = AndroidConfigManagerImpl(context.applicationContext)
        }
    }

    /**
     * 获取配置管理器实例
     *
     * 如果尚未初始化，会抛出异常。
     *
     * @return 配置管理器实例
     * @throws IllegalStateException 如果尚未初始化
     */
    fun get(): AndroidConfigManagerImpl {
        return _instance ?: throw IllegalStateException(
            "ConfigManagerProvider 尚未初始化，请先调用 init(context)"
        )
    }

    /**
     * 获取配置管理器实例（可空）
     *
     * @return 配置管理器实例，如果尚未初始化则返回 null
     */
    fun getOrNull(): AndroidConfigManagerImpl? = _instance

    /**
     * 检查是否已初始化
     *
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean = _instance != null

    /**
     * 重置（仅用于测试）
     */
    fun reset() {
        _instance = null
    }
}
