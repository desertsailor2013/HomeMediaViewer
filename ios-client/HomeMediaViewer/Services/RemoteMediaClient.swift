import Foundation

/// 文件操作结果
struct FileOperationResult: Codable {
    let success: Bool
    let message: String?
    let id: String?
}

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
    
    // MARK: - File Management API
    
    /// 上传文件
    func uploadFile(
        deviceAddress: String,
        fileData: Data,
        fileName: String,
        path: String = "/"
    ) async -> FileOperationResult {
        guard let url = URL(string: "\(deviceAddress)/upload") else {
            return FileOperationResult(success: false, message: "Invalid URL", id: nil)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30.0
        
        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"path\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(path)\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        
        request.httpBody = body
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return FileOperationResult(success: false, message: "HTTP Error", id: nil)
            }
            return try JSONDecoder().decode(FileOperationResult.self, from: data)
        } catch {
            return FileOperationResult(success: false, message: error.localizedDescription, id: nil)
        }
    }
    
    /// 删除文件
    func deleteFile(deviceAddress: String, mediaId: String) async -> FileOperationResult {
        guard let url = URL(string: "\(deviceAddress)/media/\(mediaId)") else {
            return FileOperationResult(success: false, message: "Invalid URL", id: nil)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.timeoutInterval = timeout
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return FileOperationResult(success: false, message: "HTTP Error", id: nil)
            }
            return try JSONDecoder().decode(FileOperationResult.self, from: data)
        } catch {
            return FileOperationResult(success: false, message: error.localizedDescription, id: nil)
        }
    }
    
    /// 重命名文件
    func renameFile(
        deviceAddress: String,
        mediaId: String,
        newName: String
    ) async -> FileOperationResult {
        guard let url = URL(string: "\(deviceAddress)/media/\(mediaId)/rename") else {
            return FileOperationResult(success: false, message: "Invalid URL", id: nil)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout
        
        let body: [String: Any] = ["name": newName]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return FileOperationResult(success: false, message: "HTTP Error", id: nil)
            }
            return try JSONDecoder().decode(FileOperationResult.self, from: data)
        } catch {
            return FileOperationResult(success: false, message: error.localizedDescription, id: nil)
        }
    }
    
    /// 新建文件夹
    func createFolder(
        deviceAddress: String,
        name: String,
        parentPath: String = "/"
    ) async -> FileOperationResult {
        guard let url = URL(string: "\(deviceAddress)/folder") else {
            return FileOperationResult(success: false, message: "Invalid URL", id: nil)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout
        
        let body: [String: Any] = ["name": name, "path": parentPath]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return FileOperationResult(success: false, message: "HTTP Error", id: nil)
            }
            return try JSONDecoder().decode(FileOperationResult.self, from: data)
        } catch {
            return FileOperationResult(success: false, message: error.localizedDescription, id: nil)
        }
    }
}
