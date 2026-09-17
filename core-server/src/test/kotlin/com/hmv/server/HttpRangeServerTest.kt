package com.hmv.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 端到端测试：启动真实服务端，用 HTTP 客户端验证 Range 行为。
 */
class HttpRangeServerTest {

    private class TempRepo(files: Map<String, File>) : MediaRepository {
        private val items = files.map { (id, f) ->
            MediaItem(id = id, title = f.name, mimeType = "video/mp4", size = f.length(), relativePath = f.absolutePath)
        }
        override fun list() = items
        override fun findById(id: String) = items.firstOrNull { it.id == id }
        override fun openStream(relativePath: String): java.io.InputStream {
            return File(relativePath).inputStream()
        }
    }

    private class ThumbRepo(
        files: Map<String, File>,
        private val thumbnails: Map<String, ByteArray> = emptyMap()
    ) : MediaRepository {
        private val items = files.map { (id, f) ->
            MediaItem(id = id, title = f.name, mimeType = "video/mp4", size = f.length(), relativePath = f.absolutePath)
        }
        override fun list() = items
        override fun findById(id: String) = items.firstOrNull { it.id == id }
        override fun openStream(relativePath: String): java.io.InputStream = File(relativePath).inputStream()
        override fun getThumbnail(id: String): java.io.InputStream? {
            val data = thumbnails[id] ?: return null
            return ByteArrayInputStream(data)
        }
    }

    private fun withServer(repo: MediaRepository, block: (Int) -> Unit) {
        val server = HttpRangeServer(repo)
        server.start()
        try {
            block(server.port)
        } finally {
            server.close()
        }
    }

    @Test
    fun `full file download`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(1000) { it.toByte() })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes() }
            assertEquals(200, code)
            assertEquals(1000, body.size)
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
        }
    }

    @Test
    fun `range 100-199 returns 206 with correct slice`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(500) { (it % 251).toByte() })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=100-199")
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes() }
            assertEquals(206, code)
            assertEquals(100, body.size)
            assertEquals("bytes 100-199/500", conn.getHeaderField("Content-Range"))
            assertEquals(f.readBytes().copyOfRange(100, 200).contentEquals(body), true)
        }
    }

    @Test
    fun `open range bytes=400 returns rest of file`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(1000) { 7 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=400-")
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes() }
            assertEquals(206, code)
            assertEquals(600, body.size)
            assertEquals("bytes 400-999/1000", conn.getHeaderField("Content-Range"))
        }
    }

    @Test
    fun `suffix range bytes=-100 returns last 100 bytes`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(1000) { 1 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=-100")
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes() }
            assertEquals(206, code)
            assertEquals(100, body.size)
            assertEquals("bytes 900-999/1000", conn.getHeaderField("Content-Range"))
        }
    }

    @Test
    fun `range beyond end returns 416`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(100) { 1 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=200-300")
            assertEquals(416, conn.responseCode)
            assertEquals("bytes */100", conn.getHeaderField("Content-Range"))
        }
    }

    @Test
    fun `unknown id returns 404`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(10) { 1 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/nope").openConnection() as HttpURLConnection
            assertEquals(404, conn.responseCode)
        }
    }

    @Test
    fun `media list returns json`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(10) { 1 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media").openConnection() as HttpURLConnection
            val body = conn.inputStream.use { it.readBytes() }
            val text = String(body, Charsets.UTF_8)
            assertEquals(200, conn.responseCode)
            assertTrue(text.contains("\"id\":\"m1\""))
            assertTrue(text.contains("\"size\":10"))
        }
    }

    @Test
    fun `head request returns headers without body`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(500) { 1 })
        withServer(TempRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1").openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            assertEquals(200, conn.responseCode)
            assertEquals("500", conn.getHeaderField("Content-Length"))
        }
    }

    @Test
    fun `thumbnail returns image bytes`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(100) { 1 })
        val thumbData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        withServer(ThumbRepo(mapOf("m1" to f), mapOf("m1" to thumbData))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1/thumbnail").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes() }
            assertEquals(200, code)
            assertEquals(6, body.size)
            assertEquals("image/jpeg", conn.contentType)
        }
    }

    @Test
    fun `thumbnail for unknown id returns 404`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(10) { 1 })
        withServer(ThumbRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/nope/thumbnail").openConnection() as HttpURLConnection
            assertEquals(404, conn.responseCode)
        }
    }

    @Test
    fun `thumbnail when not supported returns 404`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(10) { 1 })
        withServer(ThumbRepo(mapOf("m1" to f))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1/thumbnail").openConnection() as HttpURLConnection
            assertEquals(404, conn.responseCode)
        }
    }

    @Test
    fun `head on thumbnail returns headers without body`() {
        val f = File.createTempFile("hmv", ".mp4")
        f.writeBytes(ByteArray(100) { 1 })
        val thumbData = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        withServer(ThumbRepo(mapOf("m1" to f), mapOf("m1" to thumbData))) { port ->
            val conn = URL("http://127.0.0.1:$port/media/m1/thumbnail").openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            assertEquals(200, conn.responseCode)
            assertEquals("4", conn.getHeaderField("Content-Length"))
            assertEquals("image/png", conn.contentType)
        }
    }
}