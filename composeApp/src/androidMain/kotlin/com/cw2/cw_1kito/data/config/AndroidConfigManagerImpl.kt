package com.cw2.cw_1kito.data.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 平台的配置管理器实现
 * 使用 SharedPreferences 持久化配置
 */
class AndroidConfigManagerImpl(
    context: Context
) : ConfigManagerImpl() {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    override suspend fun saveApiKeyInternal(apiKey: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString("api_key", apiKey).apply()
        }
    }

    override suspend fun getApiKeyInternal(): String? {
        return withContext(Dispatchers.IO) {
            prefs.getString("api_key", null)
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
