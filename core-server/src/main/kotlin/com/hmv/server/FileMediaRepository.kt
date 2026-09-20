package com.hmv.server

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

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

    init {
        if (!root.exists()) root.mkdirs()
    }

    override fun list(): List<MediaItem> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .map { f -> MediaItem(id = idFor(f), title = f.name, mimeType = mimeFor(f), size = f.length(), relativePath = f.absolutePath) }
    }

    override fun list(path: String): List<MediaItem> {
        val dir = if (path.isBlank()) root else File(root, path.trimStart('/'))
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .map { f -> MediaItem(id = idFor(f), title = f.name, mimeType = mimeFor(f), size = f.length(), relativePath = f.absolutePath) }
    }

    override fun findById(id: String): MediaItem? = list().firstOrNull { it.id == id }

    override fun openStream(relativePath: String): InputStream = File(relativePath).inputStream()

    // ========== 文件操作 ==========

    override fun uploadFile(fileName: String, path: String, data: ByteArray): FileOperationResult {
        return try {
            val dir = if (path.isBlank()) root else File(root, path.trimStart('/'))
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(data)
            FileOperationResult(true, "uploaded", idFor(file))
        } catch (e: Exception) {
            FileOperationResult(false, e.message ?: "upload failed")
        }
    }

    override fun deleteFile(id: String): FileOperationResult {
        val item = findById(id) ?: return FileOperationResult(false, "file not found")
        return try {
            val file = File(item.relativePath)
            if (file.exists() && file.delete()) {
                FileOperationResult(true, "deleted")
            } else {
                FileOperationResult(false, "delete failed")
            }
        } catch (e: Exception) {
            FileOperationResult(false, e.message ?: "delete failed")
        }
    }

    override fun renameFile(id: String, newName: String): FileOperationResult {
        val item = findById(id) ?: return FileOperationResult(false, "file not found")
        if (newName.isBlank()) return FileOperationResult(false, "name is empty")
        return try {
            val oldFile = File(item.relativePath)
            val newFile = File(oldFile.parent, newName)
            if (newFile.exists()) return FileOperationResult(false, "file already exists")
            if (oldFile.renameTo(newFile)) {
                FileOperationResult(true, "renamed", idFor(newFile))
            } else {
                FileOperationResult(false, "rename failed")
            }
        } catch (e: Exception) {
            FileOperationResult(false, e.message ?: "rename failed")
        }
    }

    override fun createFolder(name: String, parentPath: String): FileOperationResult {
        if (name.isBlank()) return FileOperationResult(false, "name is empty")
        return try {
            val parent = if (parentPath.isBlank()) root else File(root, parentPath.trimStart('/'))
            val folder = File(parent, name)
            if (folder.exists()) return FileOperationResult(false, "folder already exists")
            if (folder.mkdirs()) {
                FileOperationResult(true, "created")
            } else {
                FileOperationResult(false, "create failed")
            }
        } catch (e: Exception) {
            FileOperationResult(false, e.message ?: "create failed")
        }
    }
}
