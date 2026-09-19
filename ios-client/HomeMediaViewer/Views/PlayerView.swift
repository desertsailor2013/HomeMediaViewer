import SwiftUI
import AVKit

/// 播放器页面
struct PlayerView: View {
    @State private var player: AVPlayer?
    @State private var queue: [QueueItem] = []
    @State private var queueIndex: Int = 0
    @State private var currentTitle: String = ""
    @State private var isPlaying: Bool = false
    @State private var isFullscreen: Bool = false
    @State private var currentSpeed: Float = 1.0
    @State private var currentPosition: TimeInterval = 0
    @State private var duration: TimeInterval = 0
    @State private var showQueue: Bool = false
    @State private var showCastDialog: Bool = false
    @State private var networkError: Bool = false
    @State private var devices: [DiscoveredDevice] = []
    
    private let speedOptions: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]
    private let remoteClient = RemoteMediaClient()
    private let progressManager = PlayProgressManager()
    private let speedManager = PlaybackSpeedManager()
    
    var body: some View {
        VStack(spacing: 0) {
            // 标题栏
            HStack {
                Button(action: {
                    // 返回
                }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.white)
                }
                
                Text(currentTitle)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Spacer()
            }
            .padding()
            .background(Color.black)
            
            // 播放器区域
            if let player = player {
                VideoPlayer(player: player)
                    .aspectRatio(16/9, contentMode: .fit)
                    .background(Color.black)
            } else {
                Rectangle()
                    .fill(Color.black)
                    .aspectRatio(16/9, contentMode: .fit)
                    .overlay(
                        Text("无媒体")
                            .foregroundColor(.gray)
                    )
            }
            
            // 进度条
            VStack(spacing: 4) {
                Slider(
                    value: Binding(
                        get: { currentPosition },
                        set: { newValue in
                            player?.seek(to: CMTime(seconds: newValue, preferredTimescale: 600))
                            currentPosition = newValue
                        }
                    ),
                    in: 0...max(duration, 1)
                )
                .accentColor(.blue)
                
                HStack {
                    Text(formatTime(currentPosition))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    Text(formatTime(duration))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.horizontal)
            
            // 控制栏
            HStack(spacing: 24) {
                // 速度按钮
                Button(action: changeSpeed) {
                    Text("\(String(format: "%.1f", currentSpeed))x")
                        .font(.subheadline)
                        .foregroundColor(.blue)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(.systemGray5))
                        .cornerRadius(16)
                }
                
                // 上一个
                Button(action: playPrevious) {
                    Image(systemName: "backward.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                }
                
                // 播放/暂停
                Button(action: togglePlayPause) {
                    Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 50))
                        .foregroundColor(.white)
                }
                
                // 下一个
                Button(action: playNext) {
                    Image(systemName: "forward.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                }
                
                // 投屏按钮
                Button(action: { showCastDialog = true }) {
                    Image(systemName: "airplayvideo")
                        .font(.title3)
                        .foregroundColor(.blue)
                }
                
                // 全屏按钮
                Button(action: toggleFullscreen) {
                    Image(systemName: isFullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                        .font(.title3)
                        .foregroundColor(.blue)
                }
            }
            .padding()
            .background(Color.black)
            
            // 队列面板
            if showQueue {
                VStack(alignment: .leading, spacing: 0) {
                    Text("播放队列")
                        .font(.headline)
                        .padding()
                    
                    List {
                        ForEach(Array(queue.enumerated()), id: \.element.id) { index, item in
                            HStack {
                                if index == queueIndex {
                                    Image(systemName: "play.fill")
                                        .foregroundColor(.blue)
                                }
                                
                                Text(item.title.isEmpty ? "未知标题" : item.title)
                                    .foregroundColor(index == queueIndex ? .blue : .primary)
                                    .lineLimit(1)
                                
                                Spacer()
                                
                                Button(action: {
                                    removeQueueItem(at: index)
                                }) {
                                    Image(systemName: "xmark.circle")
                                        .foregroundColor(.gray)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                queueIndex = index
                                startPlayback()
                            }
                        }
                    }
                    .listStyle(PlainListStyle())
                    
                    Button(action: {
                        // 清空队列并返回
                    }) {
                        Text("清空队列")
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                }
                .frame(height: 300)
                .background(Color(.systemBackground))
            }
            
            // 底部按钮
            HStack {
                Button(action: { showQueue.toggle() }) {
                    Text("队列")
                        .foregroundColor(.blue)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color.black)
        }
        .background(Color.black)
        .ignoresSafeArea(edges: .bottom)
        .onAppear {
            loadSpeed()
        }
        .onDisappear {
            saveProgress()
            player?.pause()
        }
        .sheet(isPresented: $showCastDialog) {
            CastDeviceSheet(
                devices: devices,
                onSelect: { device in
                    castToDevice(device)
                    showCastDialog = false
                },
                onCancel: {
                    showCastDialog = false
                }
            )
        }
    }
    
    private func loadSpeed() {
        currentSpeed = speedManager.restore()
    }
    
    private func startPlayback() {
        guard queueIndex >= 0, queueIndex < queue.count else { return }
        
        let item = queue[queueIndex]
        currentTitle = item.title
        
        guard let url = URL(string: item.url) else { return }
        
        player?.pause()
        player = AVPlayer(url: url)
        player?.play()
        isPlaying = true
        
        // 恢复播放速度
        player?.rate = currentSpeed
        
        // 开始更新进度
        startProgressTimer()
    }
    
    private func startProgressTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            guard let player = player else { return }
            currentPosition = player.currentTime().seconds
            duration = player.currentItem?.duration.seconds ?? 0
        }
    }
    
    private func togglePlayPause() {
        guard let player = player else { return }
        
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }
    
    private func playNext() {
        guard queueIndex < queue.count - 1 else { return }
        saveProgress()
        queueIndex += 1
        startPlayback()
    }
    
    private func playPrevious() {
        guard queueIndex > 0 else { return }
        saveProgress()
        queueIndex -= 1
        startPlayback()
    }
    
    private func changeSpeed() {
        let currentIndex = speedOptions.firstIndex(of: currentSpeed) ?? 2
        let nextIndex = (currentIndex + 1) % speedOptions.count
        currentSpeed = speedOptions[nextIndex]
        speedManager.save(speed: currentSpeed)
        player?.rate = currentSpeed
    }
    
    private func toggleFullscreen() {
        isFullscreen.toggle()
    }
    
    private func saveProgress() {
        guard queueIndex >= 0, queueIndex < queue.count else { return }
        let item = queue[queueIndex]
        if !item.mediaId.isEmpty {
            progressManager.save(mediaId: item.mediaId, position: currentPosition, duration: duration)
        }
    }
    
    private func removeQueueItem(at index: Int) {
        guard index >= 0, index < queue.count else { return }
        
        let wasPlaying = isPlaying
        queue.remove(at: index)
        
        if queue.isEmpty {
            player?.pause()
            return
        }
        
        if index < queueIndex {
            queueIndex -= 1
        } else if index == queueIndex && queueIndex >= queue.count {
            queueIndex = queue.count - 1
        }
        
        if wasPlaying {
            startPlayback()
        }
    }
    
    private func castToDevice(_ device: DiscoveredDevice) {
        guard queueIndex >= 0, queueIndex < queue.count else { return }
        let item = queue[queueIndex]
        
        Task {
            let success = await remoteClient.sendCastCommand(
                deviceAddress: device.address,
                mediaId: item.mediaId,
                title: item.title,
                position: currentPosition
            )
            
            DispatchQueue.main.async {
                // 显示投屏结果提示
            }
        }
    }
    
    private func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

/// 投屏设备选择器
struct CastDeviceSheet: View {
    let devices: [DiscoveredDevice]
    let onSelect: (DiscoveredDevice) -> Void
    let onCancel: () -> Void
    
    var body: some View {
        NavigationView {
            List {
                if devices.isEmpty {
                    Text("无可用设备")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(devices) { device in
                        Button(action: { onSelect(device) }) {
                            VStack(alignment: .leading) {
                                Text(device.name)
                                    .font(.headline)
                                Text("\(device.host):\(device.port)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("投屏到...")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        onCancel()
                    }
                }
            }
        }
    }
}

struct PlayerView_Previews: PreviewProvider {
    static var previews: some View {
        PlayerView()
    }
}
