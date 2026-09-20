import SwiftUI

/// 主页面
struct ContentView: View {
    @StateObject private var viewModel = MainViewModel()
    @State private var showPlayer = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // 标题栏
                HStack {
                    Text("HomeMediaViewer")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Color.blue)
                
                // 状态栏
                Text(viewModel.status)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.horizontal)
                    .padding(.top, 8)
                
                // 设备列表
                if !viewModel.devices.isEmpty {
                    Section(header: Text("发现的设备")) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(viewModel.devices) { device in
                                    DeviceCard(
                                        device: device,
                                        isFavorite: viewModel.favoritesManager.isFavorite(deviceName: device.name),
                                        alias: viewModel.favoritesManager.getAlias(deviceName: device.name),
                                        onTap: {
                                            viewModel.connectDevice(device)
                                        },
                                        onFavorite: {
                                            if viewModel.favoritesManager.isFavorite(deviceName: device.name) {
                                                viewModel.aliasDevice = device
                                                viewModel.aliasText = viewModel.favoritesManager.getAlias(deviceName: device.name)
                                                viewModel.showAliasDialog = true
                                            } else {
                                                viewModel.favoritesManager.addFavorite(device: device)
                                            }
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal)
                        }
                        .frame(height: 100)
                    }
                }
                
                // 媒体标题
                HStack {
                    Text(viewModel.currentDevice?.name ?? "本机媒体")
                        .font(.headline)
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.top, 16)
                
                // 搜索框
                TextField("搜索媒体文件...", text: $viewModel.searchQuery)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                
                // 筛选按钮
                HStack {
                    ForEach(MainViewModel.TypeFilter.allCases, id: \.self) { filter in
                        Button(action: {
                            viewModel.typeFilter = filter
                        }) {
                            Text(filter.rawValue)
                                .font(.subheadline)
                                .foregroundColor(viewModel.typeFilter == filter ? .blue : .gray)
                                .fontWeight(viewModel.typeFilter == filter ? .bold : .regular)
                        }
                        
                        Spacer()
                    }
                    
                    Button(action: {
                        viewModel.isGroupByFolder.toggle()
                    }) {
                        Text(viewModel.isGroupByFolder ? "平铺" : "按文件夹")
                            .font(.caption)
                            .foregroundColor(viewModel.isGroupByFolder ? .blue : .gray)
                    }
                }
                .padding(.horizontal)
                
                // 全部播放按钮
                if !viewModel.filteredItems.isEmpty {
                    Button(action: {
                        viewModel.playAll()
                    }) {
                        Text("全部播放")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.blue)
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }
                
                // 文件管理按钮
                if viewModel.isConnected {
                    NavigationLink(destination: FileManagerView(deviceAddress: viewModel.deviceAddress)) {
                        Label("文件管理", systemImage: "folder")
                            .font(.subheadline)
                            .foregroundColor(.blue)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color(.systemGray6))
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                }
                
                // 媒体列表
                List {
                    if viewModel.isGroupByFolder {
                        ForEach(viewModel.groupedItems, id: \.0) { folder, items in
                            Section(header:
                                HStack {
                                    Text("📁 \(folder)")
                                    Spacer()
                                    Text("\(items.count) 个文件")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    Image(systemName: viewModel.isFolderExpanded(folder) ? "chevron.down" : "chevron.right")
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.toggleFolder(folder)
                                }
                            ) {
                                if viewModel.isFolderExpanded(folder) {
                                    ForEach(items) { item in
                                        MediaRow(item: item) {
                                            viewModel.playMedia(item)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        ForEach(viewModel.filteredItems) { item in
                            MediaRow(item: item) {
                                viewModel.playMedia(item)
                            }
                        }
                    }
                    
                    if viewModel.filteredItems.isEmpty {
                        Text("没有媒体文件")
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .listStyle(PlainListStyle())
            }
            .navigationBarHidden(true)
        }
        .sheet(isPresented: $showPlayer) {
            PlayerView()
        }
        .alert("设置别名", isPresented: $viewModel.showAliasDialog) {
            TextField("设备别名", text: $viewModel.aliasText)
            Button("保存") {
                viewModel.setAlias()
            }
            Button("取消收藏") {
                if let device = viewModel.aliasDevice {
                    viewModel.favoritesManager.removeFavorite(deviceName: device.name)
                }
                viewModel.showAliasDialog = false
            }
            Button("取消", role: .cancel) {
                viewModel.showAliasDialog = false
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .playMedia)) { _ in
            showPlayer = true
        }
        .onReceive(NotificationCenter.default.publisher(for: .playAll)) { _ in
            showPlayer = true
        }
    }
}

/// 设备卡片
struct DeviceCard: View {
    let device: DiscoveredDevice
    let isFavorite: Bool
    let alias: String
    let onTap: () -> Void
    let onFavorite: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(alias.isEmpty ? device.name : alias)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                
                Spacer()
                
                Button(action: onFavorite) {
                    Image(systemName: isFavorite ? "star.fill" : "star")
                        .foregroundColor(isFavorite ? .yellow : .gray)
                }
            }
            
            Text("\(device.host):\(device.port)")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(12)
        .background(Color(.systemGray6))
        .cornerRadius(8)
        .onTapGesture(perform: onTap)
    }
}

/// 媒体行
struct MediaRow: View {
    let item: MediaItem
    let onTap: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            // 缩略图
            if let url = URL(string: item.thumbnailUri) {
                AsyncImage(url: url) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Image(systemName: item.isVideo ? "video" : "music.note")
                        .font(.title2)
                        .foregroundColor(.secondary)
                        .frame(width: 60, height: 60)
                        .background(Color(.systemGray5))
                }
                .frame(width: 60, height: 60)
                .cornerRadius(8)
            } else {
                Image(systemName: item.isVideo ? "video" : "music.note")
                    .font(.title2)
                    .foregroundColor(.secondary)
                    .frame(width: 60, height: 60)
                    .background(Color(.systemGray5))
                    .cornerRadius(8)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.subheadline)
                    .lineLimit(1)
                
                Text(item.folderName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
