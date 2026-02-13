package com.cw2.cw_1kito.util

import timber.log.Timber

/**
 * Android 平台的 Timber 日志实现
 */
actual object TimberLogger {

    actual fun d(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).d(message, *args)
    }

    actual fun i(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).i(message, *args)
    }

    actual fun w(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).w(message, *args)
    }

    actual fun e(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).e(message, *args)
    }

    actual fun e(tag: String, throwable: Throwable, message: String, vararg args: Any?) {
        Timber.tag(tag).e(throwable, message, *args)
    }
}

/**
 * Timber 日志扩展函数（使用 Logger 接口）
 */
object TimberExt {

    /**
     * DEBUG 日志
     */
    fun d(message: String, vararg args: Any?) {
        if (isDebugBuild()) {
            Timber.tag(Logger.TAG).d(message, *args)
        }
    }

    /**
     * INFO 日志
     */
    fun i(message: String, vararg args: Any?) {
        Timber.tag(Logger.TAG).i(message, *args)
    }

    /**
     * WARN 日志
     */
    fun w(message: String, vararg args: Any?) {
        Timber.tag(Logger.TAG).w(message, *args)
    }

    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.tag(Logger.TAG).w(throwable, message, *args)
    }

    /**
     * ERROR 日志
     */
    fun e(message: String, vararg args: Any?) {
        Timber.tag(Logger.TAG).e(message, *args)
    }

    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.tag(Logger.TAG).e(throwable, message, *args)
    }

    /**
     * 检查是否为 Debug 构建
     */
    private fun isDebugBuild(): Boolean {
        // TODO: 从 BuildConfig 获取
        return true
    }
}
