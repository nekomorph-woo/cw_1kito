package com.cw2.cw_1kito.engine.translation.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageConfig
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.ui.screen.LanguagePackPrompt
import com.cw2.cw_1kito.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 语言包启动检查器
 *
 * 在应用启动时检查本地翻译所需的语言包是否已下载。
 * 如果未下载，生成提示信息供 UI 层显示下载引导对话框。
 *
 * ## 检查逻辑
 * 1. 检查是否启用了本地 OCR 方案
 * 2. 检查翻译模式是否需要本地翻译（LOCAL 或 HYBRID）
 * 3. 检查当前语言对的语言包是否已下载
 * 4. 检查网络连接状态
 *
 * ## 使用方法
 * ```kotlin
 * val checker = LanguagePackStartupChecker(context, configManager, languagePackManager)
 * val prompt = checker.checkLanguagePacksOnStartup()
 * if (prompt != null) {
 *     // 显示下载引导对话框
 *     showLanguagePackGuideDialog(prompt)
 * }
 * ```
 *
 * @param context Android 上下文
 * @param configManager 配置管理器
 * @param languagePackManager 语言包管理器
 */
class LanguagePackStartupChecker(
    private val context: Context,
    private val configManager: ConfigManager,
    private val languagePackManager: ILanguagePackManager
) {

    companion object {
        private const val TAG = "LangPackStartup"
        private const val PREFS_NAME = "lang_pack_startup"
        private const val KEY_LAST_CHECK_VERSION = "last_check_version"
        private const val CURRENT_CHECK_VERSION = 1 // 检查逻辑版本号

        /**
         * 估算语言对大小（两个语言模型）
         */
        private fun estimateLanguagePairSize(source: Language, target: Language): String {
            val sourceSize = when (source) {
                Language.EN -> 15
                Language.ZH -> 25
                Language.JA -> 20
                Language.KO -> 18
                else -> 20
            }
            val targetSize = when (target) {
                Language.EN -> 15
                Language.ZH -> 25
                Language.JA -> 20
                Language.KO -> 18
                else -> 20
            }
            val totalMB = sourceSize + targetSize
            return "约 $totalMB MB"
        }
    }

    /**
     * 检查是否需要显示语言包下载引导
     *
     * 此方法执行以下检查：
     * 1. 是否启用了本地 OCR 方案
     * 2. 翻译模式是否需要本地翻译（LOCAL 或 HYBRID）
     * 3. 当前语言对的语言包是否已下载
     *
     * @return 如果需要下载，返回提示信息；否则返回 null
     */
    suspend fun checkLanguagePacksOnStartup(): LanguagePackPrompt? = withContext(Dispatchers.IO) {
        try {
            // 1. 检查是否启用本地 OCR
            val useLocalOcrScheme = configManager.getUseLocalOcrScheme()
            if (!useLocalOcrScheme) {
                Log.d(TAG, "未启用本地 OCR 方案，跳过语言包检查")
                return@withContext null
            }

            // 2. 检查翻译模式
            val translationMode = configManager.getLocalOcrTranslationMode()
            if (translationMode == TranslationMode.REMOTE) {
                Log.d(TAG, "翻译模式为 REMOTE（纯云端），跳过语言包检查")
                return@withContext null
            }

            // 3. 获取语言配置
            val languageConfig = configManager.getLanguageConfig()
            val sourceLanguage = languageConfig.sourceLanguage
            val targetLanguage = languageConfig.targetLanguage

            // 4. 检查语言是否被支持
            if (!MLKitLanguagePackManager.isLanguageSupported(sourceLanguage) ||
                !MLKitLanguagePackManager.isLanguageSupported(targetLanguage)) {
                Log.d(TAG, "语言对 ${sourceLanguage.displayName} -> ${targetLanguage.displayName} 不被本地翻译支持")
                return@withContext null
            }

            // 5. 检查语言包是否已下载
            val needsDownload = languagePackManager.needsDownload(sourceLanguage, targetLanguage)
            if (!needsDownload) {
                Log.d(TAG, "语言包已下载，无需提示")
                return@withContext null
            }

            // 6. 检查 Wi-Fi 状态
            val isWifiConnected = isWifiConnected()

            // 7. 估算下载大小
            val estimatedSize = estimateLanguagePairSize(sourceLanguage, targetLanguage)
            val languagePair = "${sourceLanguage.displayName} -> ${targetLanguage.displayName}"

            Logger.d("[LangPackStartup] 需要下载语言包：$languagePair，大小：$estimatedSize，Wi-Fi：$isWifiConnected")

            LanguagePackPrompt(
                languagePair = languagePair,
                estimatedSize = estimatedSize,
                isWifiConnected = isWifiConnected
            )

        } catch (e: Exception) {
            Logger.e(e, "[LangPackStartup] 检查语言包状态失败")
            null
        }
    }

    /**
     * 检查并下载当前配置的语言包
     *
     * 此方法用于响应用户点击下载按钮，执行实际的下载操作。
     *
     * @param requireWifi 是否要求 Wi-Fi 环境
     * @return 下载结果
     */
    suspend fun downloadCurrentLanguagePair(requireWifi: Boolean = true): DownloadResult {
        val languageConfig = configManager.getLanguageConfig()
        val sourceLanguage = languageConfig.sourceLanguage
        val targetLanguage = languageConfig.targetLanguage

        Logger.d("[LangPackStartup] 开始下载语言对：${sourceLanguage.displayName} -> ${targetLanguage.displayName}")

        return languagePackManager.downloadLanguagePair(
            sourceLang = sourceLanguage,
            targetLang = targetLanguage,
            requireWifi = requireWifi
        )
    }

    /**
     * 检查 Wi-Fi 是否连接
     */
    @Suppress("DEPRECATION")
    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Logger.w(e, "[LangPackStartup] 检查 Wi-Fi 状态失败")
            false
        }
    }

    /**
     * 标记语言包检查已完成（用于 SharedPreferences 记录）
     *
     * @param context Android 上下文
     */
    fun markCheckCompleted(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_LAST_CHECK_VERSION, CURRENT_CHECK_VERSION)
                .apply()
            Log.d(TAG, "标记语言包检查已完成")
        } catch (e: Exception) {
            Logger.w(e, "[LangPackStartup] 保存检查状态失败")
        }
    }

    /**
     * 检查是否应该执行首次启动检查
     *
     * @return 是否应该执行检查
     */
    fun shouldPerformStartupCheck(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheckVersion = prefs.getInt(KEY_LAST_CHECK_VERSION, 0)
            lastCheckVersion < CURRENT_CHECK_VERSION
        } catch (e: Exception) {
            Logger.w(e, "[LangPackStartup] 检查启动状态失败")
            true // 出错时默认执行检查
        }
    }

    /**
     * 清除检查记录（用于测试或强制重新检查）
     *
     * @param context Android 上下文
     */
    fun clearCheckRecord(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_LAST_CHECK_VERSION)
                .apply()
            Log.d(TAG, "清除语言包检查记录")
        } catch (e: Exception) {
            Logger.w(e, "[LangPackStartup] 清除检查状态失败")
        }
    }
}
