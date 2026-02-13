package com.cw2.cw_1kito.engine.merge

import com.cw2.cw_1kito.model.MergedText
import com.cw2.cw_1kito.model.MergedTextResult
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.model.OcrDetection
import com.cw2.cw_1kito.model.RectF
import com.cw2.cw_1kito.model.TextDirection
import com.cw2.cw_1kito.util.Logger

/**
 * 文本合并引擎
 *
 * 基于空间位置关系，将多个 OCR 检测结果智能合并为逻辑文本单元。
 *
 * ## 核心算法
 *
 * ### 1. Y 轴聚类（行识别）
 * - 计算平均框高度作为基准
 * - Y 轴容差阈值 = 平均高度 × yTolerance
 * - 相邻框 Y 坐标差值 < 阈值 → 判定为同一行
 *
 * ### 2. X 轴合并（同行合并）
 * - 计算平均字符宽度作为基准
 * - X 轴容差阈值 = 平均字符宽度 × xToleranceFactor
 * - 相邻框 X 间隙 < 阈值 → 直接合并（无分隔符）
 * - 相邻框 X 间隙 ≥ 阈值 → 添加空格分隔
 *
 * ### 3. 方向检测
 * - 优先使用 OCR 提供的旋转角度（90°/270° = 竖排）
 * - 备选方案：使用宽高比推断（高度 > 宽度 × 1.5 = 竖排）
 *
 * ## 性能特性
 * - 时间复杂度：O(n log n)，主要由排序决定
 * - 空间复杂度：O(n)，存储聚类结果
 * - 适用于 10-1000 个文本框的场景
 *
 * @author cw_1Kito Team
 * @since 2025-02-12
 */
class TextMergerEngine {

    companion object {
        private const val TAG = "TextMerger"

        /**
         * 竖排文本宽高比阈值
         * 当高度/宽度 > 此值时，判定为竖排文本
         */
        private const val VERTICAL_ASPECT_RATIO_THRESHOLD = 1.5f
    }

    /**
     * 合并检测结果
     *
     * @param detections OCR 检测结果列表（像素坐标）
     * @param config 合并配置（控制阈值）
     * @return 合并后的文本列表
     */
    fun merge(
        detections: List<OcrDetection>,
        config: MergingConfig = MergingConfig.DEFAULT
    ): List<MergedText> {
        // 边界检查
        if (detections.isEmpty()) {
            Logger.w("[Merge] 检测列表为空，返回空结果")
            return emptyList()
        }

        if (detections.size == 1) {
            Logger.d("[Merge] 单个检测结果，无需合并")
            return listOf(
                MergedText.fromSingleDetection(
                    detections.first(),
                    detectDirection(listOf(detections.first()))
                )
            )
        }

        Logger.mergeStart(detections.size)
        val startTime = System.currentTimeMillis()

        try {
            // 步骤 1：按 Y 轴聚类成行
            val lines = clusterByYAxis(detections, config.yTolerance)
            Logger.d("[Merge] Y 轴聚类完成：${lines.size} 行")

            // 步骤 2：每行内按 X 轴合并
            val results = lines.map { line ->
                mergeXAxisInLine(line, config.xToleranceFactor)
            }
            Logger.d("[Merge] X 轴合并完成：${results.size} 个结果")

            // 步骤 3：性能日志
            val elapsed = System.currentTimeMillis() - startTime
            Logger.mergeSuccess(detections.size, results.size, elapsed)

            // 步骤 4：合并统计
            val mergeStats = buildMergeStats(detections, results, elapsed)
            logMergeStats(mergeStats)

            return results

        } catch (e: Exception) {
            Logger.e(e, "[Merge] 合并过程发生异常")
            Logger.mergeWarning("合并失败，回退到原始检测结果: ${e.message}")

            // 回退：返回单个检测结果列表
            return detections.map { detection ->
                MergedText.fromSingleDetection(
                    detection,
                    detectDirection(listOf(detection))
                )
            }
        }
    }

    /**
     * 合并检测结果（返回带统计的结果）
     *
     * @param detections OCR 检测结果列表
     * @param config 合并配置
     * @return 合并结果（包含统计信息）
     */
    fun mergeWithStats(
        detections: List<OcrDetection>,
        config: MergingConfig = MergingConfig.DEFAULT
    ): MergedTextResult {
        val mergedTexts = merge(detections, config)
        return MergedTextResult.create(mergedTexts, detections)
    }

    /**
     * Y 轴聚类算法
     *
     * 将多个文本框按 Y 坐标聚类成行。
     *
     * ## 算法步骤
     * 1. 计算所有框的平均高度
     * 2. 计算容差阈值 = 平均高度 × tolerance
     * 3. 按 Y 坐标（top）排序
     * 4. 遍历排序后的列表：
     *    - 如果当前框与当前行的第一个框 Y 坐标差 < 阈值 → 加入当前行
     *    - 否则 → 创建新行
     *
     * @param detections 检测结果列表
     * @param tolerance Y 轴容差系数（相对于平均高度）
     * @return 二维列表（每行包含多个检测结果）
     */
    private fun clusterByYAxis(
        detections: List<OcrDetection>,
        tolerance: Float
    ): List<List<OcrDetection>> {
        require(tolerance > 0) { "Y 轴容差必须大于 0: $tolerance" }

        // 计算平均高度
        val avgHeight = detections
            .map { it.boundingBox.height }
            .average()
            .toFloat()
            .coerceAtLeast(1f)  // 避免除零

        // 容差阈值
        val threshold = avgHeight * tolerance

        Logger.d("[Merge] 平均高度: ${avgHeight.toInt()}px, Y 轴阈值: ${threshold.toInt()}px (tolerance=$tolerance)")

        // 按 Y 坐标排序
        val sortedDetections = detections.sortedBy { it.boundingBox.top }

        // 聚类成行
        val lines = mutableListOf<MutableList<OcrDetection>>()
        var currentLine = mutableListOf(sortedDetections.first())

        for (i in 1 until sortedDetections.size) {
            val detection = sortedDetections[i]
            val lastDetectionInLine = currentLine.last()

            // 检查 Y 坐标距离（使用 top 坐标）
            val yDistance = kotlin.math.abs(detection.boundingBox.top - lastDetectionInLine.boundingBox.top)

            if (yDistance <= threshold) {
                // 同一行
                currentLine.add(detection)
            } else {
                // 新的一行
                lines.add(currentLine)
                currentLine = mutableListOf(detection)
            }
        }

        // 添加最后一行
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        Logger.d("[Merge] 行聚类结果: ${lines.map { it.size }}")
        return lines
    }

    /**
     * X 轴合并算法（同行内合并）
     *
     * 将同一行内的多个文本框合并为一个逻辑文本单元。
     *
     * ## 算法步骤
     * 1. 按 X 坐标（left）排序
     * 2. 计算平均字符宽度
     * 3. 计算容差阈值 = 平均字符宽度 × toleranceFactor
     * 4. 遍历排序后的列表：
     *    - 如果当前框与前一个框的 X 间隙 < 阈值 → 直接拼接
     *    - 否则 → 添加空格分隔
     * 5. 计算合并后的外接矩形（所有框的并集）
     * 6. 检测文本方向
     *
     * @param line 同一行的检测结果列表
     * @param toleranceFactor X 轴容差系数（相对于平均字符宽度）
     * @return 合并后的文本对象
     */
    private fun mergeXAxisInLine(
        line: List<OcrDetection>,
        toleranceFactor: Float
    ): MergedText {
        require(line.isNotEmpty()) { "行检测列表不能为空" }
        require(toleranceFactor > 0) { "X 轴容差因子必须大于 0: $toleranceFactor" }

        if (line.size == 1) {
            val detection = line.first()
            return MergedText.fromSingleDetection(
                detection,
                detectDirection(line)
            )
        }

        // 按 X 坐标排序
        val sortedLine = line.sortedBy { it.boundingBox.left }

        // 计算平均字符宽度
        val avgCharWidth = sortedLine
            .map { it.boundingBox.width }
            .average()
            .toFloat()
            .coerceAtLeast(1f)  // 避免除零

        // 容差阈值
        val threshold = avgCharWidth * toleranceFactor

        Logger.d("[Merge] 平均字符宽度: ${avgCharWidth.toInt()}px, X 轴阈值: ${threshold.toInt()}px (factor=$toleranceFactor)")

        // 合并文本
        val mergedText = StringBuilder()
        val mergedDetections = mutableListOf<OcrDetection>()

        for (detection in sortedLine) {
            if (mergedDetections.isNotEmpty()) {
                val lastDetection = mergedDetections.last()
                val gap = detection.boundingBox.left - lastDetection.boundingBox.right

                if (gap <= threshold) {
                    // 间隙小，直接合并
                    mergedDetections.add(detection)
                    mergedText.append(detection.text)
                } else {
                    // 间隙大，添加空格分隔
                    mergedText.append(" ")
                    mergedDetections.add(detection)
                    mergedText.append(detection.text)
                }
            } else {
                // 第一个框
                mergedDetections.add(detection)
                mergedText.append(detection.text)
            }
        }

        // 计算外接矩形（所有框的并集）
        val boundingBox = calculateBoundingBox(mergedDetections)

        // 检测方向
        val direction = detectDirection(sortedLine)

        val result = MergedText(
            text = mergedText.toString(),
            boundingBox = boundingBox,
            direction = direction,
            originalBoxCount = line.size,
            originalDetections = mergedDetections.toList()
        )

        Logger.d("[Merge] 合并结果: [${result.text.take(20)}${if (result.text.length > 20) "..." else ""}] (${result.originalBoxCount} → 1)")
        return result
    }

    /**
     * 方向检测算法
     *
     * 检测文本是横排还是竖排。
     *
     * ## 检测策略
     * 1. **优先使用旋转角度**（如果有）：
     *    - 90° 或 270° → 竖排
     *    - 0° 或 180° → 横排
     * 2. **备选方案：宽高比推断**：
     *    - 平均高度 > 平均宽度 × 1.5 → 竖排
     *    - 否则 → 横排
     *
     * @param detections 检测结果列表
     * @return 文本方向
     */
    private fun detectDirection(detections: List<OcrDetection>): TextDirection {
        if (detections.isEmpty()) return TextDirection.HORIZONTAL
        if (detections.size == 1) {
            // 单个框：检查角度或宽高比
            val detection = detections.first()
            return detection.angle?.let { TextDirection.fromAngle(it) }
                ?: detectDirectionByAspectRatio(detection.boundingBox)
        }

        // 多个框：优先检查角度一致性
        val angles = detections
            .mapNotNull { it.angle }
            .distinct()

        if (angles.size == 1) {
            // 所有框角度一致
            return TextDirection.fromAngle(angles.first())
        }

        // 角度不一致或缺失：使用宽高比推断
        val totalWidth = detections.sumOf { it.boundingBox.width.toDouble() }.toFloat()
        val totalHeight = detections.sumOf { it.boundingBox.height.toDouble() }.toFloat()
        val avgWidth = totalWidth / detections.size
        val avgHeight = totalHeight / detections.size

        return if (avgHeight > avgWidth * VERTICAL_ASPECT_RATIO_THRESHOLD) {
            TextDirection.VERTICAL
        } else {
            TextDirection.HORIZONTAL
        }
    }

    /**
     * 基于宽高比检测方向（单个框）
     */
    private fun detectDirectionByAspectRatio(boundingBox: RectF): TextDirection {
        val aspectRatio = boundingBox.height / boundingBox.width.coerceAtLeast(1f)
        return if (aspectRatio > VERTICAL_ASPECT_RATIO_THRESHOLD) {
            TextDirection.VERTICAL
        } else {
            TextDirection.HORIZONTAL
        }
    }

    /**
     * 计算多个检测框的外接矩形（并集）
     *
     * @param detections 检测结果列表
     * @return 外接矩形
     */
    private fun calculateBoundingBox(detections: List<OcrDetection>): RectF {
        require(detections.isNotEmpty()) { "检测列表不能为空" }

        return detections
            .map { it.boundingBox }
            .reduce { acc, box -> acc.union(box) }
    }

    /**
     * 构建合并统计信息
     */
    private fun buildMergeStats(
        detections: List<OcrDetection>,
        results: List<MergedText>,
        elapsedMs: Long
    ): MergeStats {
        val multiBoxMerges = results.count { it.isMultiBoxMerged }
        val totalTextLength = results.sumOf { it.textLength.toDouble() }.toInt()

        return MergeStats(
            originalCount = detections.size,
            mergedCount = results.size,
            multiBoxMerges = multiBoxMerges,
            totalTextLength = totalTextLength,
            elapsedMs = elapsedMs
        )
    }

    /**
     * 记录合并统计信息
     */
    private fun logMergeStats(stats: MergeStats) {
        Logger.i("[Merge] 统计: ${stats.originalCount} → ${stats.mergedCount} (压缩率: ${stats.compressionRate})")
        Logger.i("[Merge] 多框合并: ${stats.multiBoxMerges} 个, 总字符数: ${stats.totalTextLength}, 耗时: ${stats.elapsedMs}ms")
    }

    /**
     * 合并统计数据
     */
    private data class MergeStats(
        val originalCount: Int,
        val mergedCount: Int,
        val multiBoxMerges: Int,
        val totalTextLength: Int,
        val elapsedMs: Long
    ) {
        val compressionRate: Float
            get() = if (originalCount > 0) {
                mergedCount.toFloat() / originalCount
            } else {
                1f
            }
    }
}

/**
 * 扩展函数：使用引擎合并检测结果（简化调用）
 *
 * @param detections 检测结果列表
 * @param config 合并配置
 * @return 合并后的文本列表
 */
fun List<OcrDetection>.mergeText(
    config: MergingConfig = MergingConfig.DEFAULT
): List<MergedText> {
    return TextMergerEngine().merge(this, config)
}

/**
 * 扩展函数：使用引擎合并检测结果（带统计）
 *
 * @param detections 检测结果列表
 * @param config 合并配置
 * @return 合并结果（包含统计信息）
 */
fun List<OcrDetection>.mergeTextWithStats(
    config: MergingConfig = MergingConfig.DEFAULT
): MergedTextResult {
    return TextMergerEngine().mergeWithStats(this, config)
}
