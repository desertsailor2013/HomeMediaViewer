package com.hmv.server

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    private val usersFile: File by lazy { File(root, ".hmv_users.json") }
    private val metadataDir: File by lazy { File(root, ".hmv_metadata").also { it.mkdirs() } }

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

    // ========== 用户权限管理 ==========

    private fun loadUsers(): MutableList<UserInfo> {
        if (!usersFile.exists()) return mutableListOf()
        return try {
            val json = usersFile.readText()
            parseUsersJson(json)
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveUsers(users: List<UserInfo>) {
        usersFile.writeText(toJson(users))
    }

    private fun parseUsersJson(json: String): MutableList<UserInfo> {
        val users = mutableListOf<UserInfo>()
        if (json.isBlank() || json == "[]") return users
        val pattern = """\{[^}]+\}""".toRegex()
        pattern.findAll(json).forEach { match ->
            val obj = match.value
            val username = extractJsonString(obj, "username")
            val role = extractJsonString(obj, "role")
            val createdAt = extractJsonLong(obj, "createdAt")
            val lastLogin = extractJsonLong(obj, "lastLogin")
            if (username.isNotBlank() && role.isNotBlank()) {
                users.add(UserInfo(username, role, createdAt, lastLogin))
            }
        }
        return users
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun extractJsonLong(json: String, key: String): Long {
        val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun toJson(users: List<UserInfo>): String {
        if (users.isEmpty()) return "[]"
        return "[" + users.joinToString(",") { u ->
            """{"username":"${u.username}","role":"${u.role}","createdAt":${u.createdAt},"lastLogin":${u.lastLogin}}"""
        } + "]"
    }

    private fun hashPassword(password: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash)
    }

    private fun verifyPassword(password: String, stored: String): Boolean {
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = Base64.getDecoder().decode(parts[0])
            val hash = hashPassword(password, salt)
            hash == stored
        } catch (e: Exception) {
            false
        }
    }

    override fun getUsers(): List<UserInfo> = loadUsers()

    override fun addUser(username: String, password: String, role: String): FileOperationResult {
        if (username.isBlank()) return FileOperationResult(false, "username is empty")
        if (password.isBlank()) return FileOperationResult(false, "password is empty")
        if (role !in listOf("admin", "editor", "viewer")) return FileOperationResult(false, "invalid role")
        val users = loadUsers()
        if (users.any { it.username == username }) return FileOperationResult(false, "user already exists")
        val hashedPassword = hashPassword(password)
        users.add(UserInfo(username, role))
        saveUsers(users)
        val pwdFile = File(root, ".hmv_pwd_$username")
        pwdFile.writeText(hashedPassword)
        return FileOperationResult(true, "user added")
    }

    override fun deleteUser(username: String): FileOperationResult {
        val users = loadUsers()
        val removed = users.removeAll { it.username == username }
        if (!removed) return FileOperationResult(false, "user not found")
        saveUsers(users)
        val pwdFile = File(root, ".hmv_pwd_$username")
        if (pwdFile.exists()) pwdFile.delete()
        return FileOperationResult(true, "user deleted")
    }

    override fun updateUserRole(username: String, role: String): FileOperationResult {
        if (role !in listOf("admin", "editor", "viewer")) return FileOperationResult(false, "invalid role")
        val users = loadUsers()
        val index = users.indexOfFirst { it.username == username }
        if (index == -1) return FileOperationResult(false, "user not found")
        users[index] = users[index].copy(role = role)
        saveUsers(users)
        return FileOperationResult(true, "role updated")
    }

    override fun verifyUser(username: String, password: String): UserInfo? {
        val users = loadUsers()
        val user = users.find { it.username == username } ?: return null
        val pwdFile = File(root, ".hmv_pwd_$username")
        if (!pwdFile.exists()) return null
        val storedHash = pwdFile.readText()
        return if (verifyPassword(password, storedHash)) {
            val index = users.indexOfFirst { it.username == username }
            if (index != -1) {
                users[index] = users[index].copy(lastLogin = System.currentTimeMillis())
                saveUsers(users)
            }
            user
        } else {
            null
        }
    }

    override fun checkPermission(username: String, permission: String): Boolean {
        val users = loadUsers()
        val user = users.find { it.username == username } ?: return false
        return when (permission) {
            "read" -> true
            "upload" -> user.role in listOf("admin", "editor")
            "delete" -> user.role in listOf("admin", "editor")
            "manage_users" -> user.role == "admin"
            "manage_device" -> user.role == "admin"
            else -> false
        }
    }

    // ========== 搜索增强 ==========

    override fun searchMedia(query: String, filters: SearchFilters): List<MediaItem> {
        val allMedia = list()
        return allMedia.filter { item ->
            val matchesQuery = query.isBlank() || item.title.contains(query, ignoreCase = true)
            val matchesType = when (filters.type) {
                "video" -> item.mimeType.startsWith("video/")
                "audio" -> item.mimeType.startsWith("audio/")
                "image" -> item.mimeType.startsWith("image/")
                else -> true
            }
            val matchesSize = item.size in filters.minSize..filters.maxSize
            val matchesFolder = filters.folder.isBlank() || item.folderName == filters.folder
            matchesQuery && matchesType && matchesSize && matchesFolder
        }
    }

    // ========== 媒体信息增强 ==========

    override fun getMediaMetadata(mediaId: String): MediaMetadata? {
        val item = findById(mediaId) ?: return null
        val metaFile = File(metadataDir, "$mediaId.json")
        if (metaFile.exists()) {
            return try {
                val json = metaFile.readText()
                parseMetadataJson(json, mediaId)
            } catch (e: Exception) {
                createDefaultMetadata(item)
            }
        }
        return createDefaultMetadata(item)
    }

    private fun createDefaultMetadata(item: MediaItem): MediaMetadata {
        val file = File(item.relativePath)
        val extension = file.extension.lowercase()
        return MediaMetadata(
            mediaId = item.id,
            title = item.title,
            format = extension,
            duration = 0,
            width = 0,
            height = 0,
            bitrate = 0,
            codec = getCodecFromMime(item.mimeType),
            tags = listOfNotNull(
                if (item.mimeType.startsWith("video/")) "视频" else null,
                if (item.mimeType.startsWith("audio/")) "音频" else null,
                if (item.mimeType.startsWith("image/")) "图片" else null
            ),
            addedAt = file.lastModified()
        )
    }

    private fun getCodecFromMime(mimeType: String): String = when {
        mimeType.contains("mp4") -> "H.264"
        mimeType.contains("webm") -> "VP9"
        mimeType.contains("ogg") -> "Opus"
        mimeType.contains("mpeg") -> "MPEG"
        mimeType.contains("mp3") -> "MP3"
        mimeType.contains("aac") -> "AAC"
        mimeType.contains("flac") -> "FLAC"
        mimeType.contains("wav") -> "PCM"
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "JPEG"
        mimeType.contains("png") -> "PNG"
        mimeType.contains("webp") -> "WebP"
        else -> "unknown"
    }

    private fun parseMetadataJson(json: String, mediaId: String): MediaMetadata {
        return MediaMetadata(
            mediaId = mediaId,
            title = extractJsonString(json, "title"),
            artist = extractJsonString(json, "artist"),
            album = extractJsonString(json, "album"),
            duration = extractJsonLong(json, "duration"),
            width = extractJsonInt(json, "width"),
            height = extractJsonInt(json, "height"),
            bitrate = extractJsonInt(json, "bitrate"),
            codec = extractJsonString(json, "codec"),
            format = extractJsonString(json, "format"),
            tags = extractJsonStringList(json, "tags"),
            addedAt = extractJsonLong(json, "addedAt"),
            lastPlayed = extractJsonLong(json, "lastPlayed"),
            playCount = extractJsonInt(json, "playCount")
        )
    }

    private fun extractJsonInt(json: String, key: String): Int {
        val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractJsonStringList(json: String, key: String): List<String> {
        val pattern = """"$key"\s*:\s*\[([^\]]*)\]""".toRegex()
        val match = pattern.find(json) ?: return emptyList()
        val content = match.groupValues[1]
        if (content.isBlank()) return emptyList()
        return content.split(",").map { it.trim().removeSurrounding("\"") }
    }
}
