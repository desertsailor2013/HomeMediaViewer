package com.hmv.app

import com.hmv.server.MediaItem
import android.content.Context
import android.provider.MediaStore
import android.content.ContentUris
import android.net.Uri

/**
 * 通过 MediaStore 扫描本机媒体库，转为可分享的 [MediaItem]。
 */
class MediaScanner(private val context: Context) {

    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "ts", "m4v")
    private val audioExtensions = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "mid", "amr")

    /** 扫描指定集合。mimeType 上游 prefer 由内容决定。 */
    fun scan(collection: CollectionKind = CollectionKind.VIDEO): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val resolver = context.contentResolver

        val (uri, projection) = when (collection) {
            CollectionKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE)
            CollectionKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE)
        }

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(projection[0])
            val nameCol = cursor.getColumnIndexOrThrow(projection[1])
            val sizeCol = cursor.getColumnIndexOrThrow(projection[2])
            val mimeCol = cursor.getColumnIndexOrThrow(projection[3])
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                if (size <= 0L) continue
                val mime = cursor.getString(mimeCol) ?: mimeFromName(name)
                val mediaUri: Uri = if (collection == CollectionKind.VIDEO)
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                else
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val thumbnailUri = buildThumbnailUri(collection, id)
                result += MediaItem(
                    id = "$collection:$id",
                    title = name,
                    mimeType = mime,
                    size = size,
                    relativePath = mediaUri.toString(),
                    thumbnailUri = thumbnailUri
                )
            }
        }
        return result
    }

    private fun buildThumbnailUri(collection: CollectionKind, dbId: Long): String? {
        return when (collection) {
            CollectionKind.VIDEO -> {
                val baseUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, dbId)
                baseUri.toString()
            }
            CollectionKind.AUDIO -> {
                val baseUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, dbId)
                val artUri = Uri.withAppendedPath(baseUri, "albumart")
                artUri.toString()
            }
        }
    }

    private fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in videoExtensions -> "video/*"
            ext in audioExtensions -> "audio/*"
            else -> "application/octet-stream"
        }
    }

    enum class CollectionKind { VIDEO, AUDIO }
}