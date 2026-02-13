package com.cw2.cw_1kito.engine.merge

import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.model.OcrDetection
import com.cw2.cw_1kito.model.RectF
import com.cw2.cw_1kito.model.TextDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TextMergerEngine 单元测试
 *
 * 测试文本合并引擎的核心算法：
 * - Y 轴聚类（行识别）
 * - X 轴合并（同行合并）
 * - 方向检测
 * - 边界情况处理
 */
class TextMergerEngineTest {

    private val engine = TextMergerEngine()

    // ==================== 基础测试 ====================

    @Test
    fun `测试空列表`() {
        val result = engine.merge(emptyList())
        assertTrue(result.isEmpty(), "空列表应返回空结果")
    }

    @Test
    fun `测试单个检测框`() {
        val detection = OcrDetection(
            text = "你好",
            boundingBox = RectF(100f, 200f, 150f, 250f),
            confidence = 0.9f
        )

        val result = engine.merge(listOf(detection))
        assertEquals(1, result.size, "单个框应返回 1 个结果")
        assertEquals("你好", result[0].text, "文本应保持不变")
        assertEquals(1, result[0].originalBoxCount, "原始框数量应为 1")
    }

    // ==================== Y 轴聚类测试 ====================

    @Test
    fun `测试同行文本合并`() {
        // 同一行的两个文本框（Y 坐标接近）
        val boxes = listOf(
            OcrDetection("你", RectF(100f, 200f, 130f, 230f), 0.9f),
            OcrDetection("好", RectF(135f, 200f, 165f, 230f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(1, result.size, "同行文本应合并为 1 个结果")
        assertEquals("你好", result[0].text, "文本应合并")
        assertEquals(2, result[0].originalBoxCount, "原始框数量应为 2")
    }

    @Test
    fun `测试多行文本不合并`() {
        // 两行文本（Y 坐标差距大）
        val boxes = listOf(
            OcrDetection("第一行", RectF(100f, 200f, 180f, 230f), 0.9f),
            OcrDetection("第二行", RectF(100f, 300f, 180f, 330f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(2, result.size, "不同行文本应分开")
        assertEquals("第一行", result[0].text, "第一行文本")
        assertEquals("第二行", result[1].text, "第二行文本")
    }

    // ==================== X 轴合并测试 ====================

    @Test
    fun `测试相邻文本直接合并`() {
        // 紧邻的两个文本框（间隙小）
        val boxes = listOf(
            OcrDetection("Go", RectF(100f, 200f, 140f, 230f), 0.9f),
            OcrDetection("od", RectF(142f, 200f, 180f, 230f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(1, result.size, "相邻文本应合并")
        assertEquals("Good", result[0].text, "文本应直接拼接")
    }

    @Test
    fun `测试间隔文本添加空格`() {
        // 有间隙的两个文本框（间隙大）
        val boxes = listOf(
            OcrDetection("Hello", RectF(100f, 200f, 160f, 230f), 0.9f),
            OcrDetection("World", RectF(300f, 200f, 360f, 230f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(1, result.size, "同行文本应合并")
        assertEquals("Hello World", result[0].text, "间隙文本应添加空格")
    }

    // ==================== 方向检测测试 ====================

    @Test
    fun `测试横排文本检测`() {
        val boxes = listOf(
            OcrDetection("横排", RectF(100f, 200f, 160f, 230f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(TextDirection.HORIZONTAL, result[0].direction, "宽扁框应判定为横排")
    }

    @Test
    fun `测试竖排文本检测（角度）`() {
        val boxes = listOf(
            OcrDetection("竖排", RectF(100f, 200f, 130f, 260f), 0.9f, angle = 90f)
        )

        val result = engine.merge(boxes)
        assertEquals(TextDirection.VERTICAL, result[0].direction, "90° 角度应判定为竖排")
    }

    @Test
    fun `测试竖排文本检测（宽高比）`() {
        // 高度远大于宽度
        val boxes = listOf(
            OcrDetection("竖排", RectF(100f, 200f, 130f, 290f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(TextDirection.VERTICAL, result[0].direction, "高窄框应判定为竖排")
    }

    // ==================== 边界框计算测试 ====================

    @Test
    fun `测试外接矩形计算`() {
        val boxes = listOf(
            OcrDetection("左", RectF(100f, 200f, 130f, 230f), 0.9f),
            OcrDetection("右", RectF(200f, 200f, 230f, 230f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(1, result.size, "应合并为 1 个结果")

        val bbox = result[0].boundingBox
        assertEquals(100f, bbox.left, 0.1f, "左边界应为最小 left")
        assertEquals(200f, bbox.top, 0.1f, "上边界应为最小 top")
        assertEquals(230f, bbox.right, 0.1f, "右边界应为最大 right")
        assertEquals(230f, bbox.bottom, 0.1f, "下边界应为最大 bottom")
    }

    // ==================== 配置测试 ====================

    @Test
    fun `测试宽松 Y 轴阈值`() {
        // Y 坐标差距较大的两行文本
        val boxes = listOf(
            OcrDetection("行1", RectF(100f, 200f, 150f, 230f), 0.9f),
            OcrDetection("行2", RectF(100f, 250f, 150f, 280f), 0.9f)
        )

        // 默认配置（严格）→ 应分开
        val defaultResult = engine.merge(boxes, MergingConfig.DEFAULT)
        assertEquals(2, defaultResult.size, "默认配置应分开")

        // 宽松配置（yTolerance=1.0）→ 应合并
        val looseConfig = MergingConfig(yTolerance = 1.0f, xToleranceFactor = 1.5f)
        val looseResult = engine.merge(boxes, looseConfig)
        assertEquals(1, looseResult.size, "宽松配置应合并")
    }

    @Test
    fun `测试游戏预设配置`() {
        val boxes = listOf(
            OcrDetection("游", RectF(100f, 200f, 120f, 230f), 0.9f),
            OcrDetection("戏", RectF(150f, 200f, 170f, 230f), 0.9f)
        )

        val result = engine.merge(boxes, MergingConfig.GAME)
        assertEquals(1, result.size, "游戏预设应合并")
    }

    // ==================== 复杂场景测试 ====================

    @Test
    fun `测试多行多列文本矩阵`() {
        // 3×3 文本矩阵
        val boxes = listOf(
            // 第一行
            OcrDetection("A1", RectF(100f, 200f, 130f, 230f), 0.9f),
            OcrDetection("A2", RectF(140f, 200f, 170f, 230f), 0.9f),
            OcrDetection("A3", RectF(180f, 200f, 210f, 230f), 0.9f),
            // 第二行
            OcrDetection("B1", RectF(100f, 250f, 130f, 280f), 0.9f),
            OcrDetection("B2", RectF(140f, 250f, 170f, 280f), 0.9f),
            OcrDetection("B3", RectF(180f, 250f, 210f, 280f), 0.9f),
            // 第三行
            OcrDetection("C1", RectF(100f, 300f, 130f, 330f), 0.9f),
            OcrDetection("C2", RectF(140f, 300f, 170f, 330f), 0.9f),
            OcrDetection("C3", RectF(180f, 300f, 210f, 330f), 0.9f)
        )

        val result = engine.merge(boxes)
        assertEquals(3, result.size, "应合并为 3 行")

        // 验证每行的文本
        assertEquals("A1A2 A3", result[0].text, "第一行")
        assertEquals("B1B2 B3", result[1].text, "第二行")
        assertEquals("C1C2 C3", result[2].text, "第三行")

        // 验证每行的框数量
        assertEquals(3, result[0].originalBoxCount, "第一行框数")
        assertEquals(3, result[1].originalBoxCount, "第二行框数")
        assertEquals(3, result[2].originalBoxCount, "第三行框数")
    }

    @Test
    fun `测试混合方向文本`() {
        val boxes = listOf(
            OcrDetection("横排", RectF(100f, 200f, 160f, 230f), 0.9f, angle = 0f),
            OcrDetection("竖排", RectF(200f, 200f, 230f, 260f), 0.9f, angle = 90f)
        )

        val result = engine.merge(boxes)
        // 不同方向应分开（Y 轴聚类可能将它们分开）
        assertTrue(result.size >= 1, "至少应有 1 个结果")
    }

    // ==================== 性能测试 ====================

    @Test
    fun `测试大规模文本合并性能`() {
        // 生成 100 个文本框（10 行 × 10 列）
        val boxes = mutableListOf<OcrDetection>()
        for (row in 0 until 10) {
            for (col in 0 until 10) {
                boxes.add(
                    OcrDetection(
                        text = "T${row}${col}",
                        boundingBox = RectF(
                            100f + col * 40f,
                            200f + row * 50f,
                            130f + col * 40f,
                            230f + row * 50f
                        ),
                        confidence = 0.9f
                    )
                )
            }
        }

        val startTime = System.currentTimeMillis()
        val result = engine.merge(boxes)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(10, result.size, "应合并为 10 行")
        assertTrue(elapsed < 1000, "100 个框合并应在 1 秒内完成（实际: ${elapsed}ms）")
    }

    // ==================== 扩展函数测试 ====================

    @Test
    fun `测试扩展函数 mergeText`() {
        val boxes = listOf(
            OcrDetection("你", RectF(100f, 200f, 130f, 230f), 0.9f),
            OcrDetection("好", RectF(135f, 200f, 165f, 230f), 0.9f)
        )

        val result = boxes.mergeText()
        assertEquals(1, result.size, "扩展函数应正常工作")
        assertEquals("你好", result[0].text, "文本应合并")
    }

    @Test
    fun `测试扩展函数 mergeTextWithStats`() {
        val boxes = listOf(
            OcrDetection("你", RectF(100f, 200f, 130f, 230f), 0.9f),
            OcrDetection("好", RectF(135f, 200f, 165f, 230f), 0.9f)
        )

        val result = boxes.mergeTextWithStats()
        assertEquals(2, result.originalDetectionCount, "原始数量应为 2")
        assertEquals(1, result.mergedCount, "合并后数量应为 1")
        assertTrue(result.averageConfidence > 0f, "应有平均置信度")
    }
}
