package com.hmv.server

/**
 * 一条可被远程点播的媒体条目。
 *
 * @param id 服务内唯一标识
 * @param title 展示标题（文件名）
 * @param mimeType MIME 类型，如 video/mp4
 * @param size 字节大小，用于 Range 校验
 * @param relativePath 服务端可打开的相对路径，具体语义由 [MediaRepository] 决定
 */
data class MediaItem(
    val id: String,
    val title: String,
    val mimeType: String,
    val size: Long,
    val relativePath: String
)

/**
 * 媒体的数据源抽象。
 *
 * 将「HTTP+Range 服务」与「具体文件系统/MediaStore」解耦：
 * - 单元测试可用内存/临时文件实现
 * - Android 端用 MediaStore 或普通文件实现
 */
interface MediaRepository {
    /** 当前可分享的全部媒体列表。 */
    fun list(): List<MediaItem>

    /** 按 id 查找媒体，不存在返回 null。 */
    fun findById(id: String): MediaItem?

    /**
     * 打开媒体流。
     *
     * @param relativePath 来自 [MediaItem.relativePath]
     * @return 可随机访问的流，调用方负责 close
     */
    fun openStream(relativePath: String): java.io.InputStream

    /**
     * 打开可随机访问的源，供 Range 请求 seek 定位。
     *
     * 返回 null 表示该数据源不支持 seek（服务端将回退为不支持 Range 的整流方式）。
     * 默认基于文件路径用 [java.io.RandomAccessFile] 打开。
     */
    fun openForRange(relativePath: String): RangeReadable? =
        try {
            FileRangeReadable(java.io.RandomAccessFile(relativePath, "r"))
        } catch (e: Exception) {
            null
        }
}
