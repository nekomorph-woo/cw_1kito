package com.cw2.cw_1kito.data.api

/**
 * 增量 JSON 解析器
 * 使用花括号计数状态机，从流式 token 中提取完整的 JSON 对象
 */
class StreamingJsonParser {
    private val buffer = StringBuilder()
    private var braceCount = 0
    private var inString = false
    private var escaped = false

    /**
     * 喂入一段 token 文本，返回解析出的完整 JSON 对象列表
     * @param token 流式接收到的文本片段
     * @return 完整 JSON 对象字符串的列表（可能为空）
     */
    fun feed(token: String): List<String> {
        val results = mutableListOf<String>()

        for (ch in token) {
            if (escaped) {
                buffer.append(ch)
                escaped = false
                continue
            }

            when {
                ch == '\\' && inString -> {
                    buffer.append(ch)
                    escaped = true
                }
                ch == '"' -> {
                    buffer.append(ch)
                    inString = !inString
                }
                !inString && ch == '{' -> {
                    braceCount++
                    buffer.append(ch)
                }
                !inString && ch == '}' -> {
                    braceCount--
                    buffer.append(ch)
                    if (braceCount == 0 && buffer.isNotEmpty()) {
                        results.add(buffer.toString())
                        buffer.delete(0, buffer.length)
                    }
                }
                else -> {
                    if (braceCount > 0) {
                        buffer.append(ch)
                    }
                }
            }
        }

        return results
    }

    /**
     * 重置解析器状态
     */
    fun reset() {
        buffer.delete(0, buffer.length)
        braceCount = 0
        inString = false
        escaped = false
    }
}
