import Foundation

/// 设备收藏管理器
class FavoritesManager: ObservableObject {
    @Published var favorites: [FavoriteDevice] = []
    
    private let storageKey = "hmv_favorites"
    
    init() {
        loadFavorites()
    }
    
    /// 加载收藏列表
    func loadFavorites() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let items = try? JSONDecoder().decode([FavoriteDevice].self, from: data) else {
            favorites = []
            return
        }
        favorites = items
    }
    
    /// 保存收藏列表
    private func saveFavorites() {
        if let data = try? JSONEncoder().encode(favorites) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
    
    /// 添加收藏
    func addFavorite(device: DiscoveredDevice, alias: String = "") {
        guard !isFavorite(deviceName: device.name) else {
            updateDeviceAddress(deviceName: device.name, host: device.host, port: device.port)
            return
        }
        
        let fav = FavoriteDevice(
            name: device.name,
            host: device.host,
            port: device.port,
            alias: alias
        )
        favorites.append(fav)
        saveFavorites()
    }
    
    /// 移除收藏
    func removeFavorite(deviceName: String) {
        favorites.removeAll { $0.name == deviceName }
        saveFavorites()
    }
    
    /// 切换收藏状态
    @discardableResult
    func toggleFavorite(device: DiscoveredDevice) -> Bool {
        if isFavorite(deviceName: device.name) {
            removeFavorite(deviceName: device.name)
            return false
        } else {
            addFavorite(device: device)
            return true
        }
    }
    
    /// 检查是否已收藏
    func isFavorite(deviceName: String) -> Bool {
        favorites.contains { $0.name == deviceName }
    }
    
    /// 设置设备别名
    func setAlias(deviceName: String, alias: String) {
        if let index = favorites.firstIndex(where: { $0.name == deviceName }) {
            favorites[index].alias = alias
            saveFavorites()
        }
    }
    
    /// 获取设备别名
    func getAlias(deviceName: String) -> String {
        favorites.first(where: { $0.name == deviceName })?.alias ?? ""
    }
    
    /// 更新设备地址
    func updateDeviceAddress(deviceName: String, host: String, port: Int) {
        if let index = favorites.firstIndex(where: { $0.name == deviceName }) {
            // Note: FavoriteDevice is struct, need to recreate
            let old = favorites[index]
            favorites[index] = FavoriteDevice(
                name: old.name,
                host: host,
                port: port,
                alias: old.alias
            )
            saveFavorites()
        }
    }
    
    /// 获取所有收藏
    func getFavorites() -> [FavoriteDevice] {
        favorites
    }
}
