import Foundation
import AVFoundation

/// 媒体服务
/// 管理 HTTP Server 的生命周期
class MediaServerService: ObservableObject {
    static let shared = MediaServerService()
    
    private var httpServer: HttpServer?
    private var port: UInt16 = 8080
    @Published var isRunning = false
    private var mediaItems: [MediaItem] = []
    
    init() {
        httpServer = HttpServer(port: port)
    }
    
    /// 获取 HTTP 服务器实例
    func getServer() -> HttpServer? {
        return httpServer
    }
    
    /// 获取监听端口
    func getPort() -> UInt16 {
        return port
    }
    
    /// 设置媒体列表
    func setMediaItems(_ items: [MediaItem]) {
        self.mediaItems = items
        httpServer?.setMediaItems(items)
    }
    
    /// 启动服务
    func start() -> Bool {
        guard let server = httpServer else { return false }
        
        server.setMediaProvider { [weak self] in
            return self?.mediaItems ?? []
        }
        
        let started = server.start()
        if started {
            DispatchQueue.main.async {
                self.isRunning = true
                self.port = server.getPort()
            }
            print("MediaServerService started on port \(port)")
        }
        return started
    }
    
    /// 停止服务
    func stop() {
        httpServer?.stop()
        DispatchQueue.main.async {
            self.isRunning = false
        }
    }
    
    /// 更新设备信息
    func setDeviceInfo(deviceType: String, deviceName: String) {
        httpServer?.setDeviceInfo(deviceType: deviceType, deviceName: deviceName)
    }
    
    /// 设置管理密码
    func setAdminPassword(_ password: String) {
        httpServer?.setAdminPassword(password)
    }
    
    /// 获取服务状态
    func getStatus() -> [String: Any] {
        return [
            "running": isRunning,
            "port": port,
            "mediaCount": mediaItems.count
        ]
    }
}
