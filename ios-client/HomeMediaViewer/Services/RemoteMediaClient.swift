import Foundation

/// 远程媒体客户端 - 连接 core-server HTTP API
class RemoteMediaClient {
    private let timeout: TimeInterval = 5.0
    
    /// 获取媒体列表
    func fetchMediaList(deviceAddress: String) async throws -> [MediaItem] {
        guard let url = URL(string: "\(deviceAddress)/media") else {
            throw URLError(.badURL)
        }
        
        let (data, response) = try await URLSession.shared.data(from: url)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let items = json?["items"] as? [[String: Any]] ?? []
        
        return try items.map { item in
            let jsonData = try JSONSerialization.data(withJSONObject: item)
            return try JSONDecoder().decode(MediaItem.self, from: jsonData)
        }
    }
    
    /// 获取媒体播放 URL
    func getMediaUrl(deviceAddress: String, mediaId: String) -> String {
        "\(deviceAddress)/media/\(mediaId)"
    }
    
    /// 获取缩略图 URL
    func getThumbnailUrl(deviceAddress: String, mediaId: String) -> String {
        "\(deviceAddress)/thumbnail/\(mediaId)"
    }
    
    /// 检查设备是否在线
    func checkDeviceOnline(deviceAddress: String) async -> Bool {
        guard let url = URL(string: deviceAddress) else { return false }
        
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.timeoutInterval = 3.0
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return false }
            return httpResponse.statusCode == 200
        } catch {
            return false
        }
    }
    
    /// 发送投屏命令到目标设备
    func sendCastCommand(
        deviceAddress: String,
        mediaId: String,
        title: String,
        position: TimeInterval
    ) async -> Bool {
        guard let url = URL(string: "\(deviceAddress)/play") else { return false }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout
        
        let body: [String: Any] = [
            "mediaId": mediaId,
            "title": title,
            "position": Int(position * 1000)
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return false }
            return httpResponse.statusCode == 200
        } catch {
            return false
        }
    }
}
