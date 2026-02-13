package com.cw2.cw_1kito.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chat Completions API 请求模型
 *
 * @property model 模型名称,例如 "Qwen/Qwen2.5-7B-Instruct"
 * @property messages 对话消息列表
 * @property temperature 温度参数 (0.0-2.0),控制随机性
 * @property max_tokens 最大生成 token 数
 * @property stream 是否流式返回
 * @property top_p Top-p 采样参数 (0.0-1.0)
 * @property enable_thinking 是否启用思考模式（Thinking 模型需要设为 false）
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
    val stream: Boolean = false,
    @SerialName("top_p")
    val topP: Double = 0.9,
    @SerialName("enable_thinking")
    val enableThinking: Boolean? = null
) {
    /**
     * 构建器类
     */
    class Builder {
        private var model: String = ""
        private val messages: MutableList<ChatMessage> = mutableListOf()
        private var temperature: Double = 0.7
        private var maxTokens: Int = 2000
        private var stream: Boolean = false
        private var topP: Double = 0.9
        private var enableThinking: Boolean? = null

        /**
         * 设置模型
         */
        fun model(model: String) = apply { this.model = model }

        /**
         * 添加消息
         */
        fun addMessage(message: ChatMessage) = apply { messages.add(message) }

        /**
         * 添加多条消息
         */
        fun addMessages(messages: List<ChatMessage>) = apply {
            this.messages.addAll(messages)
        }

        /**
         * 设置温度
         */
        fun temperature(temperature: Double) = apply {
            require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
            this.temperature = temperature
        }

        /**
         * 设置最大 token 数
         */
        fun maxTokens(maxTokens: Int) = apply {
            require(maxTokens > 0) { "Max tokens must be positive" }
            this.maxTokens = maxTokens
        }

        /**
         * 设置是否流式返回
         */
        fun stream(stream: Boolean) = apply { this.stream = stream }

        /**
         * 设置 top_p
         */
        fun topP(topP: Double) = apply {
            require(topP in 0.0..1.0) { "Top-p must be between 0.0 and 1.0" }
            this.topP = topP
        }

        /**
         * 设置思考模式（Thinking 模型需要设为 false）
         */
        fun enableThinking(enable: Boolean) = apply { this.enableThinking = enable }

        /**
         * 构建请求
         */
        fun build(): ChatCompletionRequest {
            require(model.isNotBlank()) { "Model must be specified" }
            require(messages.isNotEmpty()) { "At least one message is required" }

            return ChatCompletionRequest(
                model = model,
                messages = messages.toList(),
                temperature = temperature,
                maxTokens = maxTokens,
                stream = stream,
                topP = topP,
                enableThinking = enableThinking
            )
        }
    }

    companion object {
        /**
         * 创建构建器
         */
        fun builder() = Builder()

        /**
         * 快速创建简单请求
         */
        fun create(
            model: String,
            userMessage: String,
            temperature: Double = 0.7,
            maxTokens: Int = 2000
        ): ChatCompletionRequest {
            return builder()
                .model(model)
                .addMessage(ChatMessage.user(userMessage))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build()
        }
    }
}
