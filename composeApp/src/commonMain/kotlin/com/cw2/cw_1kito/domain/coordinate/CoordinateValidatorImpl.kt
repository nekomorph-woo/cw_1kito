package com.cw2.cw_1kito.domain.coordinate

import com.cw2.cw_1kito.model.BoundingBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 坐标验证器实现
 *
 * 处理坐标验证和调整的主要逻辑：
 * 1. 边界裁剪 - 确保坐标在 [0, 1] 范围内
 * 2. 最小尺寸扩展 - 确保框体足够大以显示文本
 * 3. 重叠处理 - 解决相邻文本框的重叠问题
 */
class CoordinateValidatorImpl(
    private val config: CoordinateValidatorConfig = CoordinateValidatorConfig.DEFAULT
) : CoordinateValidator {

    override fun validateAndAdjust(
        box: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): BoundingBox {
        // 1. 边界裁剪
        var adjusted = box.clipToBounds()

        // 2. 检查是否需要扩展到最小尺寸
        if (adjusted.width < config.minBoxSize || adjusted.height < config.minBoxSize) {
            adjusted = adjusted.expandToMinSize(
                minSize = config.minBoxSize,
                padding = config.expansionPadding
            )
        }

        // 3. 最终边界检查（确保扩展后仍在范围内）
        adjusted = adjusted.clipToBounds()

        // 4. 验证最终框体有效性
        require(isValid(adjusted)) {
            "调整后的边界框无效: left=${adjusted.left}, top=${adjusted.top}, " +
            "right=${adjusted.right}, bottom=${adjusted.bottom}"
        }

        return adjusted
    }

    override fun validateAndAdjustAll(
        boxes: List<BoundingBox>,
        screenWidth: Int,
        screenHeight: Int
    ): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()

        // 1. 先验证和调整每个框
        val validated = boxes.map { validateAndAdjust(it, screenWidth, screenHeight) }

        // 2. 过滤无效框
        val validBoxes = validated.filter { isValid(it) }

        if (validBoxes.isEmpty()) return emptyList()

        // 3. 如果启用合并，先合并接近的框
        val processedBoxes = if (config.enableMerging) {
            mergeNearbyBoxes(validBoxes)
        } else {
            validBoxes
        }

        // 4. 处理重叠
        return resolveOverlaps(processedBoxes)
    }

    override fun isValid(box: BoundingBox): Boolean {
        return box.left >= 0f &&
               box.top >= 0f &&
               box.right <= 1f &&
               box.bottom <= 1f &&
               box.left < box.right &&
               box.top < box.bottom &&
               box.width > 0f &&
               box.height > 0f
    }

    /**
     * 合并接近的框体
     *
     * 将距离很近的框体合并为一个更大的框，适用于
     * OCR 将连续文本识别为多个小块的情况。
     */
    private fun mergeNearbyBoxes(boxes: List<BoundingBox>): List<BoundingBox> {
        if (boxes.size <= 1) return boxes

        val merged = mutableListOf<BoundingBox>()
        val used = mutableSetOf<Int>()

        for (i in boxes.indices) {
            if (i in used) continue

            var currentBox = boxes[i]
            used.add(i)

            // 查找可以合并的框
            for (j in (i + 1)..boxes.lastIndex) {
                if (j in used) continue

                val otherBox = boxes[j]
                if (shouldMerge(currentBox, otherBox)) {
                    currentBox = mergeBoxes(currentBox, otherBox)
                    used.add(j)
                }
            }

            merged.add(currentBox)
        }

        return merged
    }

    /**
     * 判断两个框是否应该合并
     */
    private fun shouldMerge(box1: BoundingBox, box2: BoundingBox): Boolean {
        // 计算中心点距离
        val centerDistanceX = abs(box1.centerX - box2.centerX)
        val centerDistanceY = abs(box1.centerY - box2.centerY)

        // 如果在任一方向上距离很近，考虑合并
        return centerDistanceX < config.mergeDistanceThreshold ||
               centerDistanceY < config.mergeDistanceThreshold
    }

    /**
     * 合并两个框体
     */
    private fun mergeBoxes(box1: BoundingBox, box2: BoundingBox): BoundingBox {
        return BoundingBox(
            left = min(box1.left, box2.left),
            top = min(box1.top, box2.top),
            right = max(box1.right, box2.right),
            bottom = max(box1.bottom, box2.bottom)
        )
    }

    /**
     * 解决框体重叠问题
     *
     * 策略：
     * 1. 按面积排序，优先保留大的框
     * 2. 对于重叠的框，选择水平或垂直方向分离
     */
    private fun resolveOverlaps(boxes: List<BoundingBox>): List<BoundingBox> {
        if (boxes.size <= 1) return boxes

        // 按面积降序排序（优先保留大框）
        val sorted = boxes.sortedByDescending { it.area }
        val result = mutableListOf<BoundingBox>()

        for (box in sorted) {
            var adjustedBox = box

            // 与已有框进行重叠检查和处理
            var iteration = 0
            var hasOverlap = true
            while (hasOverlap && iteration < config.maxOverlapIterations) {
                hasOverlap = false
                for (existing in result) {
                    if (adjustedBox.hasSignificantOverlap(existing, config.overlapThreshold)) {
                        adjustedBox = resolveOverlap(adjustedBox, existing)
                        hasOverlap = true
                        break
                    }
                }
                iteration++
            }

            result.add(adjustedBox.clipToBounds())
        }

        return result
    }

    /**
     * 解决两个框之间的重叠
     *
     * 策略：根据相对位置选择移动方向
     */
    private fun resolveOverlap(box: BoundingBox, other: BoundingBox): BoundingBox {
        val intersectionLeft = max(box.left, other.left)
        val intersectionTop = max(box.top, other.top)
        val intersectionRight = min(box.right, other.right)
        val intersectionBottom = min(box.bottom, other.bottom)

        val intersectionWidth = intersectionRight - intersectionLeft
        val intersectionHeight = intersectionBottom - intersectionTop

        // 根据相对位置决定移动方向
        val horizontalOverlap = intersectionWidth / box.width
        val verticalOverlap = intersectionHeight / box.height

        return when {
            // 如果在另一个框的左边，向左移动
            box.right < other.right -> {
                val newRight = other.left - 0.005f
                box.copy(right = newRight)
            }
            // 如果在另一个框的右边，向右移动
            box.left > other.left -> {
                val newLeft = other.right + 0.005f
                box.copy(left = newLeft)
            }
            // 如果在另一个框的上方，向上移动
            box.bottom < other.bottom -> {
                val newBottom = other.top - 0.005f
                box.copy(bottom = newBottom)
            }
            // 如果在另一个框的下方，向下移动
            box.top > other.top -> {
                val newTop = other.bottom + 0.005f
                box.copy(top = newTop)
            }
            // 默认：根据重叠方向选择移动
            horizontalOverlap > verticalOverlap -> {
                // 水平重叠更大，水平移动
                if (box.centerX < other.centerX) {
                    box.copy(right = other.left - 0.005f)
                } else {
                    box.copy(left = other.right + 0.005f)
                }
            }
            else -> {
                // 垂直重叠更大，垂直移动
                if (box.centerY < other.centerY) {
                    box.copy(bottom = other.top - 0.005f)
                } else {
                    box.copy(top = other.bottom + 0.005f)
                }
            }
        }
    }
}
