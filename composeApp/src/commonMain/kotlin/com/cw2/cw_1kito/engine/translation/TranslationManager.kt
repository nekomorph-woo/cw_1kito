package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.engine.translation.local.ILocalTranslationEngine
import com.cw2.cw_1kito.engine.translation.remote.IRemoteTranslationEngine
import com.cw2.cw_1kito.error.LocalTranslationFailedException
import com.cw2.cw_1kito.error.RemoteTranslationFailedException
import com.cw2.cw_1kito.error.TranslationException
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 翻译管理器
 *
 * 统一管理本地和云端翻译引擎，支持三种翻译模式：
 * - **LOCAL**: 仅使用本地翻译（ML Kit）
 * - **REMOTE**: 仅使用云端翻译（SiliconFlow）
 * - **HYBRID**: 优先本地，失败时降级到云端
 *
 * ## 核心功能
 * 1. **模式切换**: 根据配置自动选择翻译引擎
 * 2. **结果缓存**: LRU 缓存，最多 200 条记录
 * 3. **降级策略**: 本地失败自动切换到云端（HYBRID 模式）
 * 4. **错误处理**: 统一的错误处理和日志记录
 *
 * ## 使用示例
 * ```kotlin
 * val manager = TranslationManager(
 *     configManager = configManager,
 *     localEngine = MLKitTranslator(context),
 *     remoteEngine = SiliconFlowClient(configManager)
 * )
 *
 * // 初始化
 * manager.initialize()
 *
 * // 翻译文本
 * val result = manager.translate(
 *     text = "Hello World",
 *     sourceLang = Language.ENGLISH,
 *     targetLang = Language.CHINESE
 * )
 * // result = "你好世界"
 *
 * // 释放资源
 * manager.release()
 * ```
 *
 * @param configManager 配置管理器
 * @param localEngine 本地翻译引擎（可选，LOCAL/HYBRID 模式需要）
 * @param remoteEngine 远程翻译引擎（可选，REMOTE/HYBRID 模式需要）
 *
 * @constructor 创建翻译管理器实例
 */
class TranslationManager(
    private val configManager: ConfigManager,
    private val localEngine: ILocalTranslationEngine? = null,
    private val remoteEngine: IRemoteTranslationEngine? = null
) {

    companion object {
        private const val TAG = "TranslationManager"

        /**
         * 缓存最大容量
         */
        private const val MAX_CACHE_SIZE = 200

        /**
         * 缓存键模板
         */
        private const val CACHE_KEY_TEMPLATE = "%s-%s-%s"

        /**
         * 哈希算法（用于长文本缓存键）
         */
        private const val HASH_ALGORITHM = "SHA-256"
    }

    /**
     * LRU 缓存
     *
     * 使用 LinkedHashMap 实现 LRU 淘汰策略：
     * - accessOrder = true：按访问顺序排序
     * - removeEldestEntry()：超过容量时删除最旧的条目
     */
    private val translationCache = object : LinkedHashMap<String, String>(
        MAX_CACHE_SIZE,
        0.75f,
        true // accessOrder = true（LRU）
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean {
            val removed = size > MAX_CACHE_SIZE
            if (removed) {
                Logger.d("[TranslationManager] 缓存已满，淘汰最旧条目: ${eldest?.key?.take(50)}...")
            }
            return removed
        }
    }

    /**
     * 缓存锁（保护并发访问）
     */
    private val cacheLock = Mutex()

    /**
     * 初始化翻译管理器
     *
     * 根据翻译模式初始化相应的引擎：
     * - LOCAL: 初始化本地引擎
     * - REMOTE: 不需要初始化（云端引擎即插即用）
     * - HYBRID: 初始化本地引擎（云端按需使用）
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean {
        return try {
            Logger.d("[TranslationManager] 正在初始化翻译管理器...")

            // 获取翻译模式
            val mode = configManager.getTranslationMode()
            Logger.d("[TranslationManager] 翻译模式: ${mode.displayName}")

            // 根据模式初始化引擎
            val success = when (mode) {
                TranslationMode.LOCAL -> {
                    require(localEngine != null) {
                        "LOCAL 模式需要提供本地翻译引擎"
                    }
                    Logger.d("[TranslationManager] 初始化本地翻译引擎...")
                    localEngine.initialize()
                }

                TranslationMode.REMOTE -> {
                    require(remoteEngine != null) {
                        "REMOTE 模式需要提供远程翻译引擎"
                    }
                    Logger.d("[TranslationManager] 远程翻译引擎无需初始化")
                    true
                }

                TranslationMode.HYBRID -> {
                    require(localEngine != null && remoteEngine != null) {
                        "HYBRID 模式需要同时提供本地和远程翻译引擎"
                    }
                    Logger.d("[TranslationManager] 初始化本地翻译引擎（云端按需）...")
                    localEngine.initialize()
                }
            }

            if (success) {
                Logger.i("[TranslationManager] 翻译管理器初始化成功")
                // 记录缓存状态
                logCacheStatus()
            } else {
                Logger.e("[TranslationManager] 翻译管理器初始化失败")
            }

            success
        } catch (e: Exception) {
            Logger.e(e, "[TranslationManager] 初始化失败")
            false
        }
    }

    /**
     * 翻译文本
     *
     * 根据当前翻译模式选择合适的引擎：
     * - **LOCAL**: 直接使用本地引擎
     * - **REMOTE**: 直接使用云端引擎
     * - **HYBRID**: 优先本地，失败时降级到云端
     *
     * ## 缓存策略
     * 1. 生成缓存键：源语言-目标语言-文本哈希
     * 2. 查询缓存，命中则直接返回
     * 3. 未命中则执行翻译，写入缓存
     *
     * @param text 待翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     * @throws TranslationException 翻译失败（所有引擎都失败）
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        // 边界检查
        if (text.isBlank()) {
            Logger.w("[TranslationManager] 输入文本为空，返回空字符串")
            return ""
        }

        // 1. 尝试从缓存读取
        val cacheKey = buildCacheKey(text, sourceLang, targetLang)
        cacheLock.withLock {
            translationCache[cacheKey]?.let { cachedResult ->
                Logger.d("[TranslationManager] 缓存命中: ${text.take(30)}... -> ${cachedResult.take(30)}...")
                Logger.cacheHit()
                return cachedResult
            }
        }
        Logger.cacheMiss()

        // 2. 执行翻译（根据模式选择引擎）
        val startTime = System.currentTimeMillis()
        val mode = configManager.getTranslationMode()

        Logger.translationStart(text.length, sourceLang.code, targetLang.code)

        val result = try {
            when (mode) {
                TranslationMode.LOCAL -> translateLocal(text, sourceLang, targetLang)
                TranslationMode.REMOTE -> translateRemote(text, sourceLang, targetLang)
                TranslationMode.HYBRID -> translateHybrid(text, sourceLang, targetLang)
            }
        } catch (e: TranslationException) {
            Logger.e(e, "[TranslationManager] 翻译失败: ${text.take(30)}...")
            throw e
        }

        // 3. 写入缓存
        cacheLock.withLock {
            translationCache[cacheKey] = result
        }

        val elapsed = System.currentTimeMillis() - startTime
        Logger.translationSuccess(text.length, elapsed)

        // 4. 记录缓存状态（每 10 次记录一次）
        if (translationCache.size % 10 == 0) {
            logCacheStatus()
        }

        return result
    }

    /**
     * 本地翻译
     *
     * @param text 待翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     * @throws LocalTranslationFailedException 本地翻译失败
     */
    private suspend fun translateLocal(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            Logger.d("[TranslationManager] 使用本地翻译引擎")
            localEngine?.translate(text, sourceLang, targetLang)
                ?: throw LocalTranslationFailedException("本地翻译引擎未初始化")
        } catch (e: Exception) {
            when (e) {
                is LocalTranslationFailedException -> throw e
                is TranslationException -> throw e
                else -> throw LocalTranslationFailedException("本地翻译失败: ${e.message}", e)
            }
        }
    }

    /**
     * 云端翻译
     *
     * @param text 待翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     * @throws RemoteTranslationFailedException 云端翻译失败
     */
    private suspend fun translateRemote(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            Logger.d("[TranslationManager] 使用云端翻译引擎")
            remoteEngine?.translate(text, sourceLang, targetLang)
                ?: throw RemoteTranslationFailedException("云端翻译引擎未初始化")
        } catch (e: Exception) {
            when (e) {
                is RemoteTranslationFailedException -> throw e
                is TranslationException -> throw e
                else -> throw RemoteTranslationFailedException("云端翻译失败: ${e.message}", e)
            }
        }
    }

    /**
     * 混合翻译
     *
     * 优先使用本地翻译，失败时降级到云端。
     *
     * ## 降级策略
     * 1. 尝试本地翻译
     * 2. 本地失败 → 记录警告，切换到云端
     * 3. 云端成功 → 返回结果
     * 4. 云端也失败 → 抛出异常
     *
     * @param text 待翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     * @throws TranslationException 本地和云端都失败
     */
    private suspend fun translateHybrid(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            Logger.d("[TranslationManager] 尝试本地翻译（HYBRID 模式）")
            translateLocal(text, sourceLang, targetLang)
        } catch (localError: Exception) {
            Logger.w(localError, "[TranslationManager] 本地翻译失败，降级到云端: ${text.take(30)}...")
            try {
                translateRemote(text, sourceLang, targetLang)
            } catch (remoteError: Exception) {
                Logger.e(remoteError, "[TranslationManager] 云端翻译也失败")
                throw TranslationException("本地和云端翻译都失败", remoteError)
            }
        }
    }

    /**
     * 释放所有资源
     *
     * 清理步骤：
     * 1. 释放本地引擎
     * 2. 释放云端引擎
     * 3. 清空缓存
     */
    fun release() {
        try {
            Logger.d("[TranslationManager] 正在释放资源...")

            // 释放本地引擎
            localEngine?.release()
            Logger.d("[TranslationManager] 本地翻译引擎已释放")

            // 释放云端引擎
            remoteEngine?.release()
            Logger.d("[TranslationManager] 云端翻译引擎已释放")

            // 清空缓存（直接清空，LinkedHashMap.clear() 是同步的）
            val cacheSize = translationCache.size
            translationCache.clear()
            Logger.d("[TranslationManager] 缓存已清空（$cacheSize 条）")

            Logger.i("[TranslationManager] 资源释放完成")
        } catch (e: Exception) {
            Logger.e(e, "[TranslationManager] 资源释放失败")
        }
    }

    /**
     * 清空翻译缓存
     *
     * 手动清除所有缓存的翻译结果。
     */
    suspend fun clearCache() {
        cacheLock.withLock {
            val size = translationCache.size
            translationCache.clear()
            Logger.i("[TranslationManager] 缓存已清空（$size 条）")
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存大小
     */
    suspend fun getCacheSize(): Int {
        return cacheLock.withLock { translationCache.size }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建缓存键
     *
     * 格式：`源语言代码-目标语言代码-文本内容`
     *
     * 示例：
     * - `en-zh-Hello World`
     * - `zh-en-你好世界`
     *
     * 注意：对于超长文本（> 100 字符），使用 SHA-256 哈希避免键过长
     *
     * @param text 文本内容
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 缓存键
     */
    private fun buildCacheKey(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        val textPart = if (text.length > 100) {
            // 超长文本使用哈希
            text.sha256()
        } else {
            text
        }

        return String.format(CACHE_KEY_TEMPLATE, sourceLang.code, targetLang.code, textPart)
    }

    /**
     * 记录缓存状态
     */
    private fun logCacheStatus() {
        val size = translationCache.size
        val usage = (size.toFloat() / MAX_CACHE_SIZE * 100).toInt()
        Logger.d("[TranslationManager] 缓存状态: $size/$MAX_CACHE_SIZE ($usage%)")
    }

    /**
     * 字符串 SHA-256 哈希（用于长文本缓存键）
     */
    private fun String.sha256(): String {
        return try {
            val md = java.security.MessageDigest.getInstance(HASH_ALGORITHM)
            val hash = md.digest(this.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 降级：使用 Java hashCode
            Logger.w(e, "[TranslationManager] SHA-256 失败，降级到 hashCode")
            this.hashCode().toString()
        }
    }

    /**
     * 检查管理器是否已初始化
     */
    suspend fun isInitialized(): Boolean {
        val mode = configManager.getTranslationMode()
        return when (mode) {
            TranslationMode.LOCAL -> localEngine?.isInitialized() == true
            TranslationMode.REMOTE -> remoteEngine != null
            TranslationMode.HYBRID -> localEngine?.isInitialized() == true && remoteEngine != null
        }
    }

    /**
     * 获取当前翻译模式
     */
    suspend fun getCurrentMode(): TranslationMode {
        return configManager.getTranslationMode()
    }

    /**
     * 设置翻译模式
     *
     * 注意：修改模式后可能需要重新初始化
     *
     * @param mode 新的翻译模式
     */
    suspend fun setMode(mode: TranslationMode) {
        configManager.saveTranslationMode(mode)
        Logger.i("[TranslationManager] 翻译模式已更改为: ${mode.displayName}")
    }
}
