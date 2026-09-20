import Foundation
import AVKit
import Combine

/// 主视图模型
class MainViewModel: ObservableObject {
    @Published var mediaItems: [MediaItem] = []
    @Published var devices: [DiscoveredDevice] = []
    @Published var status: String = "正在扫描媒体..."
    @Published var searchQuery: String = ""
    @Published var currentDevice: DiscoveredDevice?
    @Published var isGroupByFolder: Bool = false
    @Published var typeFilter: TypeFilter = .all
    @Published var showAliasDialog: Bool = false
    @Published var aliasDevice: DiscoveredDevice?
    @Published var aliasText: String = ""
    
    enum TypeFilter: String, CaseIterable {
        case all = "全部"
        case video = "视频"
        case audio = "音频"
    }
    
    private let remoteClient = RemoteMediaClient()
    let favoritesManager = FavoritesManager()
    private let mediaScanner = MediaScanner()
    private var nsdHelper: NsdHelper?
    private var expandedFolders: Set<String> = []
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // 监听媒体扫描结果
        mediaScanner.$mediaItems
            .receive(on: DispatchQueue.main)
            .sink { [weak self] items in
                self?.mediaItems = items
                self?.status = "本机媒体 - \(items.count) 个文件"
            }
            .store(in: &cancellables)
        
        startDeviceDiscovery()
        loadLocalMedia()
    }
    
    /// 加载本地媒体
    func loadLocalMedia() {
        status = "正在请求权限..."
        
        mediaScanner.requestAuthorization { [weak self] authorized in
            if authorized {
                self?.status = "正在扫描媒体..."
                self?.mediaScanner.scanAll()
            } else {
                self?.status = "需要相册权限才能扫描本地媒体"
            }
        }
    }
    
    /// 开始设备发现
    func startDeviceDiscovery() {
        // 尝试直连收藏设备
        tryConnectFavorites()
        
        nsdHelper = NsdHelper()
        nsdHelper?.delegate = self
        nsdHelper?.startDiscovery()
    }
    
    /// 尝试直连收藏设备
    private func tryConnectFavorites() {
        let favorites = favoritesManager.getFavorites()
        
        for fav in favorites {
            Task {
                let online = await remoteClient.checkDeviceOnline(deviceAddress: fav.address)
                if online {
                    DispatchQueue.main.async {
                        let device = DiscoveredDevice(
                            name: fav.name,
                            host: fav.host,
                            port: fav.port
                        )
                        if !self.devices.contains(where: { $0.name == device.name }) {
                            self.devices.append(device)
                        }
                    }
                }
            }
        }
    }
    
    /// 连接远程设备
    func connectDevice(_ device: DiscoveredDevice) {
        currentDevice = device
        status = "正在获取 \(device.name) 的媒体列表..."
        
        Task {
            do {
                let items = try await remoteClient.fetchMediaList(deviceAddress: device.address)
                DispatchQueue.main.async {
                    self.mediaItems = items
                    self.status = "\(device.name) - \(items.count) 个媒体文件"
                }
            } catch {
                DispatchQueue.main.async {
                    self.status = "获取失败: \(error.localizedDescription)"
                }
            }
        }
    }
    
    /// 获取筛选后的媒体列表
    var filteredItems: [MediaItem] {
        var items = mediaItems
        
        if !searchQuery.isEmpty {
            items = items.filter {
                $0.title.localizedCaseInsensitiveContains(searchQuery) ||
                $0.folderName.localizedCaseInsensitiveContains(searchQuery)
            }
        }
        
        switch typeFilter {
        case .all:
            break
        case .video:
            items = items.filter { $0.isVideo }
        case .audio:
            items = items.filter { $0.isAudio }
        }
        
        return items
    }
    
    /// 获取分组后的媒体列表
    var groupedItems: [(String, [MediaItem])] {
        let filtered = filteredItems
        let grouped = Dictionary(grouping: filtered) { $0.folderName.isEmpty ? "未分类" : $0.folderName }
        return grouped.sorted { $0.key < $1.key }
    }
    
    /// 检查文件夹是否展开
    func isFolderExpanded(_ folder: String) -> Bool {
        expandedFolders.contains(folder)
    }
    
    /// 切换文件夹展开状态
    func toggleFolder(_ folder: String) {
        if expandedFolders.contains(folder) {
            expandedFolders.remove(folder)
        } else {
            expandedFolders.insert(folder)
        }
    }
    
    /// 播放媒体
    func playMedia(_ item: MediaItem) {
        // 触发播放事件
        NotificationCenter.default.post(
            name: .playMedia,
            object: nil,
            userInfo: ["item": item]
        )
    }
    
    /// 全部播放
    func playAll() {
        let items = filteredItems
        guard let first = items.first else { return }
        
        NotificationCenter.default.post(
            name: .playAll,
            object: nil,
            userInfo: ["items": items]
        )
    }
    
    /// 设置设备别名
    func setAlias() {
        guard let device = aliasDevice else { return }
        favoritesManager.setAlias(deviceName: device.name, alias: aliasText)
        showAliasDialog = false
    }
}

extension Notification.Name {
    static let playMedia = Notification.Name("playMedia")
    static let playAll = Notification.Name("playAll")
}
