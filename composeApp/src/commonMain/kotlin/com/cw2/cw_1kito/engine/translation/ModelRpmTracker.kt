package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.model.LlmModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 模型 RPM 追踪器（滑动窗口实现）
 *
 * 追踪每个模型在指定时间窗口内的请求次数，用于本地限流控制。
 *
 * @property rpmLimit RPM 限制，默认 500
 * @property windowSizeMs 时间窗口大小（毫秒），默认 60000（60秒）
 */
class ModelRpmTracker(
    private val rpmLimit: Int = 500,
    private val windowSizeMs: Long = 60_000L
) {
    // 每个模型的请求时间戳队列
    private val requestTimestamps = mutableMapOf<LlmModel, ArrayDeque<Long>>()
    private val lock = Mutex()

    /**
     * 检查模型是否可用（未达到RPM限制）
     */
    suspend fun isAvailable(model: LlmModel): Boolean {
        return lock.withLock {
            val currentCount = getCleanedCount(model)
            currentCount < rpmLimit
        }
    }

    /**
     * 获取模型当前RPM使用量
     */
    suspend fun getCurrentRpm(model: LlmModel): Int {
        return lock.withLock {
            getCleanedCount(model)
        }
    }

    /**
     * 记录一次请求
     */
    suspend fun recordRequest(model: LlmModel) {
        lock.withLock {
            val timestamps = requestTimestamps.getOrPut(model) { ArrayDeque() }
            timestamps.addLast(System.currentTimeMillis())
        }
    }

    /**
     * 获取模型池中RPM最充裕的可用模型
     * @return RPM使用量最少的可用模型，如果全部不可用则返回null
     */
    suspend fun getMostAvailableModel(modelPool: List<LlmModel>): LlmModel? {
        return lock.withLock {
            modelPool
                .filter { getCleanedCount(it) < rpmLimit }
                .minByOrNull { getCleanedCount(it) }
        }
    }

    /**
     * 获取下一个可用时间（毫秒）
     * 用于计算需要等待多久才有配额
     */
    suspend fun getNextAvailableTime(model: LlmModel): Long? {
        return lock.withLock {
            val timestamps = requestTimestamps[model] ?: return null
            if (timestamps.isEmpty() || timestamps.size < rpmLimit) return null

            val now = System.currentTimeMillis()
            val oldestTimestamp = timestamps.first()
            val waitTime = oldestTimestamp + windowSizeMs - now
            if (waitTime > 0) waitTime else null
        }
    }

    /**
     * 清理过期时间戳并返回当前计数
     */
    private fun getCleanedCount(model: LlmModel): Int {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps.getOrPut(model) { ArrayDeque() }

        // 移除60秒前的记录
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowSizeMs) {
            timestamps.removeFirst()
        }

        return timestamps.size
    }
}
