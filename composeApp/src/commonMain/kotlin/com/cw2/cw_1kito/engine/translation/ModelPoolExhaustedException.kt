package com.cw2.cw_1kito.engine.translation

/**
 * 模型池耗尽异常
 *
 * 当模型池中所有模型都达到 RPM 限制时抛出此异常。
 *
 * ## 使用场景
 * - 多模型轮询/池化场景
 * - API 限流处理
 * - 需要告知用户稍后重试的情况
 *
 * @property message 异常消息，默认为友好提示
 */
class ModelPoolExhaustedException(
    message: String = DEFAULT_MESSAGE
) : Exception(message) {

    companion object {
        /**
         * 默认异常消息
         */
        const val DEFAULT_MESSAGE = "所有模型均达到 RPM 限制，请稍后重试"
    }
}
