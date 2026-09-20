import Foundation

/// 媒体项目数据模型
struct MediaItem: Identifiable, Codable {
    let id: String
    let title: String
    let mimeType: String
    let size: Int64
    let relativePath: String
    let thumbnailUri: String
    let folderName: String
    
    var isVideo: Bool {
        mimeType.contains("video")
    }
    
    var isAudio: Bool {
        mimeType.contains("audio")
    }
    
    var formattedSize: String {
        ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }
    
    enum CodingKeys: String, CodingKey {
        case id, title, mimeType, size, relativePath, thumbnailUri, folderName
    }
}

/// 发现的设备
struct DiscoveredDevice: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let host: String
    let port: Int
    
    var address: String {
        "http://\(host):\(port)"
    }
    
    static func == (lhs: DiscoveredDevice, rhs: DiscoveredDevice) -> Bool {
        lhs.name == rhs.name && lhs.host == rhs.host && lhs.port == rhs.port
    }
}

/// 播放队列项
struct QueueItem: Identifiable {
    let id = UUID()
    let url: String
    let title: String
    let mediaId: String
}

/// 收藏设备
struct FavoriteDevice: Identifiable, Codable {
    let id = UUID()
    let name: String
    let host: String
    let port: Int
    var alias: String
    
    var displayName: String {
        alias.isEmpty ? name : alias
    }
    
    var address: String {
        "http://\(host):\(port)"
    }
    
    enum CodingKeys: String, CodingKey {
        case name, host, port, alias
    }
}
