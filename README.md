# HomeMediaViewer

局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

## 功能特性

- **mDNS 设备自动发现** — 基于 Android NSD（`_hmv._tcp.`），局域网内设备自动注册与发现，无需手动输入 IP
- **HTTP + Range 流媒体服务** — 轻量内嵌 ServerSocket，支持整流/分段请求，拖动进度条自动触发 Range 206
- **跨设备播放** — 选择发现的远程设备，获取其媒体列表并直接流式播放
- **MediaStore 媒体扫描** — 自动扫描设备中的视频和音频文件（API 33+ 适配 READ_MEDIA_VIDEO/AUDIO）
- **ExoPlayer 在线播放** — 基于 Media3 ExoPlayer，支持 HTTP 流播放、加载状态、错误处理
- **缩略图展示** — 视频帧 / 音频封面自动生成，Coil 异步加载
- **播放进度保存** — 自动记录播放位置，再次打开自动续播
- **搜索与筛选** — 按文件名搜索，按类型（全部/视频/音频）筛选
- **深色模式** — 支持亮色/暗色主题自动切换
- **横屏全屏播放** — 进入播放页自动横屏沉浸式，支持手动切换
- **网络状态感知** — 播放页网络断开提示，设备离线自动切回本地
- **前台 Service** — HTTP 服务绑定前台 Service，后台运行不被系统回收
- **零依赖服务端** — core-server 模块纯 Kotlin/JVM，无第三方 HTTP 库
- **播放队列 / 连续播放** — 选择多个媒体项加入队列，自动连播，队列指示器（第 X/Y 项）
- **媒体分组浏览** — 按文件夹名分组显示，一键切换分组/扁平视图
- **设备别名与收藏** — 自定义别名（如"客厅电视"），星标收藏常用设备，收藏设备优先显示
- **跨设备投屏控制** — 将本机播放内容推送到指定设备继续播放，支持断点续播
- **播放速度调节** — 0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x 循环切换，长按快速重置
- **分组可折叠** — 点击分组 Header 展开/折叠文件夹，显示媒体数量统计
- **队列管理面板** — 底部弹窗查看完整队列，支持点击跳转、移除队列项
- **设备选择器** — 播放页投屏按钮弹出设备列表，选择目标设备投屏，显示投屏状态反馈
- **收藏设备自动重连** — 启动时直连收藏设备 IP:Port，减少 mDNS 等待时间
- **速度偏好持久化** — 播放速度自动保存，下次打开自动应用

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| AGP | 8.13.0 |
| Gradle | 8.7 (wrapper) |
| AndroidX Media3 (ExoPlayer) | 1.4.1 |
| Coil | 2.7.0 |
| Material Design | 1.12.0 |
| minSdk | 26 |
| targetSdk / compileSdk | 35 |

## 项目结构

```
├── app/                         Android 应用模块
│   └── src/main/kotlin/com/hmv/app/
│       ├── MainActivity.kt      主界面：权限申请 + 设备列表 + 搜索筛选 + 分组切换 + 收藏设备重连
│       ├── PlayerActivity.kt    ExoPlayer 播放页（队列/全屏/进度保存/网络检测/投屏接收/变速/速度持久化）
│       ├── MediaServerService.kt 前台 Service，承载 HTTP 服务器 + 投屏指令广播
│       ├── MediaScanner.kt      MediaStore 扫描视频/音频（含 folderName 提取）
│       ├── NsdHelper.kt         mDNS 注册与发现
│       ├── DeviceAdapter.kt     设备列表适配器（收藏星标 + 别名显示）
│       ├── MediaAdapter.kt      媒体列表适配器（分组 Header + 可折叠 + 缩略图 + 搜索过滤 + DiffUtil）
│       ├── DeviceFavoritesManager.kt 设备收藏与别名持久化（SharedPreferences + JSON + IP:Port 更新）
│       ├── QueueAdapter.kt      播放队列适配器（跳转 + 移除）
│       ├── DeviceCastAdapter.kt 投屏设备选择器适配器
│       ├── ContentMediaRepository.kt  content:// URI → RangeReadable 桥接
│       ├── RemoteMediaClient.kt 远程设备媒体列表拉取 + 投屏指令发送
│       ├── PlayProgressManager.kt 播放进度持久化
│       ├── PlaybackSpeedManager.kt 播放速度偏好持久化
│       └── NetworkMonitor.kt    网络状态监听
├── core-server/                 纯 Kotlin/JVM 模块（零 Android 依赖）
│   └── src/main/kotlin/com/hmv/server/
│       ├── HttpRangeServer.kt   核心：ServerSocket HTTP+Range 服务 + POST /play
│       ├── MediaRepository.kt   媒体数据源抽象 + MediaItem 模型（含 folderName）
│       ├── FileMediaRepository.kt 文件系统实现
│       ├── RangeParser.kt       HTTP Range 头解析
│       └── RangeReadable.kt     可 seek 只读源接口
├── anaDocs/                     方案设计文档
└── prjTracker/                  项目状态记录
```

## 构建与运行

```bash
./gradlew :core-server:test      # 运行服务端单测（22 项）
./gradlew :app:assembleDebug     # 构建调试 APK
./gradlew :app:assembleRelease   # 构建 Release APK（含 R8 混淆）
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`

## HTTP API

| 端点 | 说明 |
|------|------|
| `GET /media` | 返回 JSON 媒体列表 |
| `GET /media/{id}` | 返回整文件字节流（200） |
| `GET /media/{id}` + `Range: bytes=100-199` | 206 Partial Content |
| `GET /media/{id}/thumbnail` | 返回缩略图（JPEG/PNG） |
| `POST /play` | 投屏控制，JSON body：`{"mediaId":"...","title":"...","position":0}` |

支持 `bytes=start-`（开区间）、`bytes=-N`（后缀）、越界返回 416、HEAD 请求。

## 工作原理

```
设备 A                              设备 B
┌─────────────────────┐    LAN    ┌─────────────────────┐
│  MediaServerService │◄─────────│  NsdHelper 发现      │
│  HTTP+Range Server  │          │  设备选择器 → 投屏    │
│  mDNS 注册          │  /media  │  ExoPlayer 流式播放   │
│  POST /play         │  /play   │  队列管理 / 分组浏览   │
└─────────────────────┘          └─────────────────────┘
```

1. 每台设备启动后扫描本地媒体，开启 HTTP 服务并注册 mDNS
2. 设备间通过 mDNS 自动发现彼此，收藏设备直连加速
3. 点击远程设备 → 拉取 `/media` JSON 列表，支持分组浏览
4. 点击媒体项 → ExoPlayer 播放远程 URL，支持队列播放、拖动（Range 206）
5. 投屏 → 点击投屏按钮 → 选择目标设备 → `POST /play` 发送播放指令

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | HTTP 服务端与客户端通信 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | API 33+ 媒体文件读取 |
| `READ_EXTERNAL_STORAGE` | API ≤ 32 媒体文件读取 |
| `FOREGROUND_SERVICE` | HTTP 服务后台常驻 |

## 安全特性

- 签名密码存储在 `local.properties`（已 gitignore），不入库
- 网络安全配置限定 HTTP 明文仅允许局域网访问
- 禁用 `allowBackup` 防止 adb 提取应用数据
- R8 混淆 + 资源缩减（Release 构建）

## 环境要求

- OpenJDK 17+
- Android SDK（compileSdk 35）
- 首次构建需联网下载依赖

## 路线图

- [x] M1 HTTP+Range 媒体服务 + 媒体扫描
- [x] M2 ExoPlayer 播放端
- [x] M3 mDNS 设备发现
- [x] M4 跨设备播放
- [x] 前台 Service + 签名配置
- [x] M5 缩略图 / 播放进度保存 / 搜索筛选 / 深色模式 / 全屏播放 / 网络感知
- [x] R8 混淆优化
- [x] V2-1 播放队列 / 连续播放
- [x] V2-2 媒体分组浏览
- [x] V2-3 设备别名与收藏
- [x] V2-4 跨设备投屏控制
- [x] V2-5 播放速度调节
- [x] V2 优化：分组可折叠 + 队列管理面板 + 设备选择器 + 收藏自动重连 + 速度持久化
- [ ] CI/CD 自动构建

## License

MIT
