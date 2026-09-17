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
        private const val STATUS_PARTIAL = "206 Partial Content"
        private const val STATUS_RANGE_416 = "416 Range Not Satisfiable"
        private const val STATUS_NOT_FOUND = "404 Not Found"
        private const val STATUS_BAD_REQUEST = "400 Bad Request"
    }

    private val serverSocket = ServerSocket(port)
    private val started = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
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
        val headers: Map<String, String>
    )

    private fun handleClient(socket: Socket) {
        socket.use {
            it.tcpNoDelay = true
            try {
                val request = parseRequest(it.getInputStream())
                val out = it.getOutputStream()
                when {
                    request == null -> writeStatus(out, STATUS_BAD_REQUEST)
                    request.method != "GET" && request.method != "HEAD" -> writeStatus(out, STATUS_BAD_REQUEST)
                    request.path == "/media" || request.path == "/media/" -> handleList(out, request.method)
                    request.path.matches(Regex("^/media/[^/]+/thumbnail$")) -> handleThumbnail(out, request)
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
        val path = if (rawPath.contains('%')) decodePath(rawPath) else rawPath

        val headers = LinkedHashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        return HttpRequest(method, path, headers)
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

    private fun handleList(out: OutputStream, method: String) {
        val items = repository.list()
        val json = buildJsonList(items)
        val body = json.toByteArray(Charsets.UTF_8)
        if (method == "HEAD") {
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
            val bytes = input.readBytes()
            val mime = if (bytes.size >= 4) {
                when {
                    bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                    bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
                    else -> MIME_OCTET
                }
            } else MIME_OCTET
            if (request.method == "HEAD") {
                writeHead(out, STATUS_OK, mime, bytes.size.toLong(), emptyMap())
            } else {
                writeResponse(out, STATUS_OK, mime, bytes.size.toLong(), bytes)
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