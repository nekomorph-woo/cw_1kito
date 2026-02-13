package com.cw2.cw_1kito.engine.ocr

import android.graphics.Bitmap
import com.cw2.cw_1kito.model.OcrDetection

/**
 * OCR 引擎接口
 *
 * 定义本地 OCR 引擎的标准接口，支持不同的实现方式（MLKit）
 *
 * ## 实现要求
 * - 支持异步初始化（加载模型文件）
 * - 支持文本识别（返回检测框 + 置信度）
 * - 支持方向检测（可选）
 * - 正确管理资源（释放对象）
 */
interface IOcrEngine {

    /**
     * 初始化 OCR 引擎
     *
     * 执行步骤：
     * 1. 检查/复制模型文件
     * 2. 加载本地库（.so）
     * 3. 初始化检测/识别/方向分类模型
     *
     * @return 初始化是否成功
     * @throws OcrInitializationException 初始化失败
     */
    suspend fun initialize(): Boolean

    /**
     * 识别图像中的文本
     *
     * @param bitmap 待识别的图像（建议已预处理）
     * @return 检测结果列表（可能为空）
     * @throws OcrRuntimeException 识别失败
     * @throws OcrModelNotLoadedException 模型未加载
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrDetection>

    /**
     * 识别图像中的文本（带方向检测）
     *
     * 与 [recognize] 的区别：
     * - 使用方向分类器检测文本旋转角度
     * - 适用于可能旋转的图像（如拍照）
     * - 速度稍慢，但准确性更高
     *
     * @param bitmap 待识别的图像
     * @return 检测结果列表（包含角度信息）
     * @throws OcrRuntimeException 识别失败
     */
    suspend fun recognizeWithAngle(bitmap: Bitmap): List<OcrDetection>

    /**
     * 释放 OCR 引擎资源
     *
     * 清理步骤：
     * 1. 释放 C++ 对象
     * 2. 卸载模型
     * 3. 释放内存
     *
     * 注意：释放后需要重新初始化才能使用
     */
    fun release()

    /**
     * 检查引擎是否已初始化
     */
    fun isInitialized(): Boolean

    /**
     * 获取引擎版本信息
     */
    fun getVersion(): String
}
