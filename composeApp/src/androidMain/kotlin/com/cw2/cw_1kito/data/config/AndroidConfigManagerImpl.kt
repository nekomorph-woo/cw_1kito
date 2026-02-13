package com.cw2.cw_1kito.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cw2.cw_1kito.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 平台的配置管理器实现
 *
 * ## 存储策略
 * - API Key: 使用 EncryptedSharedPreferences (Android Keystore)
 * - 其他配置: 使用普通 SharedPreferences
 *
 * ## 安全性
 * - API Key 使用 AES256-GCM 加密
 * - MasterKey 存储在 Android Keystore 系统中
 * - 密钥由操作系统保护,无法导出
 */
class AndroidConfigManagerImpl(
    context: Context
) : ConfigManagerImpl() {

    // 普通 SharedPreferences (用于非敏感配置)
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    // EncryptedSharedPreferences (用于 API Key)
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "encrypted_api_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Logger.e(e, "[ConfigManager] 创建加密存储失败,使用普通存储")
            // 降级到普通存储
            context.getSharedPreferences("encrypted_api_store", Context.MODE_PRIVATE)
        }
    }

    override suspend fun saveApiKeyInternal(apiKey: String) {
        withContext(Dispatchers.IO) {
            try {
                encryptedPrefs.edit().putString("api_key", apiKey).apply()
                Logger.d("[ConfigManager] API Key 已加密保存")
            } catch (e: Exception) {
                Logger.e(e, "[ConfigManager] API Key 保存失败")
                throw e
            }
        }
    }

    override suspend fun getApiKeyInternal(): String? {
        return withContext(Dispatchers.IO) {
            try {
                encryptedPrefs.getString("api_key", null)
            } catch (e: Exception) {
                Logger.e(e, "[ConfigManager] API Key 读取失败")
                null
            }
        }
    }

    override suspend fun saveString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(key, value).apply()
        }
    }

    override suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) {
            prefs.getString(key, null)
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(key).apply()
        }
    }
}
