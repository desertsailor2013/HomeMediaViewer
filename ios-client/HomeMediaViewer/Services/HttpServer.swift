import Foundation
import Network
import CryptoKit

/// HTTP+Range 流媒体服务端
/// 基于 Network.framework 的 NWListener 实现
class HttpServer {
    private var listener: NWListener?
    private var port: UInt16
    private var running = false
    private var mediaItems: [MediaItem] = []
    private var thumbnailProvider: ((String) -> Data?)?
    private var fileStreamProvider: ((String, Int, Int) -> Data?)?
    private var fileSizeProvider: ((String) -> Int)?
    private var adminPassword = ""
    private var deviceType = "phone"
    private var deviceName = "iOS Device"
    
    init(port: UInt16 = 0) {
        self.port = port
    }
    
    /// 设置媒体列表
    func setMediaItems(_ items: [MediaItem]) {
        self.mediaItems = items
    }
    
    /// 设置缩略图提供者
    func setThumbnailProvider(_ provider: @escaping (String) -> Data?) {
        self.thumbnailProvider = provider
    }
    
    /// 设置文件流提供者
    func setFileStreamProvider(_ provider: @escaping (String, Int, Int) -> Data?) {
        self.fileStreamProvider = provider
    }
    
    /// 设置文件大小提供者
    func setFileSizeProvider(_ provider: @escaping (String) -> Int) {
        self.fileSizeProvider = provider
    }
    
    /// 设置管理密码
    func setAdminPassword(_ password: String) {
        self.adminPassword = password
    }
    
    /// 设置设备信息
    func setDeviceInfo(deviceType: String, deviceName: String) {
        self.deviceType = deviceType
        self.deviceName = deviceName
    }
    
    /// 获取监听端口
    func getPort() -> UInt16 {
        return port
    }
    
    /// 是否运行中
    func isRunning() -> Bool {
        return running
    }
    
    /// 启动服务器
    func start() -> Bool {
        if running { return true }
        
        do {
            let parameters = NWParameters.tcp
            parameters.allowLocalEndpointReuse = true
            
            if port == 0 {
                listener = try NWListener(using: parameters)
            } else {
                listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
            }
            
            listener?.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.running = true
                    if let actualPort = self?.listener?.localPort {
                        self?.port = actualPort
                        print("HTTP Server started on port \(actualPort)")
                    }
                case .failed(let error):
                    print("HTTP Server failed: \(error)")
                    self?.running = false
                case .cancelled:
                    self?.running = false
                default:
                    break
                }
            }
            
            listener?.newConnectionHandler = { [weak self] connection in
                self?.handleConnection(connection)
            }
            
            listener?.start(queue: .global(qos: .userInitiated))
            return true
        } catch {
            print("Failed to start HTTP server: \(error)")
            return false
        }
    }
    
    /// 停止服务器
    func stop() {
        running = false
        listener?.cancel()
        listener = nil
    }
    
    /// 处理新连接
    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: .global(qos: .userInitiated))
        
        receiveRequest(connection: connection) { [weak self] request in
            guard let self = self, let request = request else {
                connection.cancel()
                return
            }
            
            let response = self.handleRequest(request)
            self.sendResponse(connection: connection, response: response)
        }
    }
    
    /// 接收请求
    private func receiveRequest(connection: NWConnection, completion: @escaping (HttpRequest?) -> Void) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { data, _, isComplete, error in
            if let error = error {
                print("Receive error: \(error)")
                completion(nil)
                return
            }
            
            guard let data = data, !data.isEmpty else {
                completion(nil)
                return
            }
            
            let request = self.parseRequest(data)
            completion(request)
        }
    }
    
    /// 解析 HTTP 请求
    private func parseRequest(_ data: Data) -> HttpRequest? {
        guard let text = String(data: data, encoding: .utf8) else { return nil }
        
        let lines = text.components(separatedBy: "\r\n")
        guard !lines.isEmpty else { return nil }
        
        let parts = lines[0].components(separatedBy: " ")
        guard parts.count >= 2 else { return nil }
        
        let method = parts[0]
        let fullPath = parts[1]
        
        var path = fullPath
        var queryParams: [String: String] = [:]
        
        if let questionIndex = fullPath.firstIndex(of: "?") {
            path = String(fullPath[fullPath.startIndex..<questionIndex])
            let queryString = String(fullPath[fullPath.index(after: questionIndex)...])
            for pair in queryString.components(separatedBy: "&") {
                let kv = pair.components(separatedBy: "=")
                if kv.count == 2, let key = kv[0].removingPercentEncoding {
                    queryParams[key] = kv[1].removingPercentEncoding ?? kv[1]
                }
            }
        }
        
        var headers: [String: String] = [:]
        var bodyStartIndex = lines.count
        
        for i in 1..<lines.count {
            if lines[i].isEmpty {
                bodyStartIndex = i + 1
                break
            }
            let colonIndex = lines[i].firstIndex(of: ":")
            if let colonIndex = colonIndex {
                let key = String(lines[i][..<colonIndex]).trimmingCharacters(in: .whitespaces).lowercased()
                let value = String(lines[i][lines.index(after: colonIndex)...]).trimmingCharacters(in: .whitespaces)
                headers[key] = value
            }
        }
        
        let body = bodyStartIndex < lines.count ? lines[bodyStartIndex...].joined(separator: "\r\n") : ""
        
        return HttpRequest(method: method, path: path, headers: headers, body: body, queryParams: queryParams)
    }
    
    /// 处理请求
    private func handleRequest(_ request: HttpRequest) -> HttpResponse {
        let method = request.method
        let path = request.path
        let headers = request.headers
        let body = request.body
        let queryParams = request.queryParams
        
        // 路由匹配
        if method == "GET" && path == "/media" {
            return handleListMedia()
        }
        if method == "GET" && path.range(of: "^/media/[^/]+$", options: .regularExpression) != nil {
            let id = path.components(separatedBy: "/")[2]
            return handleStreamMedia(id: id, headers: headers)
        }
        if method == "GET" && path.range(of: "^/media/[^/]+/thumbnail$", options: .regularExpression) != nil {
            let id = path.components(separatedBy: "/")[2]
            return handleThumbnail(id: id)
        }
        if method == "GET" && path == "/device/info" {
            return handleGetDeviceInfo()
        }
        if method == "POST" && path == "/device/info" {
            return handleSetDeviceInfo(body: body)
        }
        if method == "GET" && path == "/admin/password" {
            return handleGetAdminPassword()
        }
        if method == "POST" && path == "/admin/password" {
            return handleSetAdminPassword(body: body)
        }
        if method == "POST" && path == "/admin/verify" {
            return handleVerifyAdminPassword(body: body)
        }
        if method == "GET" && path == "/search" {
            let query = queryParams["q"] ?? ""
            let type = queryParams["type"] ?? "all"
            return handleSearch(query: query, type: type)
        }
        if method == "POST" && path == "/play" {
            return handlePlayCommand(body: body)
        }
        
        return HttpResponse(status: "404 Not Found", headers: [:], body: "Not Found".data(using: .utf8)!)
    }
    
    // MARK: - 路由处理器
    
    /// 获取媒体列表
    private func handleListMedia() -> HttpResponse {
        let jsonData = mediaItems.map { item in
            [
                "id": item.id,
                "title": item.title,
                "mimeType": item.mimeType,
                "size": item.size,
                "relativePath": item.relativePath,
                "thumbnailUri": item.thumbnailUri ?? "",
                "folderName": item.folderName
            ] as [String: Any]
        }
        
        guard let data = try? JSONSerialization.data(withJSONObject: jsonData) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        
        return HttpResponse(status: "200 OK", headers: ["Content-Type": "application/json; charset=utf-8"], body: data)
    }
    
    /// 流式传输媒体
    private func handleStreamMedia(id: String, headers: [String: String]) -> HttpResponse {
        guard let item = mediaItems.first(where: { $0.id == id }) else {
            return HttpResponse(status: "404 Not Found", headers: [:], body: "Not Found".data(using: .utf8)!)
        }
        
        guard let fileStreamProvider = fileStreamProvider, let fileSizeProvider = fileSizeProvider else {
            return HttpResponse(status: "404 Not Found", headers: [:], body: "File provider not set".data(using: .utf8)!)
        }
        
        let fileSize = fileSizeProvider(item.relativePath)
        
        if let rangeHeader = headers["range"], let range = parseRange(rangeHeader, fileSize: fileSize) {
            let data = fileStreamProvider(item.relativePath, range.offset, range.length)
            guard let responseData = data else {
                return HttpResponse(status: "404 Not Found", headers: [:], body: "Read failed".data(using: .utf8)!)
            }
            
            let responseHeaders = [
                "Content-Range": "bytes \(range.offset)-\(range.offset + range.length - 1)/\(fileSize)",
                "Content-Length": "\(responseData.count)",
                "Content-Type": "application/octet-stream",
                "Accept-Ranges": "bytes"
            ]
            return HttpResponse(status: "206 Partial Content", headers: responseHeaders, body: responseData)
        }
        
        // 整文件
        guard let data = fileStreamProvider(item.relativePath, 0, fileSize) else {
            return HttpResponse(status: "404 Not Found", headers: [:], body: "Read failed".data(using: .utf8)!)
        }
        
        let responseHeaders = [
            "Content-Length": "\(data.count)",
            "Content-Type": "application/octet-stream",
            "Accept-Ranges": "bytes"
        ]
        return HttpResponse(status: "200 OK", headers: responseHeaders, body: data)
    }
    
    /// 解析 Range 头
    private func parseRange(_ rangeHeader: String, fileSize: Int) -> (offset: Int, length: Int)? {
        let pattern = #"bytes=(\d+)-(\d*)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: rangeHeader, range: NSRange(location: 0, length: rangeHeader.utf16.count)) else {
            return nil
        }
        
        guard let startRange = Range(match.range(at: 1), in: rangeHeader),
              let start = Int(rangeHeader[startRange]) else {
            return nil
        }
        
        var end = fileSize - 1
        if let endRange = Range(match.range(at: 2), in: rangeHeader),
           let endValue = Int(rangeHeader[endRange]) {
            end = endValue
        }
        
        guard start < fileSize, start >= 0, start <= end else { return nil }
        
        let length = min(end - start + 1, fileSize - start)
        return (offset: start, length: length)
    }
    
    /// 获取缩略图
    private func handleThumbnail(id: String) -> HttpResponse {
        guard let provider = thumbnailProvider, let data = provider(id) else {
            return HttpResponse(status: "404 Not Found", headers: [:], body: "Thumbnail not found".data(using: .utf8)!)
        }
        
        let headers = [
            "Content-Type": "image/jpeg",
            "Content-Length": "\(data.count)"
        ]
        return HttpResponse(status: "200 OK", headers: headers, body: data)
    }
    
    /// 获取设备信息
    private func handleGetDeviceInfo() -> HttpResponse {
        let json = ["deviceType": deviceType, "deviceName": deviceName] as [String: Any]
        guard let data = try? JSONSerialization.data(withJSONObject: json) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: ["Content-Type": "application/json; charset=utf-8"], body: data)
    }
    
    /// 设置设备信息
    private func handleSetDeviceInfo(body: String) -> HttpResponse {
        guard let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return HttpResponse(status: "400 Bad Request", headers: [:], body: "Invalid JSON".data(using: .utf8)!)
        }
        
        if let type = json["deviceType"] as? String { deviceType = type }
        if let name = json["deviceName"] as? String { deviceName = name }
        
        let response = ["success": true]
        guard let responseData = try? JSONSerialization.data(withJSONObject: response) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: [:], body: responseData)
    }
    
    /// 获取管理密码状态
    private func handleGetAdminPassword() -> HttpResponse {
        let json = ["configured": !adminPassword.isEmpty]
        guard let data = try? JSONSerialization.data(withJSONObject: json) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: ["Content-Type": "application/json; charset=utf-8"], body: data)
    }
    
    /// 设置管理密码
    private func handleSetAdminPassword(body: String) -> HttpResponse {
        guard let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let password = json["password"] as? String else {
            return HttpResponse(status: "400 Bad Request", headers: [:], body: "Password required".data(using: .utf8)!)
        }
        
        adminPassword = password
        let response = ["success": true]
        guard let responseData = try? JSONSerialization.data(withJSONObject: response) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: [:], body: responseData)
    }
    
    /// 验证管理密码
    private func handleVerifyAdminPassword(body: String) -> HttpResponse {
        guard let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let password = json["password"] as? String else {
            return HttpResponse(status: "400 Bad Request", headers: [:], body: "Invalid JSON".data(using: .utf8)!)
        }
        
        let valid = adminPassword == password
        let jsonResp = ["valid": valid]
        guard let responseData = try? JSONSerialization.data(withJSONObject: jsonResp) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: ["Content-Type": "application/json; charset=utf-8"], body: responseData)
    }
    
    /// 搜索媒体
    private func handleSearch(query: String, type: String) -> HttpResponse {
        var items = mediaItems
        
        if !query.isEmpty {
            items = items.filter { $0.title.localizedCaseInsensitiveContains(query) }
        }
        
        if type != "all" {
            items = items.filter { $0.mimeType.hasPrefix("\(type)/") }
        }
        
        let jsonData = items.map { item in
            [
                "id": item.id,
                "title": item.title,
                "mimeType": item.mimeType,
                "size": item.size,
                "relativePath": item.relativePath,
                "thumbnailUri": item.thumbnailUri ?? "",
                "folderName": item.folderName
            ] as [String: Any]
        }
        
        guard let data = try? JSONSerialization.data(withJSONObject: jsonData) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: ["Content-Type": "application/json; charset=utf-8"], body: data)
    }
    
    /// 处理播放命令
    private func handlePlayCommand(body: String) -> HttpResponse {
        guard let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return HttpResponse(status: "400 Bad Request", headers: [:], body: "Invalid JSON".data(using: .utf8)!)
        }
        
        if let mediaId = json["mediaId"] as? String, let position = json["position"] as? Double {
            print("Play command: mediaId=\(mediaId), position=\(position)")
        }
        
        let response = ["success": true]
        guard let responseData = try? JSONSerialization.data(withJSONObject: response) else {
            return HttpResponse(status: "500 Internal Server Error", headers: [:], body: "JSON error".data(using: .utf8)!)
        }
        return HttpResponse(status: "200 OK", headers: [:], body: responseData)
    }
    
    /// 发送响应
    private func sendResponse(connection: NWConnection, response: HttpResponse) {
        var headerString = "HTTP/1.1 \(response.status)\r\n"
        headerString += "Content-Length: \(response.body.count)\r\n"
        headerString += "Connection: close\r\n"
        
        for (key, value) in response.headers {
            headerString += "\(key): \(value)\r\n"
        }
        headerString += "\r\n"
        
        var responseData = Data(headerString.utf8)
        responseData.append(response.body)
        
        connection.send(content: responseData, completion: .contentProcessed { error in
            if let error = error {
                print("Send error: \(error)")
            }
            connection.cancel()
        })
    }
}

// MARK: - 数据结构

struct HttpRequest {
    let method: String
    let path: String
    let headers: [String: String]
    let body: String
    let queryParams: [String: String]
}

struct HttpResponse {
    let status: String
    let headers: [String: String]
    let body: Data
}
