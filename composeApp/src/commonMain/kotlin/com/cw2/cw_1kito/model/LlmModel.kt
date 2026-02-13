package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 云端 LLM 模型枚举
 *
 * 支持硅基流动平台的 19 个 LLM 模型，分为普通模型和 Thinking 模型两类。
 * Thinking 模型需要在请求中设置 enable_thinking=false 参数。
 *
 * @property modelId 模型标识符（API 调用时使用的 ID）
 * @property displayName 显示名称（用于 UI 展示）
 * @property isThinkingModel 是否为 Thinking 模型（需要特殊参数处理）
 */
@Serializable
enum class LlmModel(
    val modelId: String,
    val displayName: String,
    val isThinkingModel: Boolean = false
) {
    // ==================== 普通模型（9个）====================
    /**
     * Qwen2.5 7B - 快速轻量级模型
     */
    QWEN2_5_7B("Qwen/Qwen2.5-7B-Instruct", "Qwen2.5-7B", false),

    /**
     * Qwen2.5 72B - 高性能大模型
     */
    QWEN2_5_72B("Qwen/Qwen2.5-72B-Instruct", "Qwen2.5-72B", false),

    /**
     * Qwen3 8B - 新一代基础模型
     */
    QWEN3_8B("Qwen/Qwen3-8B", "Qwen3-8B", false),

    /**
     * GLM-4 9B - 智谱 AI 9B 参数模型
     */
    GLM_4_9B("THUDM/glm-4-9b-0414", "GLM-4-9B", false),

    /**
     * GLM-4 32B - 智谱 AI 32B 参数模型
     */
    GLM_4_32B("THUDM/GLM-4-32B-0414", "GLM-4-32B", false),

    /**
     * GLM-4.5 - 智谱 AI 4.5 版本
     */
    GLM_4_5("THUDM/GLM-4.5-0414", "GLM-4.5", false),

    /**
     * DeepSeek V3 - DeepSeek 第三代模型
     */
    DEEPSEEK_V3("deepseek-ai/DeepSeek-V3", "DeepSeek-V3", false),

    /**
     * DeepSeek R1 8B - DeepSeek R1 轻量版（Pro 镜像）
     */
    DEEPSEEK_R1_8B("Pro/DeepSeek-R1-0528-Qwen3-8B", "DeepSeek-R1-8B", false),

    /**
     * DeepSeek R1 - DeepSeek 推理模型
     */
    DEEPSEEK_R1("deepseek-ai/DeepSeek-R1", "DeepSeek-R1", false),

    // ==================== Thinking 模型（10个）====================
    // Thinking 模型需要在请求中设置 enable_thinking=false 参数

    /**
     * Qwen3 30B - Thinking 模型
     */
    QWEN3_30B("Qwen/Qwen3-30B-A3B-Instruct", "Qwen3-30B (Thinking)", true),

    /**
     * Qwen3 235B - Thinking 大模型
     */
    QWEN3_235B("Qwen/Qwen3-235B-A22B-Instruct", "Qwen3-235B (Thinking)", true),

    /**
     * Qwen2.5 32B - Thinking 模型
     */
    QWEN2_5_32B("Qwen/Qwen2.5-32B-Instruct", "Qwen2.5-32B (Thinking)", true),

    /**
     * Qwen2.5 32B - Pro 版本
     */
    QWEN2_5_32B_PRO("Pro/Qwen/Qwen2.5-32B-Instruct", "Qwen2.5-32B Pro (Thinking)", true),

    /**
     * GLM-Z1 9B - 智谱 Z1 系列轻量版
     */
    GLM_Z1_9B("THUDM/GLM-Z1-9B-0414", "GLM-Z1-9B (Thinking)", true),

    /**
     * GLM-Z1 32B - 智谱 Z1 系列标准版
     */
    GLM_Z1_32B("THUDM/GLM-Z1-32B-0414", "GLM-Z1-32B (Thinking)", true),

    /**
     * GLM-Z1 32B - Pro 版本
     */
    GLM_Z1_32B_PRO("Pro/THUDM/GLM-Z1-32B-0414", "GLM-Z1-32B Pro (Thinking)", true),

    /**
     * DeepSeek R1 Distill 32B - 蒸馏版
     */
    DEEPSEEK_R1_DISTILL_32B("deepseek-ai/DeepSeek-R1-Distill-Qwen-32B", "DeepSeek-R1-Distill-32B (Thinking)", true),

    /**
     * DeepSeek R1 Distill 32B - Pro 版本
     */
    DEEPSEEK_R1_DISTILL_32B_PRO("Pro/deepseek-ai/DeepSeek-R1-Distill-Qwen-32B", "DeepSeek-R1-Distill-32B Pro (Thinking)", true),

    /**
     * DeepSeek V3 0324 - DeepSeek V3 早期版本
     */
    DEEPSEEK_V3_0324("deepseek-ai/DeepSeek-V3-0324", "DeepSeek-V3-0324 (Thinking)", true);

    companion object {
        /**
         * 默认模型 - 选择性能和速度平衡的 Qwen2.5-7B
         */
        val DEFAULT = QWEN2_5_7B

        /**
         * 所有普通模型列表
         */
        val normalModels: List<LlmModel>
            get() = values().filter { !it.isThinkingModel }

        /**
         * 所有 Thinking 模型列表
         */
        val thinkingModels: List<LlmModel>
            get() = values().filter { it.isThinkingModel }

        /**
         * 根据 modelId 查找模型
         */
        fun fromModelId(modelId: String): LlmModel? {
            return values().find { it.modelId == modelId }
        }

        /**
         * 获取推荐的普通模型（按性能排序）
         */
        val recommendedNormalModels: List<LlmModel>
            get() = listOf(
                QWEN2_5_7B,      // 快速轻量
                QWEN2_5_72B,     // 高性能
                QWEN3_8B,        // 新一代
                GLM_4_9B,        // 智谱轻量
                GLM_4_5,         // 智谱4.5
                DEEPSEEK_V3      // DeepSeek
            )

        /**
         * 获取推荐的 Thinking 模型（按性能排序）
         */
        val recommendedThinkingModels: List<LlmModel>
            get() = listOf(
                QWEN3_30B,               // 新一代中等规模
                GLM_Z1_32B,              // 智谱 Z1
                QWEN2_5_32B,             // Qwen2.5 32B
                DEEPSEEK_R1_DISTILL_32B  // DeepSeek 蒸馏版
            )
    }
}
