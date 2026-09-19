import Foundation
import Photos

/// 本地媒体扫描器
class MediaScanner: ObservableObject {
    @Published var mediaItems: [MediaItem] = []
    @Published var isScanning: Bool = false
    
    /// 扫描所有媒体
    func scanAll() {
        isScanning = true
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            var items: [MediaItem] = []
            
            // 扫描视频
            items += self?.scanVideos() ?? []
            
            // 扫描音频
            items += self?.scanAudios() ?? []
            
            DispatchQueue.main.async {
                self?.mediaItems = items
                self?.isScanning = false
            }
        }
    }
    
    /// 扫描视频
    private func scanVideos() -> [MediaItem] {
        var items: [MediaItem] = []
        
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        
        let videos = PHAsset.fetchAssets(with: .video, options: options)
        
        videos.enumerateObjects { asset, _, _ in
            let resource = PHAssetResource.assetResources(for: asset).first
            let fileName = resource?.originalFilename ?? "未知"
            let fileSize = resource?.value(forKey: "fileSize") as? Int64 ?? 0
            
            let item = MediaItem(
                id: asset.localIdentifier,
                title: fileName,
                mimeType: self.getMimeType(for: fileName),
                size: fileSize,
                relativePath: "",
                thumbnailUri: "",
                folderName: "视频"
            )
            items.append(item)
        }
        
        return items
    }
    
    /// 扫描音频
    private func scanAudios() -> [MediaItem] {
        var items: [MediaItem] = []
        
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        
        let audios = PHAsset.fetchAssets(with: .audio, options: options)
        
        audios.enumerateObjects { asset, _, _ in
            let resource = PHAssetResource.assetResources(for: asset).first
            let fileName = resource?.originalFilename ?? "未知"
            let fileSize = resource?.value(forKey: "fileSize") as? Int64 ?? 0
            
            let item = MediaItem(
                id: asset.localIdentifier,
                title: fileName,
                mimeType: self.getMimeType(for: fileName),
                size: fileSize,
                relativePath: "",
                thumbnailUri: "",
                folderName: "音频"
            )
            items.append(item)
        }
        
        return items
    }
    
    /// 获取 MIME 类型
    private func getMimeType(for fileName: String) -> String {
        let ext = (fileName as NSString).pathExtension.lowercased()
        switch ext {
        case "mp4", "mov", "avi", "mkv", "m4v":
            return "video/\(ext)"
        case "mp3", "m4a", "aac", "wav", "flac":
            return "audio/\(ext)"
        default:
            return "application/octet-stream"
        }
    }
    
    /// 获取缩略图
    func getThumbnail(for mediaId: String, completion: @escaping (Data?) -> Void) {
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [mediaId])
        guard let asset = fetchResult.firstObject else {
            completion(nil)
            return
        }
        
        let options = PHImageRequestOptions()
        options.deliveryMode = .fastFormat
        options.isSynchronous = false
        options.resizeMode = .fast
        
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: CGSize(width: 168, height: 168),
            contentMode: .aspectFill,
            options: options
        ) { image, _ in
            completion(image?.jpegData(compressionQuality: 0.5))
        }
    }
    
    /// 请求相册权限
    func requestAuthorization(completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization { status in
            DispatchQueue.main.async {
                completion(status == .authorized || status == .limited)
            }
        }
    }
}
