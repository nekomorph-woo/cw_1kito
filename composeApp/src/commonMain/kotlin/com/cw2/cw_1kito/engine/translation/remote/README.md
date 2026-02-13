# SiliconFlowClient 使用示例

## 基本用法

### 1. 创建客户端实例

```kotlin
// 在 ViewModel 或 Service 中创建
val siliconFlowClient = SiliconFlowClient(configManager)

// 或者自定义 HTTP 客户端
val customHttpClient = HttpClient {
    // 自定义配置
}
val client = SiliconFlowClient(configManager, customHttpClient)
```

### 2. 设置 API Key

```kotlin
// 保存 API Key (会加密存储)
lifecycleScope.launch {
    siliconFlowClient.setApiKey("your-api-key-here")
}
```

### 3. 验证 API Key

```kotlin
lifecycleScope.launch {
    val isValid = siliconFlowClient.validateApiKey("your-api-key-here")
    if (isValid) {
        Logger.i("API Key 有效")
    } else {
        Logger.e("API Key 无效")
    }
}
```

### 4. 执行翻译

```kotlin
lifecycleScope.launch {
    try {
        val result = siliconFlowClient.translate(
            text = "Hello, world!",
            sourceLang = Language.EN,
            targetLang = Language.ZH
        )
        Logger.i("翻译结果: $result")
    } catch (e: ApiKeyInvalidException) {
        // API Key 无效
        Logger.e(e, "翻译失败: API Key 无效")
    } catch (e: RateLimitException) {
        // 配额超限
        Logger.e(e, "翻译失败: API 配额超限")
    } catch (e: NetworkException) {
        // 网络错误
        Logger.e(e, "翻译失败: 网络错误")
    } catch (e: TranslationTimeoutException) {
        // 超时
        Logger.e(e, "翻译失败: 请求超时")
    }
}
```

### 5. 释放资源

```kotlin
// 在 ViewModel 或 Service 的 onCleared() 中
override fun onCleared() {
    super.onCleared()
    siliconFlowClient.release()
}
```

## 在 TranslationManager 中集成

```kotlin
class TranslationManager(
    private val configManager: ConfigManager
) {
    private val remoteEngine = SiliconFlowClient(configManager)

    suspend fun translateText(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): AppResult<String> {
        return try {
            if (!remoteEngine.hasApiKey()) {
                return AppResult.Error(
                    AppError.TranslationError(
                        TranslationErrorCode.TRANSLATION_API_KEY_INVALID,
                        mapOf("reason" to "API Key 未设置")
                    )
                )
            }

            val result = remoteEngine.translate(text, sourceLang, targetLang)
            AppResult.Success(result)
        } catch (e: AppError) {
            AppResult.Error(e as AppError.TranslationError)
        } catch (e: Exception) {
            AppResult.Error(e.toAppError())
        }
    }

    fun release() {
        remoteEngine.release()
    }
}
```

## API 错误码对照

| HTTP 状态码 | 异常类型 | 说明 |
|------------|---------|------|
| 401 | ApiKeyInvalidException | API Key 无效或过期 |
| 429 | RateLimitException | API 调用频率超限 |
| 4xx | NetworkException | 客户端错误 |
| 5xx | NetworkException | 服务器错误 |
| Timeout | TranslationTimeoutException | 请求超时 |

## 配置参数

| 参数 | 默认值 | 说明 |
|-----|-------|------|
| DEFAULT_MODEL | Qwen/Qwen2.5-7B-Instruct | 使用的模型 |
| VALIDATION_TIMEOUT_MS | 10000 | 验证请求超时时间 (毫秒) |
| TRANSLATION_TIMEOUT_MS | 30000 | 翻译请求超时时间 (毫秒) |

## 注意事项

1. **API Key 存储**: API Key 会通过 ConfigManager 加密存储,无需手动管理
2. **超时设置**: 默认翻译超时 30 秒,可根据网络情况调整
3. **非流式模式**: 当前实现使用批量模式 (`stream = false`),等待完整响应
4. **资源释放**: 使用完毕后记得调用 `release()` 释放资源
5. **线程安全**: 客户端实例是线程安全的,可在多个协程中共享使用
