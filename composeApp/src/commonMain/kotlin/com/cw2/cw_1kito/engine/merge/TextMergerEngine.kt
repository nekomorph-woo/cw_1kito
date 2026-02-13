package com.cw2.cw_1kito.engine.merge

import com.cw2.cw_1kito.model.MergedText
import com.cw2.cw_1kito.model.MergedTextResult
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.model.OcrDetection
import com.cw2.cw_1kito.model.RectF
import com.cw2.cw_1kito.model.TextDirection
import com.cw2.cw_1kito.util.Logger
import kotlin.math.abs
import kotlin.math.sqrt

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
 * ### 3. 智能聚类（方案 A）
 * - 分析整行的间距分布
 * - 使用 Otsu 算法自动确定合并阈值
 * - 自动区分"同词间隙"和"分词间隙"
 *
 * ### 4. 二次合并（方案 D）
 * - 识别短文本碎片
 * - 对位置相邻的碎片进行二次合并
 *
 * ### 5. 方向检测
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

        /**
         * 智能聚类的最小样本数
         * 少于此数量时无法有效分析分布，回退到普通模式
         */
        private const val SMART_CLUSTERING_MIN_SAMPLES = 3
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

            // 步骤 2：每行内按 X 轴合并（根据配置选择算法）
            var results = lines.map { line ->
                if (config.enableSmartClustering && line.size >= SMART_CLUSTERING_MIN_SAMPLES) {
                    mergeXAxisWithSmartClustering(line, config.xToleranceFactor)
                } else {
                    mergeXAxisInLine(line, config.xToleranceFactor)
                }
            }
            Logger.d("[Merge] X 轴合并完成：${results.size} 个结果")

            // 步骤 3：二次合并（方案 D）
            if (config.enableSecondPass) {
                results = performSecondPassMerge(results, config.fragmentTextThreshold)
                Logger.d("[Merge] 二次合并完成：${results.size} 个结果")
            }

            // 步骤 4：性能日志
            val elapsed = System.currentTimeMillis() - startTime
            Logger.mergeSuccess(detections.size, results.size, elapsed)

            // 步骤 5：合并统计
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
            val yDistance = abs(detection.boundingBox.top - lastDetectionInLine.boundingBox.top)

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
     * 智能聚类合并算法（方案 A）
     *
     * 通过分析整行的间距分布，自动确定合并阈值。
     * 使用 Otsu 算法将间隙分为"小间隙（同词）"和"大间隙（分词）"两类。
     *
     * ## 算法原理
     * 1. 计算所有相邻框的间隙
     * 2. 使用 Otsu 算法找到最佳分割点
     * 3. 小于分割点的间隙视为"同词"，大于的视为"分词"
     * 4. 同词间隙直接合并，分词间隙添加空格
     *
     * @param line 同一行的检测结果列表
     * @param fallbackToleranceFactor 回退时使用的容差因子
     * @return 合并后的文本对象
     */
    private fun mergeXAxisWithSmartClustering(
        line: List<OcrDetection>,
        fallbackToleranceFactor: Float
    ): MergedText {
        require(line.isNotEmpty()) { "行检测列表不能为空" }

        if (line.size < SMART_CLUSTERING_MIN_SAMPLES) {
            Logger.d("[Merge] 样本数不足 (${line.size} < $SMART_CLUSTERING_MIN_SAMPLES)，回退到普通合并")
            return mergeXAxisInLine(line, fallbackToleranceFactor)
        }

        if (line.size == 1) {
            return MergedText.fromSingleDetection(line.first(), detectDirection(line))
        }

        // 按 X 坐标排序
        val sortedLine = line.sortedBy { it.boundingBox.left }

        // 计算所有相邻间隙
        val gaps = mutableListOf<Float>()
        for (i in 1 until sortedLine.size) {
            val prev = sortedLine[i - 1]
            val curr = sortedLine[i]
            val gap = curr.boundingBox.left - prev.boundingBox.right
            gaps.add(gap)
        }

        // 使用 Otsu 算法找到最佳阈值
        val smartThreshold = calculateOtsuThreshold(gaps)

        // 如果无法确定有效阈值，回退到普通模式
        if (smartThreshold == null) {
            Logger.d("[Merge] 无法确定智能阈值，回退到普通合并")
            return mergeXAxisInLine(line, fallbackToleranceFactor)
        }

        Logger.d("[Merge] 智能聚类: 间隙数=${gaps.size}, Otsu阈值=${smartThreshold.toInt()}px")
        Logger.d("[Merge] 间隙分布: ${gaps.sorted().map { it.toInt() }}")

        // 使用智能阈值合并
        val mergedText = StringBuilder()
        val mergedDetections = mutableListOf<OcrDetection>()

        for ((index, detection) in sortedLine.withIndex()) {
            if (mergedDetections.isEmpty()) {
                mergedDetections.add(detection)
                mergedText.append(detection.text)
            } else {
                val gap = gaps.getOrNull(index - 1) ?: 0f

                if (gap <= smartThreshold) {
                    // 小间隙：同词，直接合并
                    mergedDetections.add(detection)
                    mergedText.append(detection.text)
                } else {
                    // 大间隙：分词，添加空格
                    mergedText.append(" ")
                    mergedDetections.add(detection)
                    mergedText.append(detection.text)
                }
            }
        }

        // 计算外接矩形
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

        Logger.d("[Merge] 智能合并结果: [${result.text.take(20)}${if (result.text.length > 20) "..." else ""}] (${result.originalBoxCount} → 1)")
        return result
    }

    /**
     * 使用 Otsu 算法计算最佳阈值
     *
     * Otsu 算法通过最大化类间方差来自动确定分割阈值。
     *
     * @param gaps 间隙列表
     * @return 最佳阈值，如果无法计算则返回 null
     */
    private fun calculateOtsuThreshold(gaps: List<Float>): Float? {
        if (gaps.size < 2) return null

        // 如果所有间隙都相同或非常接近，无法分割
        val maxGap = gaps.maxOrNull() ?: return null
        val minGap = gaps.minOrNull() ?: return null
        if (maxGap - minGap < 1f) return null

        // 创建直方图（256 个 bin）
        val binCount = 256
        val histogram = IntArray(binCount)

        for (gap in gaps) {
            val bin = ((gap - minGap) / (maxGap - minGap) * (binCount - 1)).toInt().coerceIn(0, binCount - 1)
            histogram[bin]++
        }

        // Otsu 算法
        val total = gaps.size
        var sum = 0f
        for (i in 0 until binCount) {
            sum += i * histogram[i]
        }

        var sumB = 0f
        var wB = 0
        var maxVariance = 0f
        var bestThreshold = 0

        for (i in 0 until binCount) {
            wB += histogram[i]
            if (wB == 0) continue

            val wF = total - wB
            if (wF == 0) break

            sumB += i * histogram[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val variance = wB.toFloat() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVariance) {
                maxVariance = variance
                bestThreshold = i
            }
        }

        // 将 bin 索引转换回实际值
        return minGap + (maxGap - minGap) * bestThreshold / (binCount - 1)
    }

    /**
     * 二次合并算法（方案 D）
     *
     * 对第一次合并后的短文本碎片进行二次合并。
     *
     * ## 算法步骤
     * 1. 识别短文本碎片（长度 < fragmentTextThreshold）
     * 2. 计算碎片与相邻文本的距离
     * 3. 距离足够近的碎片进行合并
     *
     * @param firstPassResults 第一次合并的结果
     * @param fragmentTextThreshold 碎片文本长度阈值
     * @return 二次合并后的结果
     */
    private fun performSecondPassMerge(
        firstPassResults: List<MergedText>,
        fragmentTextThreshold: Int
    ): List<MergedText> {
        if (firstPassResults.size < 2) return firstPassResults

        // 识别碎片（短文本 + 小面积）
        val avgHeight = firstPassResults
            .map { it.boundingBox.height }
            .average()
            .toFloat()

        val avgWidth = firstPassResults
            .map { it.boundingBox.width }
            .average()
            .toFloat()

        val isFragment: (MergedText) -> Boolean = { text ->
            text.textLength <= fragmentTextThreshold &&
            text.boundingBox.height < avgHeight * 1.2f &&
            text.boundingBox.width < avgWidth * 1.5f
        }

        // 按 Y 坐标排序
        val sortedResults = firstPassResults.sortedBy { it.boundingBox.top }

        // 合并碎片
        val finalResults = mutableListOf<MergedText>()
        var currentMerge: MergedText? = null
        var currentDetections = mutableListOf<OcrDetection>()

        for (result in sortedResults) {
            if (currentMerge == null) {
                currentMerge = result
                currentDetections = result.originalDetections.toMutableList()
                continue
            }

            // 检查是否应该合并
            val yOverlap = calculateYOverlap(currentMerge.boundingBox, result.boundingBox)
            val xGap = result.boundingBox.left - currentMerge.boundingBox.right

            // 判断条件：Y 轴重叠 + X 轴间隙小 + 至少一个是碎片
            val shouldMerge = yOverlap > 0.5f &&
                xGap < avgWidth * 0.5f &&
                (isFragment(currentMerge) || isFragment(result))

            if (shouldMerge) {
                // 合并
                currentDetections.addAll(result.originalDetections)
                val mergedBox = currentMerge.boundingBox.union(result.boundingBox)
                val mergedText = if (xGap < avgWidth * 0.2f) {
                    currentMerge.text + result.text
                } else {
                    currentMerge.text + " " + result.text
                }

                currentMerge = MergedText(
                    text = mergedText,
                    boundingBox = mergedBox,
                    direction = detectDirectionFromMergedTexts(listOf(currentMerge, result)),
                    originalBoxCount = currentDetections.size,
                    originalDetections = currentDetections.toList()
                )
            } else {
                // 不合并，保存当前结果
                finalResults.add(currentMerge)
                currentMerge = result
                currentDetections = result.originalDetections.toMutableList()
            }
        }

        // 添加最后一个结果
        if (currentMerge != null) {
            finalResults.add(currentMerge)
        }

        val fragmentCount = firstPassResults.count { isFragment(it) }
        val mergedFragmentCount = fragmentCount - finalResults.count { isFragment(it) }
        Logger.d("[Merge] 二次合并: 识别碎片 $fragmentCount 个, 合并了 $mergedFragmentCount 个")

        return finalResults
    }

    /**
     * 计算 Y 轴重叠比例
     *
     * @return 0.0 - 1.0 之间的重叠比例
     */
    private fun calculateYOverlap(box1: RectF, box2: RectF): Float {
        val overlapTop = maxOf(box1.top, box2.top)
        val overlapBottom = minOf(box1.bottom, box2.bottom)
        val overlapHeight = (overlapBottom - overlapTop).coerceAtLeast(0f)

        val minHeight = minOf(box1.height, box2.height)
        return if (minHeight > 0) overlapHeight / minHeight else 0f
    }

    /**
     * 从多个 MergedText 检测文本方向
     */
    private fun detectDirectionFromMergedTexts(texts: List<MergedText>): TextDirection {
        if (texts.isEmpty()) return TextDirection.HORIZONTAL

        // 收集所有原始检测
        val allDetections = texts.flatMap { it.originalDetections }
        return detectDirection(allDetections)
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
