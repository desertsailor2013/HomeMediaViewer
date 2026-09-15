package com.hmv.server

import java.io.RandomAccessFile

/**
 * 可随机访问的只读源，供 Range 请求 seek 定位。
 */
interface RangeReadable : AutoCloseable {
    /** 总字节长度。 */
    fun length(): Long

    /** 定位到指定字节位置（含）。 */
    fun seek(position: Long)

    /** 读取最多 [len] 字节到 [b]，返回实际读取数或 -1 表示 EOF。 */
    fun read(b: ByteArray, off: Int, len: Int): Int

    override fun close()
}

/** 基于 [RandomAccessFile] 的实现，适用于普通文件路径。 */
class FileRangeReadable(private val raf: RandomAccessFile) : RangeReadable {
    override fun length(): Long = raf.length()
    override fun seek(position: Long) = raf.seek(position)
    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
    override fun close() = raf.close()
}