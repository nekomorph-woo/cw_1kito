package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * Chat API 消息模型
 *
 * @property role 消息角色: "system" | "user" | "assistant"
 * @property content 消息内容
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        /**
         * 系统角色
         */
        const val ROLE_SYSTEM = "system"

        /**
         * 用户角色
         */
        const val ROLE_USER = "user"

        /**
         * 助手角色
         */
        const val ROLE_ASSISTANT = "assistant"

        /**
         * 创建系统消息
         */
        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)

        /**
         * 创建用户消息
         */
        fun user(content: String) = ChatMessage(ROLE_USER, content)

        /**
         * 创建助手消息
         */
        fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
    }
}
