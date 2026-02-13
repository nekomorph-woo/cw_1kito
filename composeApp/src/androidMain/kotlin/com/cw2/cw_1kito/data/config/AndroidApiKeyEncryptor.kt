package com.cw2.cw_1kito.data.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cw2.cw_1kito.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 平台的 API Key 加密工具
 *
 * 使用 Android Jetpack Security 库的 EncryptedSharedPreferences
 * 实现 API Key 的安全存储。
 *
 * ## 技术方案
 * - MasterKey: 使用 Android Keystore 系统
 * - 加密算法: AES256-GCM
 * - 存储位置: EncryptedSharedPreferences
 *
 * @property context 应用上下文
 *
 * @constructor 创建加密工具实例
 */
class AndroidApiKeyEncryptor(
    private val context: Context
) : ApiKeyEncryptor {

    companion object {
        private const val PREFS_FILE_NAME = "encrypted_api_key_store"
        private const val KEY_ALIAS = "api_key_master_key"
        private const val ENCRYPTED_KEY = "encrypted_api_key"
    }

    /**
     * MasterKey for EncryptedSharedPreferences
     * 使用 Android Keystore 系统保护主密钥
     */
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

    /**
     * EncryptedSharedPreferences 实例
     */
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 加密并保存 API Key
     *
     * 使用 EncryptedSharedPreferences 自动加密存储。
     *
     * @param apiKey 原始 API Key
     * @return 加密后的字符串 (存储标识)
     */
    override suspend fun encrypt(apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .putString(ENCRYPTED_KEY, apiKey)
                .apply()

            Logger.d("[ApiKeyEncryptor] API Key 已加密存储")
            ENCRYPTED_KEY  // 返回存储键作为标识

        } catch (e: Exception) {
            Logger.e(e, "[ApiKeyEncryptor] 加密存储失败")
            throw SecurityException("无法加密存储 API Key", e)
        }
    }

    /**
     * 解密并获取 API Key
     *
     * 从 EncryptedSharedPreferences 读取并自动解密。
     *
     * @param encryptedApiKey 加密的 API Key (存储标识)
     * @return 原始 API Key,如果不存在则返回 null
     */
    override suspend fun decrypt(encryptedApiKey: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = encryptedPrefs.getString(ENCRYPTED_KEY, null)

            if (apiKey != null) {
                Logger.d("[ApiKeyEncryptor] API Key 解密成功")
            } else {
                Logger.w("[ApiKeyEncryptor] 未找到存储的 API Key")
            }

            apiKey ?: throw SecurityException("API Key 不存在")

        } catch (e: Exception) {
            Logger.e(e, "[ApiKeyEncryptor] 解密失败")
            throw SecurityException("无法解密 API Key", e)
        }
    }

    /**
     * 删除存储的 API Key
     */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .remove(ENCRYPTED_KEY)
                .apply()

            Logger.d("[ApiKeyEncryptor] API Key 已清除")

        } catch (e: Exception) {
            Logger.e(e, "[ApiKeyEncryptor] 清除失败")
        }
    }

    /**
     * 检查是否存在 API Key
     */
    suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.contains(ENCRYPTED_KEY)
        } catch (e: Exception) {
            Logger.e(e, "[ApiKeyEncryptor] 检查失败")
            false
        }
    }
}
