package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 合并后的文本对象
 * 将多个相邻的 OCR 检测结果合并为一个逻辑单元
 */
@Serializable
data class MergedText(
    /** 合并后的完整文本 */
    val text: String,

    /** 合并后的外接矩形（像素坐标） */
    val boundingBox: RectF,

    /** 文本方向 */
    val direction: TextDirection,

    /** 合并前的原始框数量 */
    val originalBoxCount: Int,

    /** 合并前的原始检测结果列表（可选，用于调试和验证） */
    val originalDetections: List<OcrDetection> = emptyList()
) {
    init {
        require(originalBoxCount > 0) {
            "合并的框数量必须大于 0，实际: $originalBoxCount"
        }
        require(text.isNotBlank()) {
            "合并后的文本不能为空"
        }
    }

    /** 合并比率（原始框数 / 1） */
    val mergeRatio: Float get() = originalBoxCount.toFloat()

    /** 检查是否为多框合并 */
    val isMultiBoxMerged: Boolean get() = originalBoxCount > 1

    /** 文本长度 */
    val textLength: Int get() = text.length

    /** 平均每个框的文本长度 */
    val avgTextLengthPerBox: Float get() = textLength.toFloat() / originalBoxCount

    companion object {
        /**
         * 从单个检测结果创建合并文本
         */
        fun fromSingleDetection(detection: OcrDetection, direction: TextDirection = TextDirection.HORIZONTAL): MergedText {
            return MergedText(
                text = detection.text,
                boundingBox = detection.boundingBox,
                direction = direction,
                originalBoxCount = 1,
                originalDetections = listOf(detection)
            )
        }

        /**
         * 合并多个检测结果
         * @param detections 要合并的检测结果列表
         * @param text 合并后的文本
         * @param direction 文本方向
         */
        fun mergeDetections(
            detections: List<OcrDetection>,
            text: String,
            direction: TextDirection
        ): MergedText {
            require(detections.isNotEmpty()) {
                "检测列表不能为空"
            }

            // 计算合并后的边界框（所有框的并集）
            val mergedBox = detections
                .map { it.boundingBox }
                .reduce { acc, box -> acc.union(box) }

            return MergedText(
                text = text,
                boundingBox = mergedBox,
                direction = direction,
                originalBoxCount = detections.size,
                originalDetections = detections
            )
        }

        /**
         * 按行合并检测结果
         * @param rows 二维检测结果列表（每行包含多个检测结果）
         * @param rowSeparator 行分隔符
         * @param boxSeparator 同行内框分隔符
         */
        fun mergeByRows(
            rows: List<List<OcrDetection>>,
            direction: TextDirection = TextDirection.HORIZONTAL,
            rowSeparator: String = "\n",
            boxSeparator: String = ""
        ): List<MergedText> {
            return rows.map { row ->
                val text = row.joinToString(boxSeparator) { it.text }
                mergeDetections(row, text, direction)
            }
        }
    }
}

/**
 * 文本合并结果
 * 包含合并后的文本列表和合并统计信息
 */
@Serializable
data class MergedTextResult(
    /** 合并后的文本列表 */
    val mergedTexts: List<MergedText>,

    /** 原始检测结果数量 */
    val originalDetectionCount: Int,

    /** 合并后的文本数量 */
    val mergedCount: Int,

    /** 平均置信度 */
    val averageConfidence: Float
) {
    /** 合并效率（原始数 / 合并后数） */
    val mergeEfficiency: Float get() =
        if (mergedCount > 0) originalDetectionCount.toFloat() / mergedCount else 1f

    /** 压缩率（1 / 合并效率） */
    val compressionRatio: Float get() =
        if (originalDetectionCount > 0) mergedCount.toFloat() / originalDetectionCount else 1f

    companion object {
        /**
         * 创建合并结果
         */
        fun create(mergedTexts: List<MergedText>, originalDetections: List<OcrDetection>): MergedTextResult {
            val avgConfidence = if (originalDetections.isNotEmpty()) {
                originalDetections.map { it.confidence }.average().toFloat()
            } else {
                0f
            }

            return MergedTextResult(
                mergedTexts = mergedTexts,
                originalDetectionCount = originalDetections.size,
                mergedCount = mergedTexts.size,
                averageConfidence = avgConfidence
            )
        }
    }
}
