package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 翻译模式
 * 控制使用本地还是云端翻译引擎
 */
@Serializable
enum class TranslationMode {
    /** 仅使用 Google ML Kit 本地翻译 */
    LOCAL,

    /** 仅使用硅基流动 API 云端翻译 */
    REMOTE,

    /** 优先本地，失败时降级到云端 */
    HYBRID;

    /** 显示名称 */
    val displayName: String
        get() = when (this) {
            LOCAL -> "仅本地"
            REMOTE -> "仅云端"
            HYBRID -> "混合模式"
        }

    /** 描述 */
    val description: String
        get() = when (this) {
            LOCAL -> "使用设备本地翻译，需要下载语言包，无网络消耗"
            REMOTE -> "使用云端大模型翻译，支持更多语言和质量更高"
            HYBRID -> "优先使用本地翻译，失败时自动切换到云端"
        }

    /** 是否允许使用本地翻译 */
    val allowLocal: Boolean
        get() = this == LOCAL || this == HYBRID

    /** 是否允许使用云端翻译 */
    val allowRemote: Boolean
        get() = this == REMOTE || this == HYBRID

    companion object {
        /** 默认翻译模式（本地优先） */
        val DEFAULT = HYBRID

        /**
         * 从显示名称获取翻译模式
         */
        fun fromDisplayName(displayName: String): TranslationMode? {
            return values().find { it.displayName == displayName }
        }
    }
}
