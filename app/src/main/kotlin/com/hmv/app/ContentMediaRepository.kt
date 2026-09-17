package com.hmv.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.hmv.server.MediaItem
import com.hmv.server.MediaRepository
import com.hmv.server.RangeReadable
import java.io.FileInputStream
import java.io.InputStream

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
            val dup = afd.parcelFileDescriptor.dup()
            FdRangeReadable(FileInputStream(dup.fileDescriptor), dup)
        } catch (e: Exception) {
            null
        }
    }

    override fun getThumbnail(id: String): InputStream? {
        val item = findById(id) ?: return null
        return try {
            if (item.mimeType.startsWith("video")) {
                getVideoThumbnail(item)
            } else if (item.mimeType.startsWith("audio")) {
                getAudioAlbumArt(item)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getVideoThumbnail(item: MediaItem): InputStream? {
        val contentUri = Uri.parse(item.relativePath)
        val dbId = ContentUris.parseId(contentUri)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumbUri = MediaStore.Video.Thumbnails.getContentUri(
                MediaStore.VOLUME_EXTERNAL, dbId
            )
            context.contentResolver.openInputStream(thumbUri)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, dbId,
                MediaStore.Video.Thumbnails.MINI_KIND, null
            )?.let { bitmap ->
                val bos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                bitmap.recycle()
                java.io.ByteArrayInputStream(bos.toByteArray())
            }
        }
    }

    private fun getAudioAlbumArt(item: MediaItem): InputStream? {
        val contentUri = Uri.parse(item.relativePath)
        val dbId = ContentUris.parseId(contentUri)
        val artUri = Uri.withAppendedPath(contentUri, "albumart")
        return try {
            context.contentResolver.openInputStream(artUri)
        } catch (e: Exception) {
            null
        }
    }

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