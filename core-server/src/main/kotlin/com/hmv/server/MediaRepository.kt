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
    val relativePath: String,
    val thumbnailUri: String? = null,
    val folderName: String = ""
) : java.io.Serializable

/**
 * 文件操作结果
 */
data class FileOperationResult(
    val success: Boolean,
    val message: String = "",
    val id: String? = null
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

    /** 按指定路径列出媒体（子目录浏览）。 */
    fun list(path: String): List<MediaItem> = list()

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

    /**
     * 获取媒体缩略图流。
     *
     * @param id 来自 [MediaItem.id]
     * @return 缩略图字节流（JPEG/PNG），调用方负责 close；不支持时返回 null
     */
    fun getThumbnail(id: String): java.io.InputStream? = null

    // ========== 文件操作（需后端支持） ==========

    /** 上传文件到指定路径，返回操作结果。 */
    fun uploadFile(fileName: String, path: String, data: ByteArray): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 删除指定 id 的文件。 */
    fun deleteFile(id: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 重命名指定 id 的文件。 */
    fun renameFile(id: String, newName: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 创建文件夹。 */
    fun createFolder(name: String, parentPath: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    // ========== 扫描路径管理（PC 端支持） ==========

    /** 获取当前扫描路径列表。 */
    fun getScanPaths(): List<String> = emptyList()

    /** 添加扫描路径。 */
    fun addScanPath(path: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 移除扫描路径。 */
    fun removeScanPath(path: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 重新扫描所有路径。 */
    fun rescanAll(): FileOperationResult =
        FileOperationResult(false, "not supported")

    // ========== 管理密码与代理通道 ==========

    /** 获取管理密码（null 表示未设置）。 */
    fun getAdminPassword(): String? = null

    /** 设置管理密码。 */
    fun setAdminPassword(password: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 验证管理密码。 */
    fun verifyAdminPassword(password: String): Boolean = false

    /** 获取代理通道状态。 */
    fun getProxyStatus(): ProxyStatus = ProxyStatus(false, null)

    /** 启用代理通道。 */
    fun enableProxy(password: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 禁用代理通道。 */
    fun disableProxy(): FileOperationResult =
        FileOperationResult(false, "not supported")

    /** 通过代理通道执行文件操作（需验证密码）。 */
    fun proxyOperation(
        password: String,
        operation: String,
        params: Map<String, String>
    ): FileOperationResult = FileOperationResult(false, "not supported")

    // ========== 设备信息 ==========

    /** 获取设备类型（phone/pad/pc/harmony/ios/web）。 */
    fun getDeviceType(): String = "unknown"

    /** 获取设备名称。 */
    fun getDeviceName(): String = "unknown"

    /** 设置设备信息。 */
    fun setDeviceInfo(deviceType: String, deviceName: String): FileOperationResult =
        FileOperationResult(false, "not supported")

    // ========== 运行时统计 ==========

    /** 获取运行时统计信息。 */
    fun getRuntimeStats(): RuntimeStats = RuntimeStats()

    /** 获取流量统计。 */
    fun getTrafficStats(): TrafficStats = TrafficStats()

    /** 记录出站流量（本节点媒体被其他节点播放）。 */
    fun recordOutboundTraffic(bytes: Long, targetHost: String, mediaId: String) {}

    /** 记录入站流量（本节点播放其他节点媒体）。 */
    fun recordInboundTraffic(bytes: Long, sourceHost: String, mediaId: String) {}

    /** 获取运行日志。 */
    fun getLogs(limit: Int = 100): List<LogEntry> = emptyList()

    /** 添加日志条目。 */
    fun addLog(level: String, message: String, source: String = "server") {}

    /** 清空日志。 */
    fun clearLogs() {}
}

/**
 * 运行时统计信息
 */
data class RuntimeStats(
    val uptime: Long = 0,
    val cpuUsage: Double = 0.0,
    val memoryUsed: Long = 0,
    val memoryTotal: Long = 0,
    val storageUsed: Long = 0,
    val storageTotal: Long = 0,
    val batteryLevel: Int = -1,
    val batteryCharging: Boolean = false,
    val networkUpload: Long = 0,
    val networkDownload: Long = 0,
    val activeConnections: Int = 0,
    val totalRequests: Long = 0
)

/**
 * 流量统计
 */
data class TrafficStats(
    val totalOutbound: Long = 0,
    val totalInbound: Long = 0,
    val outboundByDevice: Map<String, Long> = emptyMap(),
    val inboundByDevice: Map<String, Long> = emptyMap(),
    val outboundByMedia: Map<String, Long> = emptyMap(),
    val inboundByMedia: Map<String, Long> = emptyMap()
)

/**
 * 日志条目
 */
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val message: String,
    val source: String
)

/**
 * 代理通道状态
 */
data class ProxyStatus(
    val enabled: Boolean,
    val password: String? = null
)
