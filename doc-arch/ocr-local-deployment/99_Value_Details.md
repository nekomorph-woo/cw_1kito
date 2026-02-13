# 价值细节: 模板范围外的内容

**生成上下文:**
- **输入文件:** doc-arch/ocr-local-first.md + UI/UX 新需求
- **模板方案:** Option A (General Engineering)
- **生成日期:** 2026-02-13
- **总条目数:** 48
- **版本:** 1.1

---

## 生成概览

本文档捕获原始讨论中有价值但不适合标准 PRD、架构或 API 文档模板的内容。这些细节为未来迭代、实施参考和问题解决提供重要背景。

---

## UI/UX 新增价值细节

### 1. 方案切换器设计考量

**背景:** 用户需要在 VLM 云端和本地 OCR 两种方案间切换

**设计决策:**
- 使用 Switch 组件而非 Radio Button，更符合"切换"的语义
- 位置放在 Tab 上方，确保用户先看到方案选择再看到具体配置
- 默认关闭（使用 VLM 云端方案），保持向后兼容

**用户体验考量:**
- 切换方案后立即生效，无需手动保存
- 切换时不清空另一方案的配置，方便来回切换
- 切换后显示简短 Toast 提示当前方案

**实现代码参考:**
```kotlin
@Composable
fun SchemeSwitcher(
    useLocalOcr: Boolean,
    onSchemeChanged: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "翻译方案",
            style = MaterialTheme.typography.titleMedium
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VLM云端",
                style = MaterialTheme.typography.bodyMedium,
                color = if (!useLocalOcr) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Switch(
                checked = useLocalOcr,
                onCheckedChange = { newChecked ->
                    onSchemeChanged(newChecked)
                    // 显示提示
                    Toast.makeText(
                        context,
                        if (newChecked) "已切换到本地OCR方案"
                        else "已切换到VLM云端方案",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Text(
                text = "本地OCR",
                style = MaterialTheme.typography.bodyMedium,
                color = if (useLocalOcr) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

### 2. Tab 显示逻辑

**背景:** 仅在开启本地 OCR 后显示 Tab

**显示逻辑:**
```kotlin
@Composable
fun MainScreen(
    enableLocalOcr: Boolean,
    useLocalOcrScheme: Boolean
) {
    Column {
        // 方案切换器 - 始终显示（如果已开启本地 OCR）
        if (enableLocalOcr) {
            SchemeSwitcher(
                useLocalOcr = useLocalOcrScheme,
                onSchemeChanged = { ... }
            )
        }

        // Tab 导航 - 仅在开启本地 OCR 后显示
        if (enableLocalOcr) {
            TabRow(selectedTabIndex = ...) {
                Tab(text = { Text("VLM云端") }, ...)
                Tab(text = { Text("本地OCR") }, ...)
            }

            // Tab 内容
            when (selectedTab) {
                0 -> VLMCloudTab()
                1 -> LocalOcrTab()
            }
        } else {
            // 未开启本地 OCR - 直接显示 VLM 配置
            VLMCloudConfig()
        }

        // 权限配置区域 - 始终显示
        PermissionConfigSection()
    }
}
```

---

### 3. 翻译模式选择器交互

**背景:** 本地 OCR 方案下选择翻译方式

**交互流程:**
1. 默认选择"本地 ML Kit 翻译"
2. 选择"云端 LLM 翻译"时，展开配置区域
3. 配置区域包含：模型选择、提示词编辑、重置按钮

**动画效果:**
```kotlin
var selectedMode by remember { mutableStateOf(TranslationMode.LOCAL_MLKIT) }

Column {
    // 翻译模式选择
    TranslationModeSelector(
        currentMode = selectedMode,
        onModeChanged = { selectedMode = it }
    )

    // 云端 LLM 配置 - 带动画展开
    AnimatedVisibility(
        visible = selectedMode == TranslationMode.CLOUD_LLM,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        CloudLlmConfigSection(
            config = cloudLlmConfig,
            onConfigChanged = { ... },
            onReset = { ... }
        )
    }
}
```

---

### 4. 提示词编辑器功能

**背景:** 用户需要自定义云端 LLM 翻译的提示词

**功能点:**
1. **多行编辑**: 支持多行文本输入
2. **占位符提示**: 显示 `{text}` 占位符的使用说明
3. **字符计数**: 显示当前字符数
4. **重置功能**: 一键恢复默认提示词
5. **预设模板**: 提供游戏、漫画、通用等预设模板

**默认提示词:**
```kotlin
object PromptTemplates {
    // 默认提示词（占位符版本）
    val DEFAULT_TEMPLATE = """
You are a professional expert in localizing Japanese/Korean comics and Japanese games. Please translate the following \${sourceLang.displayName} text into \${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
    """.trimIndent()

    // 游戏专用提示词
    val GAME = """
You are a professional game localization expert specializing in Japanese games. Please translate the following \${sourceLang.displayName} game text into \${targetLang.displayName}.
Maintain consistency with game terminology and character names.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
    """.trimIndent()

    // 漫画专用提示词
    val MANGA = """
You are a professional manga localization expert. Please translate the following \${sourceLang.displayName} manga text into \${targetLang.displayName}.
Maintain character voice consistency and natural dialogue flow.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
    """.trimIndent()

    // 通用提示词
    val GENERAL = """
You are a professional translator. Please translate the following \${sourceLang.displayName} text into \${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
    """.trimIndent()

    // 根据语言生成实际提示词
    fun buildPrompt(
        template: String,
        sourceLang: Language,
        targetLang: Language,
        text: String
    ): String {
        return template
            .replace("\${sourceLang.displayName}", sourceLang.displayName)
            .replace("\${targetLang.displayName}", targetLang.displayName)
            .replace("{text}", text)
    }
}
```

---

### 5. 配置持久化策略

**背景:** 多项新配置需要持久化存储

**存储方案:**
使用 DataStore 配合 Proto DataStore 或 Preferences DataStore

**配置键列表:**
| 键名 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| enable_local_ocr | Boolean | false | 实验室本地 OCR 开关 |
| use_local_ocr_scheme | Boolean | false | 方案切换器状态 |
| translation_mode | String | "LOCAL_MLKIT" | 翻译模式 |
| cloud_llm_model_id | String | "Qwen/Qwen2.5-7B-Instruct" | 云端 LLM 模型 ID |
| cloud_llm_prompt | String | DEFAULT_PROMPT | 云端 LLM 提示词 |

**实现代码:**
```kotlin
class OcrConfigManager(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "ocr_translation_config"
    )

    object PreferencesKeys {
        val ENABLE_LOCAL_OCR = booleanPreferencesKey("enable_local_ocr")
        val USE_LOCAL_OCR_SCHEME = booleanPreferencesKey("use_local_ocr_scheme")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val CLOUD_LLM_MODEL_ID = stringPreferencesKey("cloud_llm_model_id")
        val CLOUD_LLM_PROMPT = stringPreferencesKey("cloud_llm_prompt")
    }

    val configFlow: Flow<OcrTranslationConfig> = context.dataStore.data
        .map { preferences ->
            OcrTranslationConfig(
                enableLocalOcr = preferences[PreferencesKeys.ENABLE_LOCAL_OCR] ?: false,
                useLocalOcrScheme = preferences[PreferencesKeys.USE_LOCAL_OCR_SCHEME] ?: false,
                translationMode = TranslationMode.valueOf(
                    preferences[PreferencesKeys.TRANSLATION_MODE] ?: "LOCAL_MLKIT"
                ),
                cloudLlmConfig = CloudLlmConfig(
                    modelId = preferences[PreferencesKeys.CLOUD_LLM_MODEL_ID]
                        ?: "Qwen/Qwen2.5-7B-Instruct",
                    customPrompt = preferences[PreferencesKeys.CLOUD_LLM_PROMPT]
                        ?: PromptTemplates.DEFAULT
                )
            )
        }

    suspend fun updateConfig(config: OcrTranslationConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_LOCAL_OCR] = config.enableLocalOcr
            preferences[PreferencesKeys.USE_LOCAL_OCR_SCHEME] = config.useLocalOcrScheme
            preferences[PreferencesKeys.TRANSLATION_MODE] = config.translationMode.name
            preferences[PreferencesKeys.CLOUD_LLM_MODEL_ID] = config.cloudLlmConfig.modelId
            preferences[PreferencesKeys.CLOUD_LLM_PROMPT] = config.cloudLlmConfig.customPrompt
        }
    }
}
```

---

## 批量翻译实现细节

### 1. 批量翻译策略

**核心思路:** 使用协程并发，每批最多 3 个并发请求

| 方面 | 本地 ML Kit | 云端 LLM |
|------|-------------|----------|
| **批量方式** | 3 个并发请求 | 3 个并发请求 |
| **API 调用** | 每批 3 个并发调用 | 每批 3 个并发调用 |
| **性能提升** | ~3x（并发） | ~3x（并发） |
| **错误处理** | 单个失败不影响其他 | 单个失败不影响其他 |

### 2. 并发批量翻译实现

```kotlin
/**
 * 并发批量翻译（本地和云端相同）
 * 每批最多 3 个文本，使用协程并发处理
 */
private suspend fun translateBatchConcurrent(
    batch: List<String>,
    sourceLang: Language,
    targetLang: Language,
    mode: TranslationMode
): List<String> = coroutineScope {
    // 创建 3 个并发任务
    batch.map { text ->
        async(Dispatchers.IO) {
            when (mode) {
                TranslationMode.LOCAL_MLKIT -> {
                    mlKitTranslator.translate(text, sourceLang, targetLang)
                }
                TranslationMode.CLOUD_LLM -> {
                    cloudLlmClient.translate(
                        text,
                        sourceLang,
                        targetLang,
                        config.cloudLlmConfig?.customPrompt
                    )
                }
            }
        }
    }.awaitAll()  // 等待所有完成
}
```

**时序图:**
```
Batch 1 (并发执行):
├── Request 1 (Text1) ───────────┐
├── Request 2 (Text2) ───────────┼──> awaitAll() → 进入下一批
└── Request 3 (Text3) ───────────┘

Batch 2 (并发执行):
├── Request 4 (Text4) ───────────┐
├── Request 5 (Text5) ───────────┼──> awaitAll() → ...
└── Request 6 (Text6) ───────────┘
```

### 3. 批量翻译与覆盖层联动

```kotlin
/**
 * 在 FloatingService 中使用批量翻译
 */
private suspend fun performTranslation(
    mergedTexts: List<MergedText>,
    config: OcrTranslationConfig
) {
    val texts = mergedTexts.map { it.text }
    val totalTexts = texts.size

    batchTranslationManager.translateBatch(
        texts = texts,
        sourceLang = config.sourceLanguage,
        targetLang = config.targetLanguage,
        mode = config.translationMode,
        onBatchComplete = { batchIndex, totalBatches ->
            // 更新进度
            updateProgress(batchIndex, totalBatches)

            // 批次完成后，立即显示该批次的翻译结果
            val startIndex = (batchIndex - 1) * BATCH_SIZE
            val endIndex = minOf(batchIndex * BATCH_SIZE, totalTexts)

            for (i in startIndex until endIndex) {
                val result = TranslationResult(
                    originalText = mergedTexts[i].text,
                    translatedText = results[i],
                    boundingBox = mergedTexts[i].boundingBox
                )
                overlayRenderer.addResult(result)
            }
        }
    )
}
```

### 4. 批量大小选择依据

| 批量大小 | 优点 | 缺点 |
|----------|------|------|
| 1 | 简单，单个失败不影响其他 | 性能差 |
| **3** | 平衡性能和可靠性 | 折中方案 |
| 5 | 更高吞吐量 | 资源占用大 |
| 10 | 最大吞吐量 | 响应慢，失败影响大 |

**选择 3 的原因:**
1. 性能提升明显（~3x）
2. 单个失败不影响其他请求
3. 资源占用可控
4. 用户体验好（每批显示一次进度）

---

## API Key 与校验规则实现

### 1. 默认方案与校验逻辑

```
┌─────────────────────────────────────────────────────────────┐
│                    用户打开悬浮窗                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  校验权限（仅校验权限）                                       │
│  - 悬浮窗权限 ✅                                             │
│  - 通知权限 ✅                                               │
│  - 截图权限 ✅                                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  显示悬浮窗（不校验 API Key 或语言包）                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  用户点击悬浮球 → 触发翻译                                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  根据方案校验依赖                                             │
│  - 本地 OCR + 本地翻译 → 检查语言包 → 未下载则引导下载         │
│  - VLM 云端方案 → 检查 API Key → 未配置则引导配置             │
│  - 本地 OCR + 云端翻译 → 检查 API Key → 未配置则引导配置      │
└─────────────────────────────────────────────────────────────┘
```

### 2. 翻译执行前校验

```kotlin
class TranslationDependencyChecker(
    private val configManager: ConfigManager,
    private val languagePackManager: LanguagePackManager
) {
    sealed class CheckResult {
        object Ready : CheckResult()
        data class NeedLanguagePack(val missingPairs: List<Pair<Language, Language>>) : CheckResult()
        data class NeedApiKey(val reason: String) : CheckResult()
    }

    suspend fun checkBeforeTranslate(): CheckResult {
        val config = configManager.getOcrTranslationConfig()

        return when {
            // 本地 OCR + 本地翻译
            config.useLocalOcrScheme && config.translationMode == TranslationMode.LOCAL_MLKIT -> {
                val missingPairs = languagePackManager.getMissingLanguagePacks(
                    config.sourceLanguage,
                    config.targetLanguage
                )
                if (missingPairs.isEmpty()) {
                    CheckResult.Ready
                } else {
                    CheckResult.NeedLanguagePack(missingPairs)
                }
            }

            // VLM 云端 或 本地 OCR + 云端翻译
            !config.useLocalOcrScheme || config.translationMode == TranslationMode.CLOUD_LLM -> {
                if (configManager.hasApiKey()) {
                    CheckResult.Ready
                } else {
                    CheckResult.NeedApiKey(
                        if (!config.useLocalOcrScheme) "VLM 云端翻译需要 API Key"
                        else "云端 LLM 翻译需要 API Key"
                    )
                }
            }

            else -> CheckResult.Ready
        }
    }
}
```

### 3. 引导 UI 实现

```kotlin
// 在 FloatingService 中
private suspend fun handleTranslationDependency(result: CheckResult) {
    when (result) {
        is CheckResult.Ready -> {
            // 继续翻译流程
            performTranslation()
        }
        is CheckResult.NeedLanguagePack -> {
            // 显示语言包下载引导
            showLanguagePackDownloadGuide(result.missingPairs)
        }
        is CheckResult.NeedApiKey -> {
            // 显示 API Key 配置引导
            showApiKeyConfigGuide(result.reason)
        }
    }
}

private fun showLanguagePackDownloadGuide(missingPairs: List<Pair<Language, Language>>) {
    val message = """
        首次使用本地翻译需要下载语言包。

        需要下载：${missingPairs.map { "${it.first.displayName}→${it.second.displayName}" }.joinToString(", ")}

        是否立即下载？
    """.trimIndent()

    // 显示对话框或跳转到下载页面
    showDialog(
        title = "下载翻译语言包",
        message = message,
        positiveButton = "下载" to { startLanguagePackDownload(missingPairs) },
        negativeButton = "取消" to { }
    )
}

private fun showApiKeyConfigGuide(reason: String) {
    val message = """
        $reason

        请在实验室中配置硅基流动 API Key。

        是否前往配置？
    """.trimIndent()

    showDialog(
        title = "需要 API Key",
        message = message,
        positiveButton = "去配置" to { navigateToLabSettings() },
        negativeButton = "取消" to { }
    )
}
```

---

## 语言包预下载实现细节

### 1. ML Kit 语言包下载机制

**背景:** Google ML Kit Translation 的语言包**无法预装到 APK**，这是 Google 的技术限制。

**下载机制:**
- 语言包由 Google Play Services 管理
- 通过 `translator.downloadModelIfNeeded()` 触发下载
- 下载后缓存到 `/data/data/<package>/files/mlkit/` 目录
- 每个 语言对约 20-40MB

### 2. 应用启动时预下载流程

```kotlin
// 在 MainActivity.onCreate() 或 Application 中
class MainActivity : ComponentActivity() {

    private val languagePackManager: LanguagePackManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并预下载语言包
        lifecycleScope.launch {
            if (languagePackManager.needsDownload()) {
                showLanguagePackDownloadDialog()
            }
        }
    }

    private fun showLanguagePackDownloadDialog() {
        val isWifi = isWifiConnected()

        val message = if (isWifi) {
            """
            首次使用本地翻译需要下载语言包（约 240MB）。
            建议在 Wi-Fi 环境下下载。

            是否立即下载？
            """.trimIndent()
        } else {
            """
            当前使用移动网络，下载语言包将消耗约 240MB 流量。
            建议连接 Wi-Fi 后下载。

            是否继续下载？
            """.trimIndent()
        }

        AlertDialog.Builder(this)
            .setTitle("下载翻译语言包")
            .setMessage(message)
            .setPositiveButton("下载") { _, _ ->
                startLanguagePackDownload()
            }
            .setNegativeButton("稍后提醒") { _, _ ->
                // 设置 3 天后再次提醒
                scheduleReminder(3)
            }
            .setNeutralButton("不再提醒") { _, _ ->
                // 永久关闭提醒
                setReminderDisabled(true)
            }
            .show()
    }

    private fun startLanguagePackDownload() {
        // 显示下载进度 UI
        val downloadDialog = DownloadProgressDialog(this)
        downloadDialog.show()

        lifecycleScope.launch {
            val result = languagePackManager.downloadAllCommonPairs(
                requireWifi = false,  // 用户已确认
                onProgress = { current, total ->
                    downloadDialog.updateProgress(current, total)
                }
            )

            downloadDialog.dismiss()

            if (result.successCount > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "已下载 ${result.successCount} 个语言包",
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (result.failedCount > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "${result.failedCount} 个语言包下载失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
```

### 3. 下载进度 UI

```kotlin
@Composable
fun LanguagePackDownloadProgress(
    current: Int,
    total: Int,
    currentPair: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "正在下载翻译语言包",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { current.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "当前：$currentPair",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### 4. 语言包管理页面

```kotlin
@Composable
fun LanguagePackManagementScreen(
    states: List<LanguagePackState>,
    onDownload: (Language, Language) -> Unit,
    onDelete: (Language, Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部统计
        val downloadedCount = states.count { it.status == DownloadStatus.DOWNLOADED }
        val totalSize = states.sumOf { it.sizeBytes }.formatFileSize()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "语言包状态",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("已下载：$downloadedCount / ${states.size}")
                Text("存储占用：$totalSize")
            }
        }

        // 语言对列表
        LazyColumn {
            items(states) { state ->
                LanguagePackItem(
                    state = state,
                    onDownload = { onDownload(state.sourceLang, state.targetLang) },
                    onDelete = { onDelete(state.sourceLang, state.targetLang) }
                )
            }
        }
    }
}

@Composable
fun LanguagePackItem(
    state: LanguagePackState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text("${state.sourceLang.displayName} → ${state.targetLang.displayName}")
        },
        supportingContent = {
            when (state.status) {
                DownloadStatus.DOWNLOADED -> {
                    Text("已下载 · ${state.sizeBytes.formatFileSize()}")
                }
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DownloadStatus.NOT_DOWNLOADED -> {
                    Text("未下载")
                }
                DownloadStatus.FAILED -> {
                    Text("下载失败", color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        },
        trailingContent = {
            when (state.status) {
                DownloadStatus.DOWNLOADED -> {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除")
                    }
                }
                DownloadStatus.NOT_DOWNLOADED,
                DownloadStatus.FAILED -> {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, "下载")
                    }
                }
                else -> {}
            }
        }
    )
}
```

### 5. Wi-Fi 检测工具

```kotlin
object NetworkUtils {
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
```

---

## 未来考虑

### 1. 图片分辨率动态调整

**Idea:** 根据设备性能和用户选择的性能模式,动态调整图片预处理分辨率

**描述:**
- **极速模式 (FAST):** 短边 672px,优先速度
- **平衡模式 (BALANCED):** 短边 896px,推荐默认
- **高精模式 (QUALITY):** 短边 1080px,优先精度
- **自动检测:** 根据设备内存和 CPU 基准自动选择

**潜在价值:**
- 在低端设备上自动降级,避免 OOM 崩溃
- 高端设备充分利用性能,提供最佳 OCR 质量
- 用户可手动覆盖,适应不同场景 (如快速扫图 vs 精细翻译)

**实现可行性:** 高 (已有 PerformanceMode 枚举)

---

### 2. NPU 加速支持

**Idea:** 利用 Snapdragon 8 Elite Gen 5 的 Hexagon NPU 加速 PaddleOCR 推理

**描述:**
- 使用 Paddle-Lite 的 QNN (Qualcomm Neural Network) 后端
- 替代 CPU 推理,降低功耗和延迟
- 预期性能提升: 20-50% (理论值)

**潜在价值:**
- 显著降低推理延迟 (<100ms in NPU vs 250ms in CPU)
- 减少功耗,延长电池续航
- 降低设备发热,避免热限频

**实现考虑:**
- 需验证 Paddle-Lite 对 Snapdragon 8 Elite Gen 5 的支持程度
- 可能需要 QNN SDK 集成 (增加复杂度)
- 提供 CPU 降级方案 (NPU 初始化失败时)

**何时重新考虑:** OnePlus 15 实测完成后,根据实际性能提升决定优先级

---

### 3. 多语言翻译模型扩展

**Idea:** 除 Google ML Kit 外,支持其他本地翻译模型 (如 NLLB-200, M2M100)

**描述:**
- 通过 ONNX Runtime 集成 NLLB-200-distilled-600M (200+ 语言)
- 支持自定义翻译 Prompt (ML Kit 不支持)
- 模型大小控制 (~1.2GB vs ML Kit 300MB)

**潜在价值:**
- 覆盖更多小语种 (ML Kit 59 种 vs NLLB 200+ 种)
- 游戏专有名词翻译准确度可能更高
- 用户可根据场景选择模型 (日常用 MLKit,术语用 NLLB)

**实现挑战:**
- ONNX Runtime Android 集成复杂度
- 模型文件分发 (APK 体积限制)
- 首次下载 1.2GB 模型的用户接受度

**何时实现:** Google ML Kit 翻译质量用户反馈不理想,收集至少 50 个样本后评估

---

### 4. 批量翻译模式

**Idea:** 支持从相册选择多张图片,批量执行 OCR + 翻译

**描述:**
- 用户选择相册中的多张截图
- 后台队列处理,逐张完成
- 进度显示 (3/10 完成)

**潜在价值:**
- 提高相册翻译效率 (如漫画章节)
- 利用并发能力,充分利用多核 CPU
- 支持导出翻译结果为纯文本或 JSON

**实现复杂度:** 中等
- 需要队列管理机制
- 进度持久化 (应用关闭后恢复)
- 内存管理 (避免同时加载多张图片)

**何时实现:** 核心功能稳定后,作为 v1.1 增强特性

---

### 5. OCR 历史记录与缓存

**Idea:** 持久化最近 OCR 结果和翻译,支持回看和搜索

**描述:**
- 保存最近 100-500 次翻译结果
- 支持文本搜索和过滤
- 导出为文件或分享

**潜在价值:**
- 用户可回看之前翻译的内容
- 减少重复翻译相同内容 (缓存命中)
- 支持学习和复习场景

**隐私考虑:**
- 用户可选择禁用历史记录
- 提供自动清理选项 (如 7 天后删除)
- 数据加密存储 (避免泄露)

**何时实现:** 用户反馈需求强烈,或隐私政策明确后

---

### 6. 自定义翻译 Prompt

**Idea:** 允许高级用户自定义翻译 Prompt (云端模式)

**描述:**
- 设置页提供 Prompt 编辑器
- 预设模板 (游戏术语、漫画翻译、商务专业)
- 用户可插入术语词典或格式要求

**潜在价值:**
- 游戏专有名词 (技能名、装备) 翻译更准确
- 漫画角色名和术语保持一致性
- 专业文档翻译符合行业规范

**实现复杂度:** 低
- 仅影响云端 API 调用
- 本地模式无此功能 (ML Kit 不支持 Prompt)

**何时实现:** 云端翻译质量不足,用户反馈收集后

---

## 替代方案

### 1. ONNX Runtime 部署 (替代 Paddle-Lite)

**方案:** 使用 ONNX Runtime 替代 Paddle-Lite,部署 PaddleOCR 或其他模型

**为何拒绝:**
- **集成复杂度:** ONNX Runtime Android SDK 文档较少,社区支持弱于 Paddle-Lite
- **性能未知:** ONNX 在 Snapdragon 上的性能未实测,可能不如 Paddle-Lite 优化
- **维护成本:** 需要自己维护模型转换和部署脚本

**何时重新考虑:**
- Paddle-Lite JNI 集成遇到难以解决的问题
- 或需要部署其他框架的模型 (如 Tesseract, EasyOCR)
- 或社区提供成熟的 ONNX Android 封装库

---

### 2. 纯云端 VL 模型方案 (端到端)

**方案:** 继续使用 Qwen2.5-VL-32B,不做本地化

**为何拒绝:**
- **延迟问题:** VL 模型单次请求 2-5 秒,用户体验差
- **网络依赖:** 完全依赖网络,离线不可用
- **成本压力:** 高频用户 API 成本 $10-50/月,开发者盈利困难
- **隐私担忧:** 截图上传到服务器,隐私敏感用户不接受

**何时重新考虑:**
- 本地方案技术失败 (如 PaddleOCR 集成无法完成)
- 或用户明确偏好云端模式 (如设备存储不足)
- 或需要最高精度 (32B VL 仍优于 7B 文本模型)

---

### 3. 云端 OCR API 方案 (如 Google Cloud Vision)

**方案:** 使用 Google Cloud Vision API 进行 OCR,本地翻译

**为何拒绝:**
- **成本更高:** Google Cloud Vision 按 API 调用计费,成本高于本地 PaddleOCR
- **仍需网络:** OCR 阶段仍依赖网络,未解决离线问题
- **隐私担忧:** 截图仍上传到 Google 服务器
- **延迟叠加:** 网络 + OCR API + 翻译 API,总延迟可能更长

**何时重新考虑:**
- PaddleOCR 本地部署失败 (模型文件损坏或 JNI 问题)
- 或用户愿意接受网络依赖以换取更高 OCR 精度
- 或本地存储不足以容纳 OCR 模型

---

### 4. 混合模式优先云端翻译 (而非本地优先)

**方案:** 默认使用云端 API 翻译,本地仅作为离线备选

**为何拒绝:**
- **违背 "本地优先" 原则:** 核心价值主张是离线可用
- **成本压力:** 高频用户仍会产生显著 API 费用
- **用户体验:** 云端延迟 (1-2 秒) vs 本地 (<100ms),感知差异明显

**何时重新考虑:**
- Google ML Kit 翻译质量严重不足 (通过用户反馈验证)
- 或商业模式调整为免费增值服务 (用户可购买云端配额)
- 或本地模型翻译质量显著落后于云端模型 (如专业术语)

---

### 5. TensorFlow Lite 部署 (替代 PaddleOCR)

**方案:** 使用 TensorFlow Lite 的 OCR 模型 (如 Tesseract.js 移植版)

**为何拒绝:**
- **精度较低:** Tesseract 等开源 OCR 精度显著低于 PaddleOCR PP-OCRv5
- **维护成本高:** 需要自己训练和优化模型,PaddleOCR 有官方支持
- **社区支持弱:** TensorFlow Lite OCR 示例较少,问题解决困难

**何时重新考虑:**
- PaddleOCR 在特定语言 (如阿拉伯文、俄文) 表现不佳
- 或需要极高定制化 (PaddleOCR 无法满足)
- 或 Google 官方提供 TFLite OCR 模型且更优

---

### 6. 分页处理超大图片 (替代整体缩放)

**方案:** 将超大分辨率截图 (如 4K) 分页处理,逐块 OCR + 翻译

**为何拒绝:**
- **复杂度高:** 分页逻辑、拼接结果、边界处理复杂
- **用户体验差:** 需要用户手动分页或自动分页导致上下文割裂
- **效果有限:** 屏幕截图通常 <2K,分页收益不明显

**何时重新考虑:**
- 用户反馈 4K 截图 OCR 质量差 (因缩放损失细节)
- 或设备性能足够,支持整体处理 (如 16GB RAM 设备)
- 或特定场景需要超高精度 (如技术文档翻译)

---

## 实施细节

### 1. OnePlus 15 性能基准测试方法

**细节:** OnePlus 15 (Snapdragon 8 Elite Gen 5) 的性能验证方案

**上下文:** PRD 和架构文档中提到的性能目标是估算值,需实测验证

**测试方法:**
1. **准备测试数据集:**
   - 100 张不同分辨率截图 (720p, 1080p, 2K)
   - 包含日文、中文、韩文、英文样本
   - 包含游戏 UI、漫画对话框、菜单等场景

2. **OCR 推理测试:**
   - 使用 `System.currentTimeMillis()` 测量 PaddleOCR 推理耗时
   - 测试 10 次,计算 P50/P95/P99
   - 记录 CPU 使用率和内存占用

3. **翻译推理测试:**
   - 测试 Google ML Kit 单段翻译耗时
   - 测试不同文本长度 (10/50/200/500 字符)
   - 记录 ML Kit 内部缓存命中率

4. **端到端延迟测试:**
   - 测量从截图触发到覆盖层显示的总时间
   - 目标: P50 <1s, P95 <1.5s
   - 对比本地模式 vs 云端模式

5. **NPU 性能对比:**
   - 如果实现 NPU 支持,对比 CPU vs NPU 性能
   - 测试功耗和设备发热
   - 验证 Paddle-Lite QNN 后端可用性

**参考:** [来源: ocr-local-first.md, 第 414-430 行]

---

### 2. PaddleOCR-VL LOC Token 解析算法

**细节:** PaddleOCR-VL 输出的 `<|LOC_xxx|>` token 转换为坐标

**上下文:** PRD 中提到需要解析 LOC token,API 文档未提供具体算法

**用户反馈澄清 (2026-02-12):**
- ✅ **已确认:** 建议使用现成的 Kotlin 库进行解析
- ✅ **调研方向:** HuggingFace Transformers 社区、PaddleOCR 官方 GitHub Issues、Stack Overflow
- ❌ **不需要自实现:** 正则表达式解析逻辑

**简化实施策略:**
1. **使用现成库** (优先推荐):
   ```kotlin
   // 方案 A: 使用 HuggingFace Transformers 的 token 处理工具
   // 如果 PaddleOCR-VL 基于 Transformers 实现,可能已有配套解析库

   implementation("com.huggingface:tokenizers:0.1.0")

   val tokenizer = Tokenizer.fromPretrained("PaddleOCR-VL-tokenizer")
   val tokens = tokenizer.decode(rawOutput)
   val coords = tokenizer.extractCoordinates(tokens)  // 假设库提供
   ```

2. **联系官方支持** (备选方案):
   ```kotlin
   // 方案 B: 在 PaddleOCR GitHub Issues 提问,请求官方提供解析示例
   // 可能已有社区贡献的 Kotlin/Java 解析工具

   // 示例问题方向:
   // "How to parse <|LOC_xxx|> tokens in Kotlin? Any existing library?"
   // "Is there official example code for coordinate parsing?"
   ```

3. **社区方案** (最后备选):
   ```kotlin
   // 方案 C: 使用社区封装库 (如 equationl/paddleocr4android)
   // 这些库可能已封装 LOC token 解析逻辑

   implementation("com.github.equationl:paddleocr4android:1.0.0")

   // 库可能提供直接返回解析后坐标的接口
   val results = paddleOCR.recognizeWithParsedCoords(bitmap)
   // results: List<ParsedResult> 包含 text + boundingBox (已解析)
   ```

**避免重复造轮:** 强烈建议使用上述方案之一,而非自己实现正则解析

**参考:** [来源: ocr-local-first.md, 第 179-234 行] | [用户反馈: 2026-02-12]

---

### 3. 语言包信息展示 (已移除下载功能)

**细节:** 用户查看已打包的语言包信息

**上下文:** 所有语言包 (中英日韩) 已打包进 APK,无需下载

**用户反馈澄清 (2026-02-12):**
- ✅ **已确认:** 所有语言包 (中英日韩) 打包进 APK
- ✅ **需求变更:** 不需要下载和断点续传功能
- ❌ **移除:** 语言包下载进度 UI、后台下载通知、断点续传机制

**UI 组件 (简化版):**
1. **语言包信息页:**
   ```kotlin
   @Composable
   fun LanguagePackInfoScreen(
       onBack: () -> Unit
   ) {
       Column {
           Text("已安装的翻译语言包")
           Spacer(modifier = Modifier.height(16.dp))

           LazyColumn {
               item(Language.CHINESE) {
                   LanguagePackInfoCard(
                       language = Language.CHINESE,
                       modelVersion = "ML Kit v17.0.3",
                       packageSize = "75MB",
                       languagePairs = "中文 ↔ 英文、中文 ↔ 日文、中文 ↔ 韩文"
                   )
               }

               item(Language.ENGLISH) {
                   LanguagePackInfoCard(
                       language = Language.ENGLISH,
                       modelVersion = "ML Kit v17.0.3",
                       packageSize = "60MB",
                       languagePairs = "英文 ↔ 中文、英文 ↔ 日文、英文 ↔ 韩文"
                   )
               }
           }
       }
   }
   ```

2. **移除的组件:**
   - ❌ 启动引导页 (不再需要首次下载)
   - ❌ LinearProgressIndicator (无需下载进度)
   - ❌ 后台下载通知 (无需后台任务)
   - ❌ 断点续传机制 (所有文件已在 APK 中)

**简化优势:**
- 用户体验提升: 首次启动即可使用,无需等待下载
- 代码复杂度降低: 移除下载管理逻辑
- 存储管理简化: 无需清理临时下载文件
- APK 体积可控: 约 120-130MB (在 Google Play 150MB 限制内)

**参考:** [来源: 用户反馈, 2026-02-12]


### 4. 文本合并算法参数调优建议值

**细节:** 日文、中文、韩文的默认合并阈值

**上下文:** API 文档提到阈值可调,需提供合理的默认值

**推荐值 (基于源文档讨论):**

| 语言类型 | yTolerance | xToleranceFactor | 理由 |
|----------|-------------|------------------|------|
| **日文 (游戏 UI)** | 0.4f | 1.2f | 字符间距小,严格合并 |
| **日文 (漫画)** | 0.5f | 1.5f | 气泡间距大,宽松合并 |
| **中文 (UI)** | 0.3f | 1.0f | 字符紧凑,较严格合并 |
| **中文 (文档)** | 0.5f | 1.8f | 行距较大,宽松合并 |
| **韩文** | 0.4f | 1.3f | 介于日文中文之间 |

**自适应策略:**
```kotlin
fun calculateTolerance(boxes: List<TextBox>, language: Language): MergingConfig {
    val avgHeight = boxes.map { it.height }.average()
    val avgCharWidth = boxes.map { it.width / it.text.length }.average()

    return when (language) {
        Language.JAPANESE -> MergingConfig(
            yTolerance = 0.4f * avgHeight,
            xToleranceFactor = 1.2f
        )
        Language.CHINESE -> MergingConfig(
            yTolerance = 0.3f * avgHeight,
            xToleranceFactor = 1.0f
        )
        // 其他语言...
    }
}
```

**用户 UI:**
- 提供 "游戏"、"漫画"、"文档"、"自动" 预设
- 高级设置页显示滑块,实时预览合并效果
- 保存用户自定义配置为命名方案

**参考:** [来源: ocr-local-first.md, 第 693-795 行]

---

### 5. 横竖排检测后的合并逻辑调整

**细节:** 检测到竖排文本后,合并算法如何调整

**上下文:** 架构文档提到方向检测,但未详细说明合并差异

**合并逻辑对比:**

**横排合并 (标准):**
```kotlin
fun mergeHorizontal(line: List<TextBox>): MergedText {
    // 1. 按 X 轴排序
    val sorted = line.sortedBy { it.left }
    // 2. X 轴间隙判断
    val mergedText = StringBuilder()
    val mergedBox = mergeXAxis(sorted)

    return MergedText(
        text = mergedText.toString(),
        boundingBox = mergedBox,
        direction = TextDirection.HORIZONTAL
    )
}
```

**竖排合并 (调整):**
```kotlin
fun mergeVertical(line: List<TextBox>): MergedText {
    // 1. 按 Y 轴排序 (竖排是从上到下)
    val sorted = line.sortedBy { it.top }
    // 2. Y 轴间隙判断 (替代 X 轴)
    val mergedText = StringBuilder()
    val mergedBox = mergeYAxis(sorted)

    return MergedText(
        text = mergedText.toString(),
        boundingBox = mergedBox,
        direction = TextDirection.VERTICAL
    )
}

private fun mergeYAxis(boxes: List<TextBox>): RectF {
    // 竖排时:合并 Y 方向相邻的框
    var minY = boxes.first().top
    var maxY = boxes.first().bottom
    var minX = boxes.minOf { it.left }
    var maxX = boxes.maxOf { it.right }

    for (box in boxes) {
        val gap = box.top - maxY  // 计算上下间隙
        if (gap <= yTolerance) {
            maxY = box.bottom
            minY = minOf(minY, box.top)
        }
        minX = minOf(minX, box.left)
        maxX = maxOf(maxX, box.right)
    }

    return RectF(minX, minY, maxX, maxY)
}
```

**方向检测优先级:**
1. **使用 PaddleOCR 方向分类器** (use_angle_cls=True)
   - 最可靠,提供 0°/90°/180°/270° 角度
   - 角度接近 0° 或 180° → 横排
   - 角度接近 90° 或 270° → 竖排

2. **降级到宽高比推断**
   ```kotlin
   fun inferDirection(box: TextBox): TextDirection {
       val ratio = box.width / box.height
       return when {
           ratio < 0.5f -> TextDirection.VERTICAL   // 高 > 宽 2 倍
           ratio > 2.0f -> TextDirection.HORIZONTAL  // 宽 > 高 2 倍
           else -> TextDirection.MIXED                   // 其他情况
       }
   }
   ```

**参考:** [来源: ocr-local-first.md, 第 759-769 行]

---

### 6. 流式翻译与 Google ML Kit 批量 API 冲突处理

**细节:** Google ML Kit 不支持流式,如何保持流式 UX

**上下文:** PRD 要求流式显示翻译结果,但 ML Kit 仅支持批量翻译

**冲突处理方案:**

**方案 A: 分批翻译 (推荐)**
```kotlin
suspend fun translateStream(texts: List<String>): Flow<String> = flow {
    val batchSize = 3  // 每批翻译 3 段
    texts.chunked(batchSize).forEach { chunk ->
        val translated = chunk.map { text ->
            translator.translate(text)  // 并发翻译
        }.awaitAll()
        translated.forEach { emit(it) }
    }
}
```

- 优点: 减少 API 调用次数,提升吞吐量
- 缺点: 仍需等待整批完成,不如真流式平滑

**方案 B: 虚假流式 (备选)**
```kotlin
suspend fun translateFakeStream(text: String): Flow<String> = flow {
    // 1. 按句子分割 (简单正则)
    val sentences = text.split(Regex("""[。！？.？]"""))
    sentences.forEach { sentence ->
        delay(50)  // 模拟流式延迟
        emit(translator.translate(sentence))
    }
}
```

- 优点: 保持流式 UX 体验
- 缺点: 翻译质量可能下降 (上下文割裂)

**方案 C: 混合模式 (云端流式)**
```kotlin
suspend fun translateHybrid(text: String): Flow<String> = flow {
    if (apiKeyManager.hasApiKey()) {
        // 云端 API 支持流式
        remoteClient.translateStream(text).collect { emit(it) }
    } else {
        // 降级到本地批量
        val translated = localTranslator.translate(text)
        emit(translated)
    }
}
```

- 优点: 有 API Key 时真流式,无 Key 时优雅降级
- 缺点: 云端延迟高于本地,流式优势不明显

**推荐:** 方案 C (混合模式),已架构文档采用

**参考:** [来源: ocr-local-first.md, 第 313-330 行]

---

## 用户洞察

### 1. 游戏玩家对翻译速度的敏感性

**洞察:** "等待 2-5 秒会打断游戏心流,错过剧情选择"

**来源:** 讨论中用户提到的痛点

**含义:**
- 游戏剧情是连续的,翻译延迟导致错过后面的对话
- 用户宁愿牺牲一点精度换取速度
- 建议性能模式默认设为 "极速" (FAST)

**影响:**
- PRD 中的性能模式优先级调整 (FAST > BALANCED > QUALITY)
- UI 中添加 "游戏模式" 预设,自动应用激进参数
- 考虑游戏场景专用优化 (跳过方向分类,假设全横排)

---

### 2. 漫画读者对翻译质量的高要求

**洞察:** "角色名必须保持一致,不能一会儿叫 'Alice' 一会儿叫 'Arisu'"

**来源:** 源文档中未明确提及,但日漫翻译常见问题

**含义:**
- 翻译质量 > 速度 (可接受更长延迟)
- 术语一致性比流畅度更重要
- 需要角色名词典或自定义 Prompt

**影响:**
- 提供云端 API 作为 "高质量模式" 选项
- 允许用户保存角色名映射表
- 未来考虑自定义翻译 Prompt (见未来考虑 #6)

---

### 3. 隐私敏感用户的离线优先偏好

**洞察:** "我不希望截图上传到任何服务器,即使承诺删除也不行"

**来源:** 本地优先架构的核心价值主张

**含义:**
- 完全离线是核心竞争力,非可选特性
- 用户愿意为离线能力牺牲 APK 体积和存储空间
- 隐私政策必须明确说明 "零数据上传"

**影响:**
- 营销材料强调 "完全本地、零网络、100% 隐私"
- 设置页默认禁用云端 API,需手动启用
- 隐私政策文档详细说明数据处理方式

---

### 4. 旅行者对流量消耗的关注

**洞察:** "海外漫游时数据流量很贵,希望完全离线使用"

**来源:** 本地翻译的价值点

**含义:**
- 离线模式是必需功能,非增值特性
- 语言包首次下载后,后续使用不消耗流量
- 用户可接受一次性下载 300MB (语言包),换取后续零流量

**影响:**
- 启动引导明确说明 "首次使用需 Wi-Fi 下载"
- 提供 "精简版" SKU (仅中英) 和 "完整版" SKU (所有语言)
- 设置页显示当前流量消耗统计 (如节省 XX MB 流量)

---

### 5. 高级用户对参数可调性的需求

**洞察:** "不同游戏/漫画文本间距不同,希望能手动调整合并敏感度"

**来源:** 文本合并算法的讨论

**含义:**
- 固定阈值无法适应所有场景
- 高级用户愿意花时间调优
- 预设方案 (游戏/漫画/文档) 能覆盖 80% 场景

**影响:**
- 高级设置页提供合并阈值滑块
- 实时预览合并效果 (显示合并前后的框数量)
- 保存用户自定义配置为命名方案,便于分享

---

## 边界情况

### 1. MediaProjection 权限在应用更新后过期

**情况:** 用户升级应用到新版本,MediaProjection token 失效但未提示重新授权

**描述:**
- Android 的 MediaProjection token 仅对授予它的应用有效
- 应用更新 (签名变化) 或系统重启导致 token 失效
- 用户点击悬浮球时无响应,或崩溃

**处理考虑:**
```kotlin
class ScreenCaptureManager {
    private var lastGrantedVersion: Int? = null

    fun hasValidToken(): Boolean {
        val currentVersion = getVersionCode()
        return lastGrantedVersion == currentVersion && mediaProjection != null
    }

    fun onPermissionGranted(resultCode: Int, data: Intent?) {
        setMediaProjection(resultCode, data)
        lastGrantedVersion = getVersionCode()
    }

    fun clearPermission() {
        mediaProjection?.stop()
        mediaProjection = null
        lastGrantedVersion = null
    }
}
```

**用户提示:**
- 检测到权限失效时,显示友好引导 ("需要重新授予截图权限")
- 跳转到系统设置页 (API 34+ 需要特殊处理)
- 记录最后授予时间,避免频繁打扰

---

### 2. 内存不足时模型加载失败恢复

**情况:** 设备内存不足,PaddleOCR 模型加载失败,应用进入"安全模式"

**描述:**
- 设备运行其他应用,可用内存 <400MB
- PaddleOCRManager.initialize() 返回 false 或抛出 OutOfMemoryError
- 应用功能部分失效,需降级到云端 API

**恢复策略:**
1. **检测内存状态:**
   ```kotlin
   fun getAvailableMemory(): Long {
       val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
       val memoryInfo = ActivityManager.MemoryInfo()
       activityManager.getMemoryInfo(memoryInfo)
       return memoryInfo.availMem  // 可用内存 (字节)
   }
   ```

2. **渐进式模型加载:**
   ```kotlin
   suspend fun initializeWithFallback(): Boolean {
       val availableMem = getAvailableMemory()
       return when {
           availableMem > 800MB -> loadFullModel()
           availableMem > 400MB -> loadLiteModel()  // 仅检测+识别
           else -> {
               Log.w("OCR", "内存不足,禁用本地 OCR")
               return false
           }
       }
   }
   ```

3. **用户提示和降级:**
   ```kotlin
   if (!ocrEngine.initialize()) {
       if (apiKeyManager.hasApiKey()) {
           showSnackbar("本地 OCR 不可用,已切换到云端模式")
           config.ocrMode = OcrMode.REMOTE
       } else {
           showErrorDialog("内存不足,请关闭其他应用后重试")
       }
   }
   ```

4. **内存监控:**
   ```kotlin
   // 定期检查内存压力
   val memoryWatcher = LifecycleObserver { _, event ->
       if (event == Lifecycle.Event.ON_RESUME) {
           if (getAvailableMemory() < LOW_MEMORY_THRESHOLD) {
               releaseUnusedModels()  // 释放未使用模型
           }
       }
   }
   ```

---

### 3. 同时处理多个翻译请求的并发控制

**情况:** 用户快速连续截图,触发多个并发翻译流程

**描述:**
- 用户快速点击悬浮球 3 次
- 3 个协程同时执行 OCR → 合并 → 翻译
- 可能导致内存峰值、CPU 竞争、翻译顺序混乱

**并发控制策略:**
```kotlin
class TranslationConcurrencyGuard {
    private val semaphore = Semaphore(permits = 1)  // 仅允许 1 个翻译任务
    private val queue = Channel<TranslationRequest>(capacity = 10)

    suspend fun translate(request: TranslationRequest): String {
        semaphore.acquire()  // 获取许可
        try {
            return translator.translate(request.text)
        } finally {
            semaphore.release()  // 释放许可
            processNext()  // 处理队列中下一个
        }
    }

    fun enqueue(request: TranslationRequest) {
        queue.trySend(request)
    }
}
```

**替代方案 (队列模式):**
```kotlin
class TranslationQueue {
    private val queue = LinkedList<TranslationRequest>()
    private var isProcessing = false

    @Synchronized
    fun add(request: TranslationRequest) {
        queue.add(request)
        processNext()
    }

    private fun processNext() {
        if (isProcessing || queue.isEmpty()) return

        isProcessing = true
        launch {
            val result = translator.translate(queue.pop().text)
            onTranslationComplete(result)
            isProcessing = false
            processNext()  // 递归处理下一个
        }
    }
}
```

**用户体验:**
- 显示 "正在翻译: 队列中 (2)" 提示
- 取消之前未完成的翻译 (如果用户离开当前会话)
- 按优先级处理 (最新的优先)

---

### 4. OCR 检测框完全超出屏幕边界

**情况:** PaddleOCR 返回的坐标有误 (如 right > screenWidth),导致绘制失败

**描述:**
- 归一化坐标转换错误 (如存储时使用了错误屏幕宽高)
- OCR 模型检测框溢出图片边界 (如训练集和实际图片尺寸不同)
- 导致覆盖层绘制时崩溃或文本显示在屏幕外

**防护策略:**
```kotlin
class CoordinateValidator {
    fun validateAndClamp(
        box: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): BoundingBox {
        // 1. 检查边界
        require(box.left in 0f..1f) { "left 超出 0-1 范围" }
        require(box.top in 0f..1f) { "top 超出 0-1 范围" }
        require(box.right in 0f..1f) { "right 超出 0-1 范围" }
        require(box.bottom in 0f..1f) { "bottom 超出 0-1 范围" }

        // 2. 检查逻辑一致性
        require(box.left < box.right) { "left 必须 < right" }
        require(box.top < box.bottom) { "top 必须 < bottom" }

        // 3. Clamp 到有效范围 (防御性编程)
        return BoundingBox(
            left = box.left.coerceIn(0f, 1f),
            top = box.top.coerceIn(0f, 1f),
            right = box.right.coerceIn(0f, 1f),
            bottom = box.bottom.coerceIn(0f, 1f)
        )
    }
}
```

**日志记录:**
```kotlin
fun safeConvertAndDraw(box: BoundingBox) {
    val validated = validator.validateAndClamp(box, screenWidth, screenHeight)
    if (validated !== box) {
        Log.w("Overlay", "坐标已修正: $box -> $validated")
    }
    val rect = validated.toScreenRect(screenWidth, screenHeight)
    drawOverlay(rect)
}
```

---

### 5. 翻译文本过长导致覆盖层溢出框

**情况:** 翻译后文本远长于原文,无法在原框内显示

**描述:**
- 日文 "こんにちは" (5 字符) 翻译为 "你好你好你好" (20 字符)
- 中文常比日文长,尤其是游戏术语扩展解释
- 原框高度不足,翻译文本被截断或溢出到其他区域

**处理策略:**
```kotlin
class OverlayTextLayout {
    fun fitTextInBox(
        canvas: Canvas,
        box: Rect,
        text: String
    ): Boolean {
        val paint = TextPaint().apply {
            textSize = calculateAdaptiveTextSize(box)
        }

        // 1. 尝试完整显示
        val layout = StaticLayout.Builder
            .setText(text)
            .setTextSize(paint.textSize)
            .setWidth(box.width())
            .build()

        if (layout.height <= box.height()) {
            // 能完整显示
            canvas.drawText(layout, box.left.toFloat(), box.top.toFloat())
            return true
        }

        // 2. 文本过长,截断并添加省略号
        val maxWidth = box.width() - padding
        val truncated = TextUtils.ellipsize(text, maxWidth, paint, "...", false)
        val truncatedLayout = StaticLayout.Builder
            .setText(truncated)
            .setTextSize(paint.textSize)
            .build()

        canvas.drawText(truncatedLayout, box.left.toFloat(), box.top.toFloat())
        Log.i("Overlay", "文本已截断: ${text.length} -> ${truncated.length}")
        return false
    }
}
```

**替代策略 (点击展开):**
```kotlin
class ExpandableOverlayView : View() {
    private var isExpanded = false

    override fun onDraw(canvas: Canvas) {
        if (isExpanded) {
            drawFullText()  // 完整文本,可能溢出到屏幕其他区域
        } else {
            drawTruncatedText()  // 截断文本 + "..." + 点击提示
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            isExpanded = !isExpanded  // 切换展开/收起
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
```

---

### 6. 极低存储空间时语言包下载失败

**情况:** 设备存储 <100MB,语言包下载到 50% 时因空间不足失败

**描述:**
- 用户首次启动应用,自动下载中英日韩语言包 (300MB)
- 设备可用空间不足,下载到一半失败
- 应用处于不可用状态 (无翻译模型)

**处理策略:**
1. **预检查存储空间:**
   ```kotlin
   fun checkAvailableSpace(requiredBytes: Long): Boolean {
       val dir = context.filesDir
       val stats = android.os.StatFs(dir.path)
       val availableBytes = stats.availableBlocksLong * stats.blockSizeLong
       return availableBytes >= requiredBytes
   }
   ```

2. **分批下载语言包:**
   ```kotlin
   suspend fun downloadLanguagePacksProgressive(languages: List<Language>) {
       for (language in languages) {
           if (!checkAvailableSpace(language.sizeBytes)) {
               showErrorDialog("存储空间不足,请清理后下载 $language")
               continue  // 跳过该语言,下载下一个
           }
           downloadLanguagePack(language)
       }
   }
   ```

3. **用户清理引导:**
   ```kotlin
   fun showStorageGuideDialog() {
       val used = getTotalUsedSpace()
       val available = getTotalAvailableSpace()

       AlertDialog.Builder(context)
           .setTitle("存储空间不足")
           .setMessage("当前可用 ${available}MB\n建议清理以下内容:\n\n" +
                   "- 应用缓存 (${getCacheSize()}MB)\n" +
                   - 旧版应用 (${getOldAppsSize()}MB)\n" +
                   - 下载文件 (${getDownloadsSize()}MB)")
           .setPositiveButton("设置") { _, _ ->
               openStorageSettings()  // 跳转到系统存储设置页
           }
           .setNegativeButton("跳过下载") { _, _ ->
               // 仅下载默认语言 (如仅英语)
           }
           .show()
   }
   ```

4. **临时文件清理:**
   ```kotlin
   suspend fun downloadWithCleanup(language: Language) {
       val tempFile = File(context.cacheDir, "temp_${language.code}.bin")
       try {
           downloadTo(tempFile)
           // 下载成功后,清理其他缓存
           cleanOldCache()
           // 移动到最终位置
           tempFile.renameTo(languagePackFile)
       } catch (e: IOException) {
           tempFile.delete()  // 清理临时文件
           throw e
       }
   }
   ```

---

## 依赖项

### 1. PaddleOCR-Lite Android 文档完整性

**依赖类型:** 技术依赖 → PaddleOCR 官方文档

**影响:**
- 文档可能不完整或缺少示例
- JNI 集成部分需要参考官方 Demo 代码
- 模型优化工具 (paddle_lite_opt) 使用说明不清楚

**风险:**
- 开发周期延长,需要反复试验
- 可能集成不稳定或使用错误的 API
- 未来维护困难 (无人熟悉 Paddle-Lite 内部)

**缓解措施:**
- 使用社区封装库 (equationl/paddleocr4android) 作为备选
- 在 GitHub Issues 搜索相似问题和解决方案
- 与 PaddleOCR 社区保持联系,确认最佳实践

---

### 2. Google ML Kit 翻译语言支持

**依赖类型:** 技术依赖 → Google ML Kit Translation API

**影响:**
- 59+ 语言支持,但某些小语种可能不支持
- 语言包大小不可控 (Google 内部管理)
- 翻译质量无法定制 (使用闭源模型)

**风险:**
- 用户需要的语言可能不在支持列表中
- 未来无法切换到其他翻译引擎 (Google 生态锁定)
- 专有名词翻译准确度无法评估 (黑盒模型)

**缓解措施:**
- 提供云端 API 备选,支持更多语言
- 收集用户反馈,评估 ML Kit 翻译质量
- 设置页明确显示 "支持的语言" 列表

---

### 3. Snapdragon 8 Elite Gen 5 NPU 支持文档

**依赖类型:** 技术依赖 → Qualcomm NPU SDK

**影响:**
- Paddle-Lite QNN 后端文档可能不完整
- Hexagon SDK 集成示例代码少
- NPU 性能提升不确定 (理论 20-50%)

**风险:**
- 开发时间可能投入但收益不明显
- NPU 支持可能因设备异构而不同
- 增加测试复杂度 (CPU vs NPU 对比)

**缓解措施:**
- Phase 1 (MVP) 先使用 CPU 推理,确保功能可用
- Phase 2 (优化) 在 OnePlus 15 实测 NPU 性能
- 提供 CPU 降级开关,用户可选择禁用 NPU

---

### 4. Android Keystore 加密算法限制

**依赖类型:** 平台依赖 → AndroidX Security 库

**影响:**
- AndroidX Security 使用 AES256-GCM,不支持自定义算法
- API Key 加密后无法跨设备迁移 (绑定到设备)
- 密钥重置后用户需重新输入 (无法备份)

**风险:**
- 用户换机后无法迁移 API Key
- 密钥找回困难 (如果不备份)
- 多设备同步困难

**缓解措施:**
- 提供 API Key 导出/导入功能 (加密文件)
- 云端同步 (可选,使用用户账号)
- 清晰的密钥重置和恢复流程说明

---

### 5. Google Play APK 体积 150MB 限制

**依赖类型:** 平台依赖 → Google Play 政策

**影响:**
- 无法发布包含所有语言包的 "完整版" APK
- 用户需首次启动下载 300MB+ 数据
- 可能影响转化率 (下载流失率)

**风险:**
- 用户无 Wi-Fi 时无法使用应用
- 下载流量消耗大,移动网络用户流失
- 首次体验差,可能被应用商店差评

**缓解措施:**
- 发布两个 SKU:
  - **精简版 (Lite):** ~80MB,包含核心模型 + 中英
  - **完整版 (Full):** ~150MB,包含所有语言包
- 应用内引导首次下载,提供 "稍后下载" 选项
- AAB (Android App Bundle) + Play Asset Delivery,按需下载语言包

---

### 6. MediaProjection 权限在 Android 14+ 的特殊处理

**依赖类型:** 平台依赖 → Android 14 (API 34) 行为变更

**影响:**
- API 34+ 需要声明前台服务类型 (FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | SPECIAL_USE)
- 权限请求流程可能不同 (需提供使用说明)
- 用户的理解成本增加 (为什么截图服务需要通知权限?)

**风险:**
- 未正确配置可能导致应用无法通过 Google Play 审核
- 用户拒绝通知权限导致服务被杀
- 不同 Android 厂商 (Samsung, Xiaomi 等) 行为可能不同

**缓解措施:**
- 仔细阅读 Android 14 变更日志 ([官方文档](https://developer.android.com/about/versions/14))
- 在 AndroidManifest.xml 中正确声明所有服务类型
- 提供清晰的通知权限说明 ("截图服务需要显示通知图标")
- 测试多个厂商 ROM (OnePlus, Samsung, Xiaomi, MIUI 等)

---

## 开放问题

**用户反馈澄清 (2026-02-12):**
- ✅ **已解决:** 使用现成 Kotlin 库进行 LOC Token 解析
- ✅ **已解决:** 文本合并针对简单场景(游戏、漫画),不处理复杂表格
- ✅ **已解决:** 用户自己提供 LLM,应用内集成支持
- ✅ **已解决:** 实验室增加本地 OCR 开关,用户手动选择翻译引擎
- ✅ **已解决:** 竪排文本按 Y 轴聚类而非 X 轴
- ✅ **待解决:** 6 个高优先级问题需要团队跟进

**当前开放问题:**
1. **P0 优先级 (需要立即解决):**
   - 无 (所有用户反馈问题已澄清)

2. **P1 高优先级 (1-2 周内解决):**
   - PaddleOCR-Lite Android JNI 集成复杂度验证
   - OnePlus 15 性能基准测试(验证 OCR<250ms,翻译<100ms 目标)
   - Google ML Kit 翻译质量评估(由用户提供 LLM,自己验证)
   - 用户 LLM 模型集成(Ktor 客户端,API Key 管理)
   - 实验室 UI 实现(本地 OCR 开关,翻译引擎选择)

3. **P2 中优先级 (1-2 月内解决):**
   - 横竖排合并算法实现与调优
   - 文本合并阈值参数默认值确定
   - MediaProjection 权限过期自动检测逻辑
   - 内存不足时模型加载失败恢复策略

4. **P3 低优先级 (2-4 月内解决):**
   - NPU 加速支持验证(QNN 后端可行性测试)
   - 多列文本/表格布局支持评估(是否需要)
   - 批量翻译模式实现(相册多图处理)
   - OCR 历史记录与缓存功能设计

**行动建议:**
- 立即召开团队评审会议,确认实施优先级
- 分配开发资源,重点解决 P1 高优先级问题
- 每周同步开放问题进展,更新本文档

1. **收集测试数据集:**
   - 准备 100-200 个游戏专有名词 (技能名、装备、地名)
   - 包含日文原文和参考中文翻译
   - 覆盖多种游戏类型 (RPG, FPS, 卡牌)

2. **人工评测流程:**
   - 双盲测试: 不告知是 ML Kit 还是云端 VL 翻译
   - 评分标准: 准确度、通顺度、术语一致性
   - 记录每个样本的详细问题和改进建议

3. **对比基准:**
   - 对标云端 Qwen2.5-VL-32B (当前最准)
   - 对标云端 Qwen2.5-7B (成本和精度平衡)
   - 计算 ML Kit 相对基准的准确度百分比

4. **用户反馈收集:**
   - 应用内评分功能 ("这次翻译准确吗?")
   - 收集用户提交的错误案例
   - 定期分析并优化提示词或后处理规则

**决策依据:**
- 如果 MLKit 准确度 >80% → 保持默认
- 如果 MLKit 准确度 60-80% → 提供云端备选
- 如果 MLKit 准确度 <60% → 考虑切换默认引擎

**预期时间线:** 2-4 周收集数据,1 周分析评估

---

### Q5: 实验室本地 OCR 开关 (优先级: P0)

**用户反馈澄清 (2026-02-12):**
- ✅ **已确认:** 不需要自动升降级逻辑
- ✅ **需求明确:** 在实验室中增加"本地 OCR 识别"开关
- ✅ **实现方式:** 用户选择使用哪个翻译引擎 (PaddleOCR + Google ML Kit / 轨迹流动 LLM)
- ✅ **场景说明:** 当前轨迹流动 API 已经集成 LLM,不需要自动判断

**实验室设置扩展:**
1. **本地 OCR 开关:**
   ```kotlin
   data class AppConfig(
       // ... 其他字段 ...

       // 新增字段
       val enableLocalOcr: Boolean = true,  // 实验室开关
   )
   ```

2. **实验室设置页面:**
   ```kotlin
   @Composable
   fun LabSettingsScreen(
       config: AppConfig,
       onOcrModeChanged: (Boolean) -> Unit
   ) {
       Column {
           // ... 其他实验室设置 ...

           Section("OCR 引擎选择") {
               Row {
                   RadioButton("本地 PaddleOCR", selected = config.enableLocalOcr) {
                       onClick {
                           config.enableLocalOcr = true
                           // 启用本地 OCR,使用 Google ML Kit/轨迹流动 LLM 翻译
                       }
                   }
                   RadioButton("云端 VL 模型", selected = !config.enableLocalOcr) {
                       onClick {
                           config.enableLocalOcr = false
                           // 使用云端 VL 模型进行 OCR+翻译 (一体化)
                       }
                   }
               }
           }
       }
   }
   ```

3. **翻译引擎联动:**
   ```kotlin
   // 当用户选择"本地 OCR"时:
   // - 如果有用户 LLM → 使用用户 LLM
   // - 否 → 使用 Google ML Kit

   // 当用户选择"云端 VL"时:
   // - 使用云端 VL 模型的 OCR+翻译一体化功能

   class OcrTranslationPipeline(
       private val config: AppConfig
   ) {
       suspend fun processScreenshot(bitmap: Bitmap): List<TranslationResult> {
           return when {
               // 本地 OCR 模式
               config.enableLocalOcr -> {
                   val ocrResults = paddleOcrEngine.recognize(bitmap)
                   val mergedTexts = textMerger.merge(ocrResults)

                   // 翻译: 用户 LLM 优先,降级到 Google ML Kit
                   val translated = if (config.enableUserLlm && userLlmEngine != null) {
                       userLlmEngine.translate(mergedTexts.joinToString("\n"))
                   } else {
                       googleMLKit.translate(mergedTexts.joinToString("\n"))
                   }

                   translated.map { text ->
                       TranslationResult(text, text, toBoundingBox(merged.boundingBox))
                   }
               }

               // 云端 VL 模式
               else -> {
                   // 使用云端 VL API 一体化处理
                   cloudVlEngine.translate(bitmap)
               }
           }
       }
   }
   ```

4. **UI 控制逻辑:**
   ```kotlin
   // 本地 OCR 模式下的翻译引擎选择自动跟随:
   if (config.enableLocalOcr) {
       translationEngineSelector.value = TranslationEngine.USER_LLM
       translationEngineSelector.enabled = (config.enableUserLlm && userLlmEngine != null)
   }
   ```

5. **禁用原来自动降级逻辑:**
   ```kotlin
   // 移除 TranslationManager 中的自动降级触发条件判断
   // 翻译引擎选择由用户在实验室中明确控制

   class TranslationManager(
       private val googleMLKit: MLKitTranslator,
       private val userLlmEngine: UserLlmTranslationEngine?,
       private val config: AppConfig
   ) {
       suspend fun translate(
           text: String,
           sourceLang: Language,
           targetLang: Language
       ): String {
           // 直接使用用户选择的翻译引擎,无需自动降级逻辑
           return when {
               config.enableUserLlm && userLlmEngine != null -> {
                   userLlmEngine.translate(text, sourceLang, targetLang)
               }
               else -> {
                   googleMLKit.translate(text, sourceLang, targetLang)
               }
           }
       }
   }
   ```

**实施优先级:**
- ✅ **Phase 1 (当前迭代):** 在实验室添加本地 OCR 开关
- ✅ **UI 调整:** 移除自动降级逻辑,翻译引擎由用户选择
- ⏸️ **不需要:** 原有的混合模式自动降级策略

**实现优势:**
- 用户完全控制 OCR 和翻译引擎选择
- 支持轨迹流动 LLM 作为翻译选项
- 简化逻辑,移除不必要的条件判断
- 在实验室中统一管理实验性功能

**参考:** [来源: 用户反馈, 2026-02-12]

---

---

## 遗漏功能与问题修复 (2026-02-13)

### 1. LocalOcrTabContent 未完成集成

**发现时间:** 2026-02-13
**严重程度:** P0 (阻塞核心功能)

**问题描述:**
`SettingsScreen.kt` 中的 `LocalOcrTabContent` 函数仅显示"开发中"占位符，未集成已创建的组件。

**已创建但未集成的组件:**
| 组件 | 文件路径 | 状态 |
|------|---------|------|
| TranslationModeSelector | `ui/component/TranslationModeSelector.kt` | ✅ 已创建 |
| CloudLlmConfigEditor | `ui/component/CloudLlmConfigEditor.kt` | ✅ 已创建 |
| LanguagePackManagementScreen | `ui/screen/LanguagePackManagementScreen.kt` | ✅ 已创建 |

**当前 LocalOcrTabContent 代码 (问题代码):**
```kotlin
@Composable
private fun LocalOcrTabContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    // 开发中提示  <-- 问题：只显示占位符
    Card(...) {
        Text("🚧 本地 OCR 翻译功能开发中")
    }
    // ... 仅权限配置
}
```

**应有的内容 (PRD 7.4 要求):**
```
┌─────────────────────────────────────────────┐
│  翻译模式                                    │
│  ┌─────────────────────────────────────┐    │
│  │  ◉ 本地 ML Kit 翻译                 │    │
│  │  ○ 云端 LLM 翻译                    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │  云端 LLM 配置（选择云端 LLM 时显示） │    │
│  ├─────────────────────────────────────┤    │
│  │  模型选择、提示词编辑、重置按钮       │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │  语言包管理入口                      │    │
│  │  [管理语言包 >]                      │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

**修复计划:**
1. 修改 `LocalOcrTabContent` 函数
2. 集成 `TranslationModeSelector` 组件
3. 条件显示 `CloudLlmConfigEditor`（选择云端 LLM 时）
4. 添加"语言包管理"导航入口
5. 添加必要的状态和事件到 `SettingsUiState` 和 `SettingsEvent`

**需要新增的状态/事件:**
```kotlin
// SettingsUiState 新增
data class SettingsUiState(
    // ... 现有字段 ...
    val localOcrTranslationMode: TranslationMode = TranslationMode.LOCAL,
    val cloudLlmModel: LlmModel = LlmModel.DEFAULT,
    val cloudLlmPrompt: String? = null,
)

// SettingsEvent 新增
sealed class SettingsEvent {
    // ... 现有事件 ...
    data class LocalOcrTranslationModeChanged(val mode: TranslationMode) : SettingsEvent()
    data class CloudLlmModelChanged(val model: LlmModel) : SettingsEvent()
    data class CloudLlmPromptChanged(val prompt: String) : SettingsEvent()
    data object ResetCloudLlmPrompt : SettingsEvent()
    data object NavigateToLanguagePackManagement : SettingsEvent()
}
```

---

### 2. 实验室页面返回按钮问题

**发现时间:** 2026-02-13
**严重程度:** P1 (影响用户体验)

**问题描述:**
从实验室页面使用手机返回键时，应用直接退出而非返回设置主页。

**期望行为:**
- 手机返回键 → 返回设置主页
- 实验室左上角返回按钮 → 返回设置主页

**当前行为:**
- 手机返回键 → 直接退出应用
- 实验室左上角返回按钮 → 正常返回

**问题定位:**
`LabSettingsScreen` 有 `onNavigateBack` 回调，但 `MainScreen.kt` 中的 `BackHandler` 未正确处理。

**修复方案:**
```kotlin
// MainScreen.kt 中添加 BackHandler
@Composable
fun MainScreen(...) {
    var showLabSettings by remember { mutableStateOf(false) }

    // 添加返回键处理
    BackHandler(enabled = showLabSettings) {
        showLabSettings = false
    }

    if (showLabSettings) {
        LabSettingsScreen(
            onNavigateBack = { showLabSettings = false },
            // ...
        )
    } else {
        // 正常设置页面
    }
}
```

---

### 3. 修复任务清单

| 任务ID | 任务描述 | 优先级 | 依赖 |
|--------|---------|--------|------|
| FIX-001 | 完善 LocalOcrTabContent 集成 | P0 | 无 |
| FIX-002 | 添加语言包管理导航入口 | P0 | FIX-001 |
| FIX-003 | 修复实验室返回键问题 | P1 | 无 |
| FIX-004 | 添加 TranslationMode 状态管理 | P0 | 无 |
| FIX-005 | 添加 CloudLlmConfig 状态管理 | P0 | FIX-004 |

---

## 总结统计

- **总价值条目:** 42
- **需要后续跟进:** 6 (开放问题) + 2 (新发现问题)
- **高优先级项:** 3 (P0) + 3 (P1) = 6

## 下一步行动

- [ ] 与团队评审所有开放问题,确定优先级
- [ ] 评估 PaddleOCR 集成复杂度和备选方案
- [ ] 与硬件团队合作,在 OnePlus 15 上进行性能基准测试
- [ ] 收集用户反馈,验证 Google ML Kit 翻译质量
- [ ] 制定文本合并算法的多列支持路线图
- [ ] 测试 MediaProjection 权限在 Android 14+ 的行为
- [ ] 设计并实现混合模式的自动降级逻辑

---

**文档状态:** 待评审
**最后更新:** 2026-02-12
