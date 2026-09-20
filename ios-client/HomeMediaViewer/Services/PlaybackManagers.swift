import Foundation

/// 播放进度管理器
class PlayProgressManager {
    private let storageKey = "hmv_play_progress"
    
    struct ProgressData: Codable {
        let position: TimeInterval
        let duration: TimeInterval
        let timestamp: Date
    }
    
    /// 保存播放进度
    func save(mediaId: String, position: TimeInterval, duration: TimeInterval) {
        var progress = getAllProgress()
        progress[mediaId] = ProgressData(
            position: position,
            duration: duration,
            timestamp: Date()
        )
        
        if let data = try? JSONEncoder().encode(progress) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
    
    /// 恢复播放进度
    func restore(mediaId: String) -> TimeInterval {
        let progress = getAllProgress()
        return progress[mediaId]?.position ?? 0
    }
    
    /// 清除播放进度
    func clear(mediaId: String) {
        var progress = getAllProgress()
        progress.removeValue(forKey: mediaId)
        
        if let data = try? JSONEncoder().encode(progress) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
    
    private func getAllProgress() -> [String: ProgressData] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let progress = try? JSONDecoder().decode([String: ProgressData].self, from: data) else {
            return [:]
        }
        return progress
    }
}

/// 播放速度管理器
class PlaybackSpeedManager {
    private let storageKey = "hmv_playback_speed"
    
    /// 保存播放速度
    func save(speed: Float) {
        UserDefaults.standard.set(speed, forKey: storageKey)
    }
    
    /// 恢复播放速度
    func restore() -> Float {
        UserDefaults.standard.float(forKey: storageKey).clamped(to: 0.5...2.0)
    }
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
