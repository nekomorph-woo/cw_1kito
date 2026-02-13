# 本地 OCR 翻译系统 - 依赖集成总结

## 完成日期

2025-02-12

## 已完成任务

### 任务 13：集成 Google ML Kit Text Recognition 依赖 ✅

#### 1. 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `gradle/libs.versions.toml` | 添加 `mlkit = "19.0.0"` 版本和库声明 |
| `composeApp/build.gradle.kts` | 添加 `implementation(libs.google.mlkit.text.recognition)` 依赖 |

#### 2. 创建的文件

| 文件 | 说明 |
|------|------|
| `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/engine/ocr/MLKitOCRManager.kt` | ML Kit OCR 引擎管理器 |

#### 3. 依赖配置

```toml
# gradle/libs.versions.toml
[versions]
mlkit = "19.0.0"

[libraries]
google-mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkit" }
```

```kotlin
// composeApp/build.gradle.kts
androidMain.dependencies {
    implementation(libs.google.mlkit.text.recognition)
}
```

#### 4. 语言包说明

**内置支持**：
- 拉丁文本（英文、西欧语言）- 无需额外下载
- 阿拉伯文、中文、梵文、日文、韩文等 - 需要下载语言包（约 10-20MB/语言）

**优势**：
- 无需预训练模型文件
- Google 官方维护，稳定可靠
- 自动下载所需语言包

---

### 任务 14：集成 Google ML Kit Translation 依赖 ✅

#### 1. 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `gradle/libs.versions.toml` | 添加 `mlkit = "19.0.0"` 版本和库声明 |
| `composeApp/build.gradle.kts` | 添加 `implementation(libs.google.mlkit.translation)` 依赖 |

#### 2. 创建的文件

| 文件 | 说明 |
|------|------|
| `composeApp/src/androidMain/res/values/mlkit_values.xml` | ML Kit 配置文件 |
| `docs/LANGUAGE_PACKS.md` | 语言包配置和使用文档 |

#### 3. 依赖配置

```toml
# gradle/libs.versions.toml
[versions]
mlkit = "19.0.0"

[libraries]
google-mlkit-translation = { group = "com.google.mlkit", name = "translation", version.ref = "mlkit" }
```

```kotlin
// composeApp/build.gradle.kts
androidMain.dependencies {
    implementation(libs.google.mlkit.translation)
}
```

#### 4. 预装语言包

| 语言 | 代码 | 大小 |
|------|------|------|
| 中文（简体） | `zh` | ~75MB |
| 英语（美式） | `en` | ~75MB |
| 日语 | `ja` | ~75MB |
| 韩语 | `ko` | ~75MB |

**总大小**：约 300MB（APK 会显著增大）

---

## 构建配置文件对比

### gradle/libs.versions.toml

**添加的版本**：
```toml
# Google ML Kit - 本地 OCR 和翻译引擎
mlkit = "19.0.0"
```

**添加的库**：
```toml
google-mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkit" }
google-mlkit-translation = { group = "com.google.mlkit", name = "translation", version.ref = "mlkit" }
```

### composeApp/build.gradle.kts

**在 `androidMain.dependencies` 中添加**：
```kotlin
// Google ML Kit - 本地 OCR 引擎
implementation(libs.google.mlkit.text.recognition)
// Google ML Kit - 本地翻译引擎
implementation(libs.google.mlkit.translation)
```

---

## 下一步

依赖集成完成后，需要实现以下模块：

1. **MLKitOCRManager** - 封装 ML Kit Text Recognition API（任务 34）
2. **MLKitTranslator** - 封装 ML Kit Translation API（任务 4）
3. **TranslationManager** - 整合 OCR + Translation 流程（任务 6）
4. **FloatingService 集成** - 在悬浮窗服务中调用本地 OCR 翻译（任务 16）

---

## 验证方法

### 1. 同步 Gradle 依赖

```bash
./gradlew :composeApp:dependencies --configuration androidDebugRuntimeClasspath
```

确认输出中包含：
- `com.google.mlkit:text-recognition:19.0.0`
- `com.google.mlkit:translation:19.0.0`

### 2. 构建项目

```bash
./gradlew clean build
```

### 3. 安装测试

```bash
./gradlew :composeApp:installDebug
```

---

## 常见问题

### Q1: 依赖解析失败

**症状**：`Could not find com.google.mlkit:text-recognition`

**解决方案**：确认 `settings.gradle.kts` 中已添加 Google Maven 仓库

### Q2: ML Kit 语言包在哪里

**位置**：ML Kit 自动管理语言包，无需手动下载

**说明**：
- OCR 拉丁文本（英文等）内置，无需下载
- OCR 非拉丁文本（中文/日文等）首次使用时自动下载（约 10-20MB）
- 翻译语言包首次使用时自动下载（约 75MB/语言）

### Q3: APK 太大怎么办

ML Kit 语言包每个约 75MB，4 种语言共 300MB。

**优化方案**：
1. 只打包必需的语言
2. 使用按需下载模式（需要网络）
3. 考虑拆分 APK（ABI + 语言）

---

## 参考文档

- [ML Kit 语言包说明](docs/LANGUAGE_PACKS.md)

---

## 依赖来源

### Google ML Kit Text Recognition

- **官方文档**: https://developers.google.com/ml-kit/vision/text-recognition
- **Maven**: com.google.mlkit:text-recognition:19.0.0
- **许可**: Google ML Kit License

### Google ML Kit Translation

- **官方文档**: https://developers.google.com/ml-kit/translation
- **Maven**: com.google.mlkit:translation:19.0.0
- **许可**: Google ML Kit License
