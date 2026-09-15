package com.hmv.server

/**
 * HTTP Range 说明。
 *
 * @param rangeStart 起始字节（含），0 表示从头
 * @param rangeEnd 结束字节（含，区间为闭区间），-1 表示到文件末尾
 * @param suffixLength 若为后缀请求 bytes=-N，表示取文件最后 N 字节（此时 rangeStart/rangeEnd 意义由 [isSuffix] 决定）
 * @param isSuffix 是否是 bytes=-N 的后缀请求
 */
data class ByteRange(
    val rangeStart: Long = 0,
    val rangeEnd: Long = -1,
    val suffixLength: Long = -1,
    val isSuffix: Boolean = false
)

/**
 * 解析 HTTP Range 请求头。仅支持单范围（多范围返回 null，由上层降级为整文件响应）。
 */
object RangeParser {

    fun parse(header: String?): ByteRange? {
        if (header.isNullOrBlank()) return null
        val text = header.trim()
        if (!text.startsWith("bytes=", ignoreCase = true)) return null
        val spec = text.substringAfter('=').trim()
        if (spec.isEmpty() || spec.contains(',')) return null // 多范围不支持

        val dash = spec.indexOf('-')
        if (dash < 0) return null

        val startStr = spec.substring(0, dash).trim()
        val endStr = spec.substring(dash + 1).trim()

        // 后缀请求：bytes=-N
        if (startStr.isEmpty()) {
            val n = endStr.toLongOrNull() ?: return null
            if (n <= 0) return null
            return ByteRange(suffixLength = n, isSuffix = true)
        }

        val start = startStr.toLongOrNull() ?: return null
        if (start < 0) return null

        if (endStr.isEmpty()) {
            return ByteRange(rangeStart = start, rangeEnd = -1)
        }

        val end = endStr.toLongOrNull() ?: return null
        if (end < start) return null
        return ByteRange(rangeStart = start, rangeEnd = end)
    }
}