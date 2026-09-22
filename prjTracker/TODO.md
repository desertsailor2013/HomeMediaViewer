# HomeMediaViewer — 待办任务清单

> 创建时间：2026-09-21
> 说明：记录待实现的功能优化项

---

## 待办任务

| 序号 | 任务 | 预计工期 | 优先级 | 状态 |
|------|------|----------|--------|------|
| 1 | 缩略图生成完善 | 6 天 | ⭐⭐⭐ | ⏳ 待开始 |
| 2 | 媒体元数据深度提取 | 7 天 | ⭐⭐⭐ | ⏳ 待开始 |
| 3 | 大文件播放内存优化 | 3 天 | ⭐⭐ | ⏳ 待开始 |
| 4 | DLNA/AirPlay 投屏 | 17-26 天 | ⭐⭐ | ⏳ 待开始 |
| 5 | GitHub Actions CI/CD | 3.5 天 | ⭐ | ⏳ 待开始 |

**总预估工期：36.5-45.5 天**

---

## 任务详情

### 1. 缩略图生成完善（6 天）

**目标：** 各平台实现视频帧/音频封面提取

**实现内容：**
- Android: MediaMetadataRetriever + FFmpeg
- iOS: AVAssetImageGenerator
- HarmonyOS: AVMetadataExtractor
- 缓存系统（LRU + 磁盘）
- 懒加载与预加载策略

**预估时间：** 6 天

---

### 2. 媒体元数据深度提取（7 天）

**目标：** 提取完整技术元数据（时长/分辨率/码率/编码等）

**实现内容：**
- Android: MediaMetadataRetriever + FFmpeg
- iOS: AVAsset 解析
- HarmonyOS: AVMetadataExtractor
- Room 缓存数据库
- 批量提取与 UI 集成

**预估时间：** 7 天

---

### 3. 大文件播放内存优化（3 天）

**目标：** 优化大文件播放时的内存占用

**实现内容：**
- HTTP Range 分段传输优化
- ExoPlayer/AVPlayer 缓冲区配置
- 缩略图降采样
- 媒体列表分页加载
- 内存监控与预警
- 资源释放策略

**预估时间：** 3 天

---

### 4. DLNA/AirPlay 投屏（17-26 天）

**目标：** 实现真正的投屏到电视/音箱

**实现内容：**
- DLNA 设备发现（Cling 库）
- DLNA 媒体投射
- AirPlay 设备发现
- AirPlay 媒体投射
- Google Cast 集成
- 统一投屏接口
- 投屏状态同步

**预估时间：** 17-26 天

---

### 5. GitHub Actions CI/CD（3.5 天）

**目标：** 自动构建 APK/HAP/IPA 并发布

**实现内容：**
- Android 构建 workflow
- iOS 构建 workflow
- HarmonyOS 构建 workflow
- Web 部署到 GitHub Pages
- 发布自动化（tag 触发）
- 签名密钥配置

**预估时间：** 3.5 天

---

## 依赖关系

```
缩略图生成 ──────┐
                 │
媒体元数据提取 ──┼──► DLNA/AirPlay 投屏
                 │
大文件内存优化 ──┘
                 
GitHub Actions CI/CD（独立）
```

---

## 里程碑规划

| 阶段 | 任务 | 预计时间 |
|------|------|----------|
| V3.1 | 缩略图生成完善 | 2026-09-22 ~ 2026-09-28 |
| V3.2 | 媒体元数据深度提取 | 2026-09-29 ~ 2026-10-05 |
| V3.3 | 大文件播放内存优化 | 2026-10-06 ~ 2026-10-08 |
| V4.0 | DLNA/AirPlay 投屏 | 2026-10-09 ~ 2026-11-04 |
| V3.1 | GitHub Actions CI/CD | 2026-09-22 ~ 2026-09-25 |
