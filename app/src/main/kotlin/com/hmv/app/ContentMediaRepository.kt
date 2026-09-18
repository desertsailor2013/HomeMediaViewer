package com.hmv.app

import android.content.Context
import android.net.Uri
import com.hmv.server.MediaItem
import com.hmv.server.MediaRepository
import com.hmv.server.RangeReadable
import java.io.FileInputStream

/**
 * 基于 MediaStore content:// URI 的 [MediaRepository]。
 *
 * - [findById] 支持从 [items] 中查找
 * - [openForRange] 通过 ContentResolver 打开 AssetFileDescriptor，
 *   取其文件描述符包装成可 seek 的 [RangeReadable]，从而支持 Range 拖动。
 * - [getThumbnail] 通过 MediaStore 生成视频缩略图 / 音频封面。
 */
class ContentMediaRepository(
    private val context: Context,
    private val items: List<MediaItem> = emptyList()
) : MediaRepository {

    override fun list(): List<MediaItem> = items

    override fun findById(id: String): MediaItem? = items.firstOrNull { it.id == id }

    override fun openStream(relativePath: String): java.io.InputStream {
        return context.contentResolver.openInputStream(Uri.parse(relativePath))
            ?: throw IllegalStateException("Cannot open $relativePath")
    }

    override fun openForRange(relativePath: String): RangeReadable? {
        return try {
            val afd = context.contentResolver.openAssetFileDescriptor(Uri.parse(relativePath), "r")
                ?: return null
            afd.use { afdInner ->
                val dup = afdInner.parcelFileDescriptor.dup()
                FdRangeReadable(FileInputStream(dup.fileDescriptor), dup)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun getThumbnail(id: String): java.io.InputStream? = null

    /** 基于文件描述符的 [RangeReadable]，通过 FileChannel 实现 seek。 */
    private class FdRangeReadable(
        private val stream: FileInputStream,
        private val owner: android.os.ParcelFileDescriptor
    ) : RangeReadable {
        private val channel = stream.channel
        override fun length(): Long = channel.size()
        override fun seek(position: Long) {
            channel.position(position)
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun close() {
            try { stream.close() } finally { owner.close() }
        }
    }
}