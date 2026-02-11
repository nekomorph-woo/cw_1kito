# Value Details Outside Template Scope

This document captures valuable details from the original brainstorming that did not fit into the standardized template structure but may be important for future reference or iteration.

## Generation Context
- **Input File:** 安卓翻译app功能需求分析.md
- **Template Scheme:** A (General Engineering)
- **Generated On:** 2025-02-10
- **Updated On:** 2025-02-10 (基于用户澄清)
- **Total Items Captured:** 20

---

## Future Considerations

| Idea | Description | Potential Value |
|------|-------------|-----------------|
| 离线模式支持 | 使用本地 OCR 模型（如 Tesseract）处理简单场景，减少 API 调用 | 降低延迟，节省 API 成本 |
| 翻译历史记录 | 保存用户的翻译历史，支持回顾和收藏 | 学习辅助，用户体验改进 |
| 原文/译文切换 | 点击覆盖层切换显示原文和译文 | 语言学习场景 |
| 自定义悬浮球样式 | 允许用户自定义悬浮球图标和大小 | 个性化体验 |
| 区域翻译 | 仅翻译用户选择的屏幕区域 | 减少处理时间，降低成本 |
| 实时翻译模式 | 持续监控屏幕变化并自动翻译 | 游戏场景下的连续翻译 |
| 多提供商轮换 | 根据各 API 的响应速度和成本动态选择提供商 | 优化性能和成本 |
| 批量翻译预加载 | 预翻译常见应用界面文本 | 即时显示，降低延迟感知 |

*Count: 8 items*

---

## Alternative Approaches

| Approach | Why Discarded | When to Reconsider |
|----------|---------------|-------------------|
| 传统 OCR + 翻译 API 分离方案 | 需要两次 API 调用，成本更高，延迟更大 | 如果多模态 API 成本过高 |
| 使用 AccessibilityService 自动读取文本 | 仅支持可访问性元素，无法翻译图片中的文字 | 如果需求改为仅翻译界面文本 |
| 用户自建后端代理 API | 增加开发和维护成本，需要服务器资源 | 如果用户量达到规模化 |
| 截图后手动选择文字区域 | 增加用户操作步骤，体验不如自动识别 | 如果自动识别准确率过低 |
| 使用纯本地 OCR 和翻译 | 准确率不如云端大模型，语言支持有限 | 如果用户隐私要求极高 |

*Count: 5 items*

---

## Implementation Details

| Detail | Context | Reference |
|--------|---------|-----------|
| 坐标归一化推荐使用 0-1 范围 | 便于适配不同分辨率，避免重新计算 | [Source: 原始文档, 技术分解部分] |
| 图像压缩建议保持最大边 2048px | 平衡清晰度和传输速度 | [Source: 原始文档, 潜在挑战] |
| 使用 TYPE_APPLICATION_OVERLAY 窗口类型 | Android 8.0+ 推荐的悬浮窗类型 | [Source: 原始文档, 悬浮窗技术] |
| VirtualDisplay 配置使用 ImageReader 获取画面 | MediaProjection 的标准实现方式 | [Source: 原始文档, 屏幕录制/截图] |
| 建议使用 Kotlin 协程处理异步操作 | 避免回调地狱，代码更简洁 | [Source: 原始文档, Kotlin 开发流程] |
| 文字换行使用 StaticLayout 而非 Paint.drawText | 自动处理多行文本和换行 | [Source: 原始文档, 图形绘制] |
| 建议项目文件放在 Windows 文件系统 | 避免跨文件系统访问的性能问题 | [Source: 原始文档, WSL 环境配置] |
| 在 .gitattributes 中强制使用 LF 换行符 | 避免跨平台开发时的换行符问题 | [Source: 原始文档, Git 配置] |

*Count: 8 items*

---

## User Insights

| Insight | Source | Implication |
|---------|--------|-------------|
| 用户可能不熟悉悬浮窗权限 | 原始文档提到需要引导用户 | 需要提供清晰的权限说明和首次使用引导 |
| 用户可能对录屏权限有隐私顾虑 | Android 系统每次都会弹窗提示 | 需要在首次使用时解释为什么需要录屏权限 |
| 3-10 秒的延迟可能让用户认为卡死 | 原始文档的潜在挑战部分 | 必须提供明显的加载状态反馈（动画 + 进度） |
| 英文翻译成中文字数变少，反之亦然 | 原始文档的文字排版挑战 | 需要实现字体自适应或框体扩展机制 |

*Count: 4 items*

---

## Edge Cases

| Case | Description | Handling Consideration |
|------|-------------|------------------------|
| 大模型未识别到任何文字 | 返回空结果或非预期格式 | 显示"未检测到文字"提示，建议用户重试 |
| 坐标超出屏幕范围 | 返回的坐标值可能 > 1000 或 < 0 | 实现坐标裁剪和验证逻辑 |
| 截图时屏幕方向变化 | 横屏/竖屏切换导致坐标错位 | 锁定截图时的屏幕方向，或在结果中包含方向信息 |
| 系统对话框覆盖悬浮球 | 权限弹窗等系统级UI可能遮挡 | 检测系统UI，自动调整悬浮球位置 |
| API 返回的坐标顺序不一致 | 可能是 [x1,y1,x2,y2] 或 [left,top,right,bottom] | 统一解析逻辑，支持多种格式 |
| 多个文本框重叠 | 翻译结果可能互相遮挡 | 实现碰撞检测和位置调整算法 |
| 文字过长导致矩形溢出屏幕 | 翻译后的中文可能比英文原文更长 | 实现文本截断或动态缩小字号 |

*Count: 7 items*

---

## Dependencies

| Dependency | Type | Impact |
|-------------|------|--------|
| 大模型 API 稳定性 | External API | 如果 API 不可用，核心功能无法使用 |
| 用户授予悬浮窗权限 | System Permission | 如果用户拒绝，应用无法正常工作 |
| 用户授予录屏权限 | System Permission | 每次启动服务都需要用户确认 |
| 网络连接质量 | Infrastructure | 影响响应速度和用户体验 |
| 设备性能 | Hardware | 低端设备可能处理速度较慢 |
| Android 系统版本 | Platform | 需要适配不同 Android 版本的差异 |
| 电池优化设置 | System Setting | 激进的电池优化可能杀死后台服务 |

*Count: 7 items*

---

## Open Questions

| Question | Priority | Suggested Resolution |
|----------|----------|----------------------|
| 如何根据实际使用数据优化坐标容错参数？ | P1 | 收集用户反馈，调整扩展边距和最小框尺寸 |
| 是否需要支持离线模式？ | P2 | 调研本地 OCR 方案（如 Tesseract、ML Kit） |
| 如何优化图像压缩算法？ | P1 | 平衡清晰度和传输速度，测试不同压缩比 |
| 是否支持 iOS 平台？ | P2 | 评估市场需求和技术可行性 |

*Count: 4 items*

---

## Summary Statistics

- **Total Value Items:** 43
- **Items Requiring Follow-up:** 11 (Open Questions + Dependencies)
- **High-Priority Items:** 0 (所有 P0 问题已解决)

---

## Next Actions

1. [ ] **设计权限引导流程** - 制作首次使用时的权限说明界面
2. [ ] **实现加载状态反馈** - 设计明显的加载动画和进度提示
3. [ ] **实现电池优化引导** - 引导用户关闭电池优化
4. [ ] **实现坐标验证和扩展算法** - 按照架构文档中的方案实现
5. [ ] **实现文本自适应排版** - 按照架构文档中的方案实现
6. [ ] **实现防腐层** - 隔离外部 API 结构

---

## Maintenance Notes

### Related Documents
- 02_PRD.md - 产品需求文档
- 03_System_Architecture.md - 系统架构设计
- 04_API_Documentation.md - API 文档

### Update History
| Date | Changes | Author |
|------|---------|--------|
| 2025-02-10 | Initial creation | Doc Architect |
| 2025-02-10 | 更新：确定硅基流动 API、用户自备 Key、添加防腐层设计 | Doc Architect |

### Review Schedule
建议在每个开发阶段结束时审查此文档，更新已解决的问题和新增的考虑事项。
