# Google ML Kit 翻译语言包

## 概述

Google ML Kit Translation 提供本地翻译能力，无需网络连接。所有语言包已预装在 APK 中，无需用户下载。

## 已预装语言

以下语言包已打包到应用中，可直接使用：

| 语言 | 代码 | 大小 | 状态 |
|------|------|------|------|
| 中文（简体） | `zh` | ~75MB | 已预装 |
| 英语（美式） | `en` | ~75MB | 已预装 |
| 日语 | `ja` | ~75MB | 已预装 |
| 韩语 | `ko` | ~75MB | 已预装 |

### 总大小

约 **300MB**（4 种语言 × 75MB）

## 使用方式

### Kotlin API

```kotlin
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

// 创建翻译器
val options = TranslatorOptions.Builder()
    .setSourceLanguage(TranslateLanguage.JAPANESE)
    .setTargetLanguage(TranslateLanguage.CHINESE)
    .build()

val translator = Translation.getClient(options)

// 下载模型（由于已预装，会立即返回成功）
translator.downloadModelIfNeeded()
    .addOnSuccessListener {
        // 模型已准备好
    }
    .addOnFailureListener { exception ->
        // 处理错误
    }

// 执行翻译
translator.translate(text)
    .addOnSuccessListener { translatedText ->
        // 翻译成功
    }
    .addOnFailureListener { exception ->
        // 翻译失败
    }
```

## 性能特性

### 翻译速度（参考值）

| 设备性能 | 短文本（<50 字） | 长文本（>200 字） |
|----------|------------------|-------------------|
| 高端（旗舰级） | < 50ms | < 200ms |
| 中端（主流） | < 100ms | < 500ms |
| 低端（入门） | < 200ms | < 1000ms |

### 内存占用

- 单个语言包：~150MB（运行时）
- 多个语言包：~300-400MB（运行时）

## API 限制

### 无网络要求

- ✅ 完全离线运行
- ✅ 无需网络权限
- ✅ 无 API 调用费用

### 语言支持

完整支持的语言列表：
https://developers.google.com/ml-kit/language-support/translation

常用语言代码：

```kotlin
// TranslateLanguage 常量
TranslateLanguage.CHINESE    // zh
TranslateLanguage.ENGLISH     // en
TranslateLanguage.JAPANESE    // ja
TranslateLanguage.KOREAN      // ko
TranslateLanguage.FRENCH      // fr
TranslateLanguage.GERMAN     // de
TranslateLanguage.SPANISH     // es
TranslateLanguage.RUSSIAN     // ru
TranslateLanguage.PORTUGUESE  // pt
TranslateLanguage.ITALIAN     // it
```

## 版本信息

- **ML Kit Translation 版本**: 17.0.3
- **最后更新**: 2025-02-12

## 构建配置

### Gradle 依赖

```kotlin
// gradle/libs.versions.toml
[versions]
mlkit = "17.0.3"

[libraries]
google-mlkit-translation = { group = "com.google.mlkit", name = "translation", version.ref = "mlkit" }
```

```kotlin
// composeApp/build.gradle.kts
dependencies {
    implementation(libs.google.mlkit.translation)
}
```

### ProGuard 规则

ML Kit 已包含 ProGuard 规则，无需额外配置。

## 故障排除

### 模型文件损坏

如果遇到翻译错误，可能需要清除应用数据重新安装：

```bash
adb shell pm clear com.cw2.cw_1kito
```

### 内存不足

在低端设备上，如果同时加载多个翻译器可能导致 OOM：

```kotlin
// 使用完毕后释放资源
fun closeTranslator(translator: Translator) {
    translator.close()
}
```

### 语言包未预装

如果尝试使用未预装的语言，会触发下载。为避免网络流量，建议仅使用预装语言。

## 相关资源

- [官方文档](https://developers.google.com/ml-kit/translation/overview)
- [API 参考](https://developers.google.com/android/reference/com/google/mlkit/nl/translate/package-summary)
- [GitHub 示例](https://github.com/google/mlkit-samples)

## 注意事项

1. **APK 大小**: 预装语言包会显著增加 APK 大小（每个语言约 75MB）
2. **首次启动**: 首次创建翻译器可能需要 50-200ms 进行模型初始化
3. **并发翻译**: 建议使用同一个 Translator 实例进行批量翻译，避免重复初始化
4. **内存管理**: 使用完毕后调用 `close()` 释放内存

## 未来扩展

如需添加更多语言支持：

1. 修改 `composeApp/src/androidMain/res/values/mlkit_values.xml`
2. 添加新的语言代码到 `mlkit_preinstalled_languages` 数组
3. 重新构建 APK

注意：每增加一个语言，APK 大小约增加 75MB。
