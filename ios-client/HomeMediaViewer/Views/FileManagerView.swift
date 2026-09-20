import SwiftUI
import UniformTypeIdentifiers

/// 文件管理页面
struct FileManagerView: View {
    let deviceAddress: String
    
    @State private var mediaItems: [MediaItem] = []
    @State private var status: String = "加载中..."
    @State private var showUploadPicker = false
    @State private var showRenameAlert = false
    @State private var showDeleteAlert = false
    @State private var showCreateFolderAlert = false
    @State private var selectedItem: MediaItem?
    @State private var newName: String = ""
    @State private var folderName: String = ""
    
    private let remoteClient = RemoteMediaClient()
    
    var body: some View {
        VStack(spacing: 0) {
            // 标题栏
            HStack {
                Text("文件管理")
                    .font(.headline)
                    .foregroundColor(.white)
                
                Spacer()
                
                Button(action: { showCreateFolderAlert = true }) {
                    Image(systemName: "plus")
                        .foregroundColor(.white)
                }
            }
            .padding()
            .background(Color.blue)
            
            // 状态
            Text(status)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .padding(.top, 8)
            
            // 文件列表
            List {
                ForEach(mediaItems) { item in
                    HStack(spacing: 12) {
                        Image(systemName: item.isVideo ? "video" : item.isAudio ? "music.note" : "doc")
                            .font(.title2)
                            .foregroundColor(.secondary)
                            .frame(width: 48, height: 48)
                            .background(Color(.systemGray5))
                            .cornerRadius(8)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                                .font(.subheadline)
                                .lineLimit(1)
                            
                            Text(item.formattedSize)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 16) {
                            Button(action: {
                                selectedItem = item
                                newName = item.title
                                showRenameAlert = true
                            }) {
                                Image(systemName: "pencil")
                                    .foregroundColor(.blue)
                            }
                            
                            Button(action: {
                                selectedItem = item
                                showDeleteAlert = true
                            }) {
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                            }
                        }
                    }
                }
            }
            .listStyle(PlainListStyle())
            
            // 底部按钮
            Button(action: { showUploadPicker = true }) {
                Text("上传文件")
                    .font(.subheadline)
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color(.systemGray6))
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            loadFiles()
        }
        .fileImporter(
            isPresented: $showUploadPicker,
            allowedContentTypes: [.item],
            allowsMultipleSelection: false
        ) { result in
            handleFileImport(result)
        }
        .alert("重命名", isPresented: $showRenameAlert) {
            TextField("新名称", text: $newName)
            Button("确定") { renameItem() }
            Button("取消", role: .cancel) { }
        }
        .alert("确认删除", isPresented: $showDeleteAlert) {
            Text("确定要删除 \"\(selectedItem?.title ?? \"\"}\" 吗？")
            Button("删除", role: .destructive) { deleteItem() }
            Button("取消", role: .cancel) { }
        }
        .alert("新建文件夹", isPresented: $showCreateFolderAlert) {
            TextField("文件夹名称", text: $folderName)
            Button("创建") { createFolder() }
            Button("取消", role: .cancel) { }
        }
    }
    
    private func loadFiles() {
        status = "加载中..."
        Task {
            let items = await remoteClient.fetchMediaList(deviceAddress: deviceAddress)
            await MainActor.run {
                mediaItems = items
                status = items.isEmpty ? "空文件夹" : "\(items.count) 个文件"
            }
        }
    }
    
    private func handleFileImport(_ result: Result<[URL], Error>) {
        guard let urls = try? result.get(),
              let url = urls.first else { return }
        
        Task {
            let data = try? Data(contentsOf: url)
            let fileName = url.lastPathComponent
            
            if let data = data {
                let result = await remoteClient.uploadFile(
                    deviceAddress: deviceAddress,
                    fileData: data,
                    fileName: fileName
                )
                
                await MainActor.run {
                    if result.success {
                        loadFiles()
                    } else {
                        status = "上传失败: \(result.message ?? "Unknown error")"
                    }
                }
            }
        }
    }
    
    private func renameItem() {
        guard let item = selectedItem, !newName.isEmpty else { return }
        
        Task {
            let result = await remoteClient.renameFile(
                deviceAddress: deviceAddress,
                mediaId: item.id,
                newName: newName
            )
            
            await MainActor.run {
                if result.success {
                    loadFiles()
                } else {
                    status = "重命名失败: \(result.message ?? "Unknown error")"
                }
            }
        }
    }
    
    private func deleteItem() {
        guard let item = selectedItem else { return }
        
        Task {
            let result = await remoteClient.deleteFile(
                deviceAddress: deviceAddress,
                mediaId: item.id
            )
            
            await MainActor.run {
                if result.success {
                    loadFiles()
                } else {
                    status = "删除失败: \(result.message ?? "Unknown error")"
                }
            }
        }
    }
    
    private func createFolder() {
        guard !folderName.isEmpty else { return }
        
        Task {
            let result = await remoteClient.createFolder(
                deviceAddress: deviceAddress,
                name: folderName
            )
            
            await MainActor.run {
                if result.success {
                    loadFiles()
                } else {
                    status = "创建失败: \(result.message ?? "Unknown error")"
                }
                folderName = ""
            }
        }
    }
}

struct FileManagerView_Previews: PreviewProvider {
    static var previews: some View {
        FileManagerView(deviceAddress: "http://192.168.1.100:8080")
    }
}
