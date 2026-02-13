package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.model.ModelPoolConfig
import kotlinx.coroutines.delay

/**
 * 模型选择器 - 负责从模型池中选择合适的模型
 *
 * 实现优先首选、故障转移的选择策略。
 *
 * @property modelPoolConfig 模型池配置
 * @property rpmTracker RPM 追踪器
 */
class ModelSelector(
    private val modelPoolConfig: ModelPoolConfig,
    private val rpmTracker: ModelRpmTracker
) {
    /**
     * 选择一个可用模型
     * @return 选中的模型，如果全部不可用则返回null
     */
    suspend fun selectModel(): LlmModel? {
        // 1. 优先尝试首选模型
        val primaryModel = modelPoolConfig.primaryModel
        if (rpmTracker.isAvailable(primaryModel)) {
            return primaryModel
        }

        // 2. 首选模型限流，尝试故障转移到备用模型
        val backupModels = modelPoolConfig.backupModels
        if (backupModels.isNotEmpty()) {
            val availableBackup = rpmTracker.getMostAvailableModel(backupModels)
            if (availableBackup != null) {
                return availableBackup
            }
        }

        // 3. 所有模型都限流，返回null由调用方决定策略
        return null
    }

    /**
     * 选择模型，如果全部不可用则等待
     * @param maxWaitMs 最大等待时间（毫秒）
     * @return 选中的模型
     */
    suspend fun selectModelWithWait(maxWaitMs: Long = 5000): LlmModel? {
        val selected = selectModel()
        if (selected != null) return selected

        // 全部限流，计算最短等待时间
        val allModels = modelPoolConfig.models
        val waitInfo = allModels.mapNotNull { model ->
            rpmTracker.getNextAvailableTime(model)?.let { model to it }
        }.minByOrNull { it.second }

        if (waitInfo != null && waitInfo.second <= maxWaitMs) {
            delay(waitInfo.second)
            return waitInfo.first
        }

        return null
    }
}
