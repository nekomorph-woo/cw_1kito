package com.cw2.cw_1kito.model

/**
 * 坐标模式枚举
 * 用于判断 LLM 返回的坐标类型
 */
enum class CoordinateMode {
    /** 等待判断（尚未收到坐标数据） */
    PENDING,

    /** 归一化 0-1000 坐标（大多数 VLM 使用） */
    NORMALIZED_1000,

    /** 像素坐标 */
    PIXEL;

    companion object {
        /**
         * 根据坐标值自动检测坐标模式
         * @param coords 坐标列表 [x1, y1, x2, y2]
         * @param imageWidth 图像宽度（像素），用于辅助判断
         * @param imageHeight 图像高度（像素），用于辅助判断
         * @return 检测到的坐标模式
         */
        fun detectCoordinateMode(
            coords: List<Int>,
            imageWidth: Int = 0,
            imageHeight: Int = 0
        ): CoordinateMode {
            if (coords.size < 4) return PENDING

            val maxVal = coords.max()

            return when {
                // 所有坐标都在 0-1000 范围内，认为是归一化坐标
                maxVal <= 1000 -> NORMALIZED_1000
                // 坐标超过 1000，认为是像素坐标
                else -> PIXEL
            }
        }
    }
}
