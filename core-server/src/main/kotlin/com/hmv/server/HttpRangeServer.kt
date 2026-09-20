package com.hmv.server

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 内嵌的 HTTP+Range 流媒体服务端。
 *
 * 为保持轻量、不依赖第三方 HTTP 库，直接基于 ServerSocket 实现。
 * 支持：
 * - GET /media                    → JSON 媒体列表
 * - GET /media/{id}               → 整文件字节流
 * - GET /media/{id} + Range 头      → 字节范围（支持拖动）
 *
 * @param repository 媒体数据源
 * @param port 监听端口（0 表示自动分配）
 * @param bufferSize 单次读写缓冲区大小
 */
class HttpRangeServer(
    private val repository: MediaRepository,
    port: Int = 0,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) : AutoCloseable {

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val MIME_OCTET = "application/octet-stream"
        private const val STATUS_OK = "200 OK"
        private const val STATUS_CREATED = "201 Created"
        private const val STATUS_NO_CONTENT = "204 No Content"
        private const val STATUS_PARTIAL = "206 Partial Content"
        private const val STATUS_RANGE_416 = "416 Range Not Satisfiable"
        private const val STATUS_NOT_FOUND = "404 Not Found"
        private const val STATUS_BAD_REQUEST = "400 Bad Request"
        private const val STATUS_UNAUTHORIZED = "401 Unauthorized"
        private val THUMBNAIL_PATTERN = Regex("^/media/[^/]+/thumbnail$")
        private val RENAME_PATTERN = Regex("^/media/[^/]+/rename$")
        private val SCAN_PATH_PATTERN = Regex("^/scanpaths/.*$")
        private val PROXY_PATTERN = Regex("^/proxy/.*$")
    }

    private val serverSocket = ServerSocket(port)
    private val started = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    ) { r ->
        Thread(r, "hmv-server").apply { isDaemon = true }
    }
    private val thread = Thread {
        while (!serverSocket.isClosed) {
            try {
                val client = serverSocket.accept()
                executor.execute { handleClient(client) }
            } catch (e: IOException) {
                if (serverSocket.isClosed) break
            }
        }
    }.apply { isDaemon = true }

    /** 实际监听端口（若构造时传 0，可由此取得真实端口）。 */
    val port: Int get() = serverSocket.localPort

    fun start() {
        if (started.compareAndSet(false, true)) thread.start()
    }

    override fun close() {
        started.set(false)
        try { serverSocket.close() } catch (_: IOException) {}
        executor.shutdownNow()
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String = "",
        val rawBody: ByteArray? = null,
        val queryParams: Map<String, String> = emptyMap()
    )

    private fun handleClient(socket: Socket) {
        socket.use {
            it.tcpNoDelay = true
            try {
                val request = parseRequest(it.getInputStream())
                val out = it.getOutputStream()
                when {
                    request == null -> writeStatus(out, STATUS_BAD_REQUEST)
                    request.method != "GET" && request.method != "HEAD" &&
                        request.method != "POST" && request.method != "DELETE" -> writeStatus(out, STATUS_BAD_REQUEST)

                    // POST 端点
                    request.method == "POST" && request.path == "/play" -> handlePlayCommand(out, request)
                    request.method == "POST" && request.path == "/upload" -> handleUpload(out, request)
                    request.method == "POST" && request.path == "/folder" -> handleCreateFolder(out, request)
                    request.method == "POST" && request.path.matches(RENAME_PATTERN) -> handleRename(out, request)
                    request.method == "POST" && request.path == "/scanpaths" -> handleAddScanPath(out, request)
                    request.method == "POST" && request.path == "/scanpaths/rescan" -> handleRescanAll(out, request)
                    request.method == "POST" && request.path == "/admin/password" -> handleSetAdminPassword(out, request)
                    request.method == "POST" && request.path == "/admin/verify" -> handleVerifyAdminPassword(out, request)
                    request.method == "POST" && request.path == "/proxy/enable" -> handleEnableProxy(out, request)
                    request.method == "POST" && request.path == "/proxy/disable" -> handleDisableProxy(out, request)
                    request.method == "POST" && request.path.matches(PROXY_PATTERN) && request.path.endsWith("/operation") -> handleProxyOperation(out, request)
                    request.method == "POST" && request.path == "/device/info" -> handleSetDeviceInfo(out, request)

                    // DELETE 端点
                    request.method == "DELETE" && request.path.startsWith("/media/") -> handleDelete(out, request)
                    request.method == "DELETE" && request.path.matches(SCAN_PATH_PATTERN) -> handleRemoveScanPath(out, request)

                    // GET 端点
                    request.path == "/media" || request.path == "/media/" -> handleList(out, request)
                    request.path == "/scanpaths" -> handleGetScanPaths(out, request)
                    request.path == "/admin/password" -> handleGetAdminPassword(out, request)
                    request.path == "/proxy/status" -> handleGetProxyStatus(out, request)
                    request.path == "/device/info" -> handleGetDeviceInfo(out, request)
                    request.path.matches(THUMBNAIL_PATTERN) -> handleThumbnail(out, request)
                    request.path.startsWith("/media/") -> handleStream(out, bufferSize, request)
                    else -> writeStatus(out, STATUS_NOT_FOUND)
                }
            } catch (e: IOException) {
                // 客户端中断或超时，忽略
            }
        }
    }

    private fun parseRequest(stream: java.io.InputStream): HttpRequest? {
        // 逐字节读取直到头部结束（\r\n\r\n 或 \n\n），避免 readNBytes 阻塞等待 EOF
        val headBytes = ByteArrayOutputStream()
        val sb = StringBuilder()
        var done = false
        while (!done) {
            val b = stream.read()
            if (b < 0) break
            headBytes.write(b)
            sb.append(b.toChar())
            if (headBytes.size() >= 4 && (sb.endsWith("\r\n\r\n") || sb.endsWith("\n\n"))) done = true
            if (headBytes.size() > 64 * 1024) return null // 防止恶意超大头部
        }
        if (headBytes.size() == 0) return null
        val head = headBytes.toString("ISO-8859-1")

        val headerEnd = indexOfHeaderEnd(head)
        if (headerEnd < 0) return null
        val headSection = head.substring(0, headerEnd)
        val lines = headSection.split("\r\n", "\n")

        val requestLine = lines.firstOrNull()?.trim()?.split(" ") ?: return null
        if (requestLine.size < 3) return null
        val method = requestLine[0]
        val rawPath = requestLine[1]

        // 分离路径和查询参数
        val questionMark = rawPath.indexOf('?')
        val path = if (questionMark >= 0) {
            val p = rawPath.substring(0, questionMark)
            if (p.contains('%')) decodePath(p) else p
        } else {
            if (rawPath.contains('%')) decodePath(rawPath) else rawPath
        }
        val queryParams = if (questionMark >= 0) {
            parseQueryParams(rawPath.substring(questionMark + 1))
        } else {
            emptyMap()
        }

        val headers = LinkedHashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }

        // 读取请求体
        var body = ""
        var rawBody: ByteArray? = null
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength > 0) {
            val isMultipart = headers["content-type"]?.contains("multipart/form-data") == true
            if (isMultipart) {
                // multipart 上传：读取原始字节（最大 500MB）
                if (contentLength <= 500 * 1024 * 1024) {
                    rawBody = stream.readNBytes(contentLength)
                }
            } else if (contentLength <= 64 * 1024) {
                // 普通 JSON body
                val bodyBytes = stream.readNBytes(contentLength)
                body = bodyBytes.toString(Charsets.UTF_8)
            }
        }

        return HttpRequest(method, path, headers, body, rawBody, queryParams)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = decodePath(pair.substring(0, eq))
                val value = decodePath(pair.substring(eq + 1))
                params[key] = value
            }
        }
        return params
    }

    private fun indexOfHeaderEnd(text: String): Int {
        val a = text.indexOf("\r\n\r\n")
        if (a >= 0) return a + 4
        val b = text.indexOf("\n\n")
        if (b >= 0) return b + 2
        return -1
    }

    private fun decodePath(raw: String): String = try {
        URLDecoder.decode(raw, "UTF-8")
    } catch (e: Exception) {
        raw
    }

    // ---------- 列表 ----------

    private fun handleList(out: OutputStream, request: HttpRequest) {
        val path = request.queryParams["path"]
        val items = if (path != null) repository.list(path) else repository.list()
        val json = buildJsonList(items)
        val body = json.toByteArray(Charsets.UTF_8)
        if (request.method == "HEAD") {
            writeHead(out, STATUS_OK, MIME_JSON, body.size.toLong(), emptyMap())
        } else {
            writeResponse(out, STATUS_OK, MIME_JSON, body.size.toLong(), body)
        }
    }

    private fun buildJsonList(items: List<MediaItem>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append('{')
                .append("\"id\":\"").append(escapeJson(m.id)).append("\",")
                .append("\"title\":\"").append(escapeJson(m.title)).append("\",")
                .append("\"mimeType\":\"").append(escapeJson(m.mimeType)).append("\",")
                .append("\"size\":").append(m.size).append(',')
                .append("\"path\":\"").append(escapeJson(m.relativePath)).append("\"")
            if (m.thumbnailUri != null) {
                sb.append(",\"thumbnail\":\"").append(escapeJson(m.thumbnailUri)).append("\"")
            }
            sb.append('}')
        }
        return sb.append(']').toString()
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    // ---------- 投屏控制 ----------

    /** 投屏指令回调，由上层设置以处理投屏请求 */
    var onPlayCommand: ((mediaId: String, title: String, position: Long) -> Unit)? = null

    private fun handlePlayCommand(out: OutputStream, request: HttpRequest) {
        try {
            val json = request.body
            if (json.isEmpty()) {
                writeStatus(out, STATUS_BAD_REQUEST)
                return
            }

            // 简单解析 JSON
            val mediaId = extractJsonString(json, "mediaId") ?: ""
            val title = extractJsonString(json, "title") ?: ""
            val position = extractJsonLong(json, "position")

            if (mediaId.isEmpty()) {
                writeStatus(out, STATUS_BAD_REQUEST)
                return
            }

            onPlayCommand?.invoke(mediaId, title, position)

            val response = """{"status":"ok"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } catch (e: Exception) {
            writeStatus(out, STATUS_BAD_REQUEST)
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonLong(json: String, key: String): Long {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    // ---------- 文件上传 ----------

    private fun handleUpload(out: OutputStream, request: HttpRequest) {
        val rawBody = request.rawBody
        if (rawBody == null || rawBody.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }

        val contentType = request.headers["content-type"] ?: ""
        val boundary = extractMultipartBoundary(contentType)
        if (boundary == null) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }

        try {
            val multipart = parseMultipart(rawBody, boundary)
            val fileName = multipart["filename"] ?: multipart["file"]?.let { extractFileName(it) } ?: "upload_${System.currentTimeMillis()}"
            val path = multipart["path"] ?: "/"
            val fileData = multipart["fileData"]?.toByteArray(Charsets.ISO_8859_1)

            if (fileData == null) {
                writeStatus(out, STATUS_BAD_REQUEST)
                return
            }

            val result = repository.uploadFile(fileName, path, fileData)
            if (result.success) {
                val response = """{"status":"ok","id":"${escapeJson(result.id ?: "")}"}"""
                writeResponse(out, STATUS_CREATED, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
            } else {
                writeStatus(out, STATUS_BAD_REQUEST)
            }
        } catch (e: Exception) {
            writeStatus(out, STATUS_BAD_REQUEST)
        }
    }

    private fun extractMultipartBoundary(contentType: String): String? {
        val idx = contentType.indexOf("boundary=")
        if (idx < 0) return null
        return contentType.substring(idx + 9).trim().removeSurrounding("\"")
    }

    private fun parseMultipart(body: ByteArray, boundary: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val boundaryBytes = ("--$boundary").toByteArray(Charsets.ISO_8859_1)
        val endBytes = ("--$boundary--").toByteArray(Charsets.ISO_8859_1)

        var pos = 0
        while (pos < body.size) {
            // 查找 boundary
            val start = indexOf(body, boundaryBytes, pos)
            if (start < 0) break

            val afterBoundary = start + boundaryBytes.size
            if (afterBoundary + 2 > body.size) break

            // 检查是否是结束 boundary
            if (body[afterBoundary] == '-'.toInt().toByte() && body[afterBoundary + 1] == '-'.toInt().toByte()) break

            // 跳过 \r\n
            val headerStart = afterBoundary + 2
            if (headerStart >= body.size) break

            // 查找头部结束
            val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(Charsets.ISO_8859_1), headerStart)
            if (headerEnd < 0) break

            val headerBytes = body.copyOfRange(headerStart, headerEnd)
            val header = headerBytes.toString(Charsets.ISO_8859_1)

            // 解析 Content-Disposition
            val name = extractDispositionName(header)
            val filename = extractDispositionFilename(header)

            // 数据开始
            val dataStart = headerEnd + 4
            // 查找下一个 boundary
            val dataEnd = indexOf(body, "\r\n--$boundary".toByteArray(Charsets.ISO_8859_1), dataStart)
            if (dataEnd < 0) break

            if (filename != null) {
                // 文件字段：保存文件名和数据
                result["filename"] = filename
                result["fileData"] = body.copyOfRange(dataStart, dataEnd).toString(Charsets.ISO_8859_1)
            } else if (name != null) {
                // 普通字段
                result[name] = body.copyOfRange(dataStart, dataEnd).toString(Charsets.UTF_8)
            }

            pos = dataEnd
        }
        return result
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, start: Int): Int {
        if (pattern.isEmpty()) return start
        val max = data.size - pattern.size
        for (i in start..max) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun extractDispositionName(header: String): String? {
        val regex = Regex("""name="([^"]+)"""")
        return regex.find(header)?.groupValues?.get(1)
    }

    private fun extractDispositionFilename(header: String): String? {
        val regex = Regex("""filename="([^"]+)"""")
        return regex.find(header)?.groupValues?.get(1)
    }

    private fun extractFileName(contentDisposition: String): String? {
        val regex = Regex("""filename="([^"]+)"""")
        return regex.find(contentDisposition)?.groupValues?.get(1)
    }

    // ---------- 删除文件 ----------

    private fun handleDelete(out: OutputStream, request: HttpRequest) {
        val id = request.path.removePrefix("/media/")
        if (id.isEmpty()) {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }
        val result = repository.deleteFile(id)
        if (result.success) {
            writeResponse(out, STATUS_OK, MIME_JSON, """{"status":"ok"}""".toByteArray().size.toLong(), """{"status":"ok"}""".toByteArray())
        } else {
            writeStatus(out, STATUS_NOT_FOUND)
        }
    }

    // ---------- 重命名文件 ----------

    private fun handleRename(out: OutputStream, request: HttpRequest) {
        val id = request.path.removeSuffix("/rename").removePrefix("/media/")
        if (id.isEmpty()) {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }
        val newName = extractJsonString(request.body, "name") ?: ""
        if (newName.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.renameFile(id, newName)
        if (result.success) {
            val response = """{"status":"ok","id":"${escapeJson(result.id ?: "")}"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            writeStatus(out, STATUS_BAD_REQUEST)
        }
    }

    // ---------- 创建文件夹 ----------

    private fun handleCreateFolder(out: OutputStream, request: HttpRequest) {
        val name = extractJsonString(request.body, "name") ?: ""
        val path = extractJsonString(request.body, "path") ?: "/"
        if (name.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.createFolder(name, path)
        if (result.success) {
            writeResponse(out, STATUS_CREATED, MIME_JSON, """{"status":"ok"}""".toByteArray().size.toLong(), """{"status":"ok"}""".toByteArray())
        } else {
            writeStatus(out, STATUS_BAD_REQUEST)
        }
    }

    // ---------- 扫描路径管理 ----------

    private fun handleGetScanPaths(out: OutputStream, request: HttpRequest) {
        val paths = repository.getScanPaths()
        val sb = StringBuilder("[")
        paths.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('"').append(escapeJson(p)).append('"')
        }
        sb.append(']')
        val response = sb.toString()
        writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
    }

    private fun handleAddScanPath(out: OutputStream, request: HttpRequest) {
        val path = extractJsonString(request.body, "path") ?: ""
        if (path.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.addScanPath(path)
        if (result.success) {
            val response = """{"status":"ok","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_CREATED, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    private fun handleRemoveScanPath(out: OutputStream, request: HttpRequest) {
        val path = request.path.removePrefix("/scanpaths/")
        if (path.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val decodedPath = if (path.contains('%')) decodePath(path) else path
        val result = repository.removeScanPath(decodedPath)
        if (result.success) {
            val response = """{"status":"ok"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            writeStatus(out, STATUS_NOT_FOUND)
        }
    }

    private fun handleRescanAll(out: OutputStream, request: HttpRequest) {
        val result = repository.rescanAll()
        if (result.success) {
            val response = """{"status":"ok","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    // ---------- 管理密码 ----------

    private fun handleGetAdminPassword(out: OutputStream, request: HttpRequest) {
        val password = repository.getAdminPassword()
        val hasPassword = password != null && password.isNotEmpty()
        val response = """{"hasPassword":$hasPassword}"""
        writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
    }

    private fun handleSetAdminPassword(out: OutputStream, request: HttpRequest) {
        val password = extractJsonString(request.body, "password") ?: ""
        if (password.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.setAdminPassword(password)
        if (result.success) {
            val response = """{"status":"ok","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    private fun handleVerifyAdminPassword(out: OutputStream, request: HttpRequest) {
        val password = extractJsonString(request.body, "password") ?: ""
        if (password.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val valid = repository.verifyAdminPassword(password)
        val response = """{"valid":$valid}"""
        writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
    }

    // ---------- 代理通道 ----------

    private fun handleGetProxyStatus(out: OutputStream, request: HttpRequest) {
        val status = repository.getProxyStatus()
        val hasPassword = status.password != null && status.password.isNotEmpty()
        val response = """{"enabled":${status.enabled},"hasPassword":$hasPassword}"""
        writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
    }

    private fun handleEnableProxy(out: OutputStream, request: HttpRequest) {
        val password = extractJsonString(request.body, "password") ?: ""
        if (password.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.enableProxy(password)
        if (result.success) {
            val response = """{"status":"ok","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    private fun handleDisableProxy(out: OutputStream, request: HttpRequest) {
        val result = repository.disableProxy()
        if (result.success) {
            val response = """{"status":"ok"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    private fun handleProxyOperation(out: OutputStream, request: HttpRequest) {
        // 从 URL 提取设备地址 /proxy/{deviceAddress}/operation
        val pathAfterProxy = request.path.removePrefix("/proxy/")
        val deviceAddress = pathAfterProxy.removeSuffix("/operation")
        
        if (deviceAddress.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }

        val password = extractJsonString(request.body, "password") ?: ""
        val operation = extractJsonString(request.body, "operation") ?: ""
        val paramsJson = extractJsonObject(request.body, "params") ?: "{}"

        if (password.isEmpty() || operation.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }

        // 验证密码
        if (!repository.verifyAdminPassword(password)) {
            val response = """{"status":"error","message":"Invalid password"}"""
            writeResponse(out, STATUS_UNAUTHORIZED, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
            return
        }

        // 解析 params
        val params = parseJsonMap(paramsJson)

        // 执行代理操作
        val result = repository.proxyOperation(password, operation, params)
        if (result.success) {
            val response = """{"status":"ok","message":"${escapeJson(result.message)}","id":"${escapeJson(result.id ?: "")}"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    private fun parseJsonMap(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        regex.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun extractJsonObject(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\\{([^}]*)\\}"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.let { "{$it}" }
    }

    // ---------- 设备信息 ----------

    private fun handleGetDeviceInfo(out: OutputStream, request: HttpRequest) {
        val deviceType = repository.getDeviceType()
        val deviceName = repository.getDeviceName()
        val response = """{"deviceType":"${escapeJson(deviceType)}","deviceName":"${escapeJson(deviceName)}"}"""
        writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
    }

    private fun handleSetDeviceInfo(out: OutputStream, request: HttpRequest) {
        val deviceType = extractJsonString(request.body, "deviceType") ?: ""
        val deviceName = extractJsonString(request.body, "deviceName") ?: ""
        if (deviceType.isEmpty()) {
            writeStatus(out, STATUS_BAD_REQUEST)
            return
        }
        val result = repository.setDeviceInfo(deviceType, deviceName)
        if (result.success) {
            val response = """{"status":"ok"}"""
            writeResponse(out, STATUS_OK, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        } else {
            val response = """{"status":"error","message":"${escapeJson(result.message)}"}"""
            writeResponse(out, STATUS_BAD_REQUEST, MIME_JSON, response.toByteArray().size.toLong(), response.toByteArray())
        }
    }

    // ---------- 缩略图 ----------

    private fun handleThumbnail(out: OutputStream, request: HttpRequest) {
        val id = request.path.removePrefix("/media/").removeSuffix("/thumbnail")
        if (id.isEmpty()) {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }
        val stream = repository.getThumbnail(id)
        if (stream == null) {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }
        stream.use { input ->
            val first2 = input.readNBytes(2)
            val mime = when {
                first2.size >= 2 && first2[0] == 0xFF.toByte() && first2[1] == 0xD8.toByte() -> "image/jpeg"
                first2.size >= 2 && first2[0] == 0x89.toByte() && first2[1] == 0x50.toByte() -> "image/png"
                else -> MIME_OCTET
            }
            val contentLength = input.available().toLong() + first2.size
            if (request.method == "HEAD") {
                writeHead(out, STATUS_OK, mime, contentLength, emptyMap())
            } else {
                writeHead(out, STATUS_OK, mime, contentLength, emptyMap())
                out.write(first2)
                input.copyTo(out, bufferSize)
                out.flush()
            }
        }
    }

    // ---------- 媒体流 ----------

    private fun handleStream(out: OutputStream, bufferSize: Int, request: HttpRequest) {
        val id = request.path.removePrefix("/media/")
        if (id.isEmpty() || id.contains('/')) {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }
        val item = repository.findById(id) ?: run {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }

        val raf: RangeReadable = repository.openForRange(item.relativePath) ?: run {
            writeStatus(out, STATUS_NOT_FOUND)
            return
        }

        try {
            val fileSize = raf.length()
            val range = RangeParser.parse(request.headers["range"])

            // 解析出实际 start/end/status
            val start: Long
            val end: Long
            val status: String
            when {
                range == null -> { start = 0; end = fileSize - 1; status = STATUS_OK }
                range.isSuffix -> {
                    val n = range.suffixLength
                    start = (fileSize - n).coerceAtLeast(0)
                    end = fileSize - 1
                    status = STATUS_PARTIAL
                }
                else -> {
                    var s = range.rangeStart
                    var e = range.rangeEnd
                    if (e < 0) e = fileSize - 1
                    if (s >= fileSize || s > e) {
                        writeRangeNotSatisfiable(out, fileSize)
                        return
                    }
                    start = s
                    end = e
                    status = STATUS_PARTIAL
                }
            }

            val length = (end - start).coerceAtLeast(0) + 1
            val headers = mapOf("Content-Range" to contentRange(start, end, fileSize))

            if (request.method == "HEAD") {
                writeResponse(out, status, mimeOf(item.mimeType), length, null, headers, headOnly = true)
                return
            }

            writeResponse(out, status, mimeOf(item.mimeType), length, null, headers) {
                var pos = start
                raf.seek(pos)
                val buf = ByteArray(bufferSize)
                var remaining = length
                while (remaining > 0) {
                    val toRead = minOf(bufferSize.toLong(), remaining).toInt()
                    val n = raf.read(buf, 0, toRead)
                    if (n < 0) break
                    it.write(buf, 0, n)
                    remaining -= n
                }
            }
        } finally {
            raf.close()
        }
    }

    private fun contentRange(start: Long, end: Long, total: Long) = "bytes $start-$end/$total"

    private fun mimeOf(mime: String): String = if (mime.isEmpty()) MIME_OCTET else mime

    // ---------- 响应工具 ----------

    private fun writeRangeNotSatisfiable(out: OutputStream, fileSize: Long) {
        writeHead(out, STATUS_RANGE_416, mapOf("Content-Range" to "bytes */$fileSize"))
    }

    private fun writeStatus(out: OutputStream, status: String) = writeHead(out, status, emptyMap())

    private fun writeHead(out: OutputStream, status: String, extra: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        for ((k, v) in extra) sb.append("$k: $v\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeHead(out: OutputStream, status: String, contentType: String, contentLength: Long, extra: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: $contentLength\r\n")
        for ((k, v) in extra) sb.append("$k: $v\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeResponse(
        out: OutputStream,
        status: String,
        contentType: String,
        contentLength: Long,
        body: ByteArray?,
        extra: Map<String, String> = emptyMap(),
        headOnly: Boolean = false,
        bodyWriter: ((OutputStream) -> Unit)? = null
    ) {
        writeHead(out, status, contentType, contentLength, extra)
        if (headOnly) return
        if (body != null) out.write(body) else bodyWriter?.invoke(out)
        out.flush()
    }
}