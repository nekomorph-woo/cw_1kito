package com.cw2.cw_1kito.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chat Completions API 响应模型
 *
 * @property id 响应 ID
 * @property `object` 对象类型,通常是 "chat.completion"
 * @property created 创建时间戳
 * @property model 使用的模型名称
 * @property choices 选择列表
 * @property usage Token 使用统计 (可选)
 */
@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    /**
     * 获取第一个选择的内容
     */
    val firstChoice: Choice?
        get() = choices.firstOrNull()

    /**
     * 获取第一条消息内容
     */
    val content: String?
        get() = firstChoice?.message?.content

    /**
     * 获取结束原因
     */
    val finishReason: String?
        get() = firstChoice?.finishReason

    /**
     * 选择项
     *
     * @property index 索引
     * @property message 消息内容
     * @property finishReason 结束原因: "stop" | "length" | "content_filter"
     */
    @Serializable
    data class Choice(
        val index: Int,
        val message: ChatMessage,
        @SerialName("finish_reason")
        val finishReason: String
    ) {
        companion object {
            /**
             * 正常结束
             */
            const val FINISH_REASON_STOP = "stop"

            /**
             * 达到最大长度
             */
            const val FINISH_REASON_LENGTH = "length"

            /**
             * 内容过滤
             */
            const val FINISH_REASON_CONTENT_FILTER = "content_filter"
        }
    }

    /**
     * Token 使用统计
     *
     * @property promptTokens 提示词 token 数
     * @property completionTokens 生成 token 数
     * @property totalTokens 总 token 数
     */
    @Serializable
    data class Usage(
        @SerialName("prompt_tokens")
        val promptTokens: Int,
        @SerialName("completion_tokens")
        val completionTokens: Int,
        @SerialName("total_tokens")
        val totalTokens: Int
    )
}
