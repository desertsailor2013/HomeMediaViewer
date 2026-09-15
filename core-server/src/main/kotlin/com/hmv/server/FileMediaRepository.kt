package com.hmv.server

import java.io.File
import java.io.InputStream

/**
 * 基于普通文件系统的 [MediaRepository] 实现。
 *
 * @param root 扫描根目录；相对路径即相对此目录
 * @param idFor 由文件生成稳定 id（默认用相对路径）
 * @param mimeFor 由文件推断 MIME（可传 null/自定义）
 */
class FileMediaRepository(
    private val root: File,
    private val idFor: (File) -> String = { f -> f.name },
    private val mimeFor: (File) -> String = { "" }
) : MediaRepository {

    override fun list(): List<MediaItem> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .map { f -> MediaItem(id = idFor(f), title = f.name, mimeType = mimeFor(f), size = f.length(), relativePath = f.absolutePath) }
    }

    override fun findById(id: String): MediaItem? = list().firstOrNull { it.id == id }

    override fun openStream(relativePath: String): InputStream = File(relativePath).inputStream()
}