package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 模型池配置
 *
 * 管理用户配置的云端 LLM 翻译模型集合，用于分散 RPM 请求压力。
 *
 * @property models 模型列表，至少 1 个，最多 5 个
 * @property rpmLimit 每个模型的 RPM（每分钟请求数）限制，默认 500
 */
@Serializable
data class ModelPoolConfig(
    val models: List<LlmModel>,
    val rpmLimit: Int = 500
) {
    /**
     * 首选模型，列表中的第一个模型
     */
    val primaryModel: LlmModel get() = models.first()

    /**
     * 备用模型列表，除第一个外的所有模型
     */
    val backupModels: List<LlmModel> get() = models.drop(1)

    init {
        require(models.isNotEmpty()) { "模型池至少需要一个模型" }
        require(models.size <= 5) { "模型池最多支持5个模型" }
        require(models.distinct().size == models.size) { "模型池中不能有重复模型" }
    }

    companion object {
        /**
         * 默认模型池配置，仅包含默认的 Qwen2.5-7B 模型
         */
        val DEFAULT = ModelPoolConfig(
            models = listOf(LlmModel.DEFAULT)
        )
    }
}
