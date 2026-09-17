# HomeMediaViewer

局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

## 功能特性

- **mDNS 设备自动发现** — 基于 Android NSD（`_hmv._tcp.`），局域网内设备自动注册与发现，无需手动输入 IP
- **HTTP + Range 流媒体服务** — 轻量内嵌 ServerSocket，支持整流/分段请求，拖动进度条自动触发 Range 206
- **跨设备播放** — 选择发现的远程设备，获取其媒体列表并直接流式播放
- **MediaStore 媒体扫描** — 自动扫描设备中的视频和音频文件（API 33+ 适配 READ_MEDIA_VIDEO/AUDIO）
- **ExoPlayer 在线播放** — 基于 Media3 ExoPlayer，支持 HTTP 流播放、加载状态、错误处理
- **前台 Service** — HTTP 服务绑定前台 Service，后台运行不被系统回收
- **零依赖服务端** — core-server 模块纯 Kotlin/JVM，无第三方 HTTP 库

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| AGP | 8.13.0 |
| Gradle | 8.7 (wrapper) |
| AndroidX Media3 (ExoPlayer) | 1.4.1 |
| minSdk | 26 |
| targetSdk / compileSdk | 35 |

## 项目结构

```
├── app/                         Android 应用模块
│   └── src/main/kotlin/com/hmv/app/
│       ├── MainActivity.kt      主界面：权限申请 + 设备列表 + 媒体列表
│       ├── PlayerActivity.kt    ExoPlayer 播放页
│       ├── MediaServerService.kt 前台 Service，承载 HTTP 服务器
│       ├── MediaScanner.kt      MediaStore 扫描视频/音频
│       ├── NsdHelper.kt         mDNS 注册与发现
│       ├── DeviceAdapter.kt     设备列表 RecyclerView 适配器
│       ├── MediaAdapter.kt      媒体列表 RecyclerView 适配器
│       ├── ContentMediaRepository.kt  content:// URI → RangeReadable 桥接
│       └── RemoteMediaClient.kt 远程设备媒体列表拉取
├── core-server/                 纯 Kotlin/JVM 模块（零 Android 依赖）
│   └── src/main/kotlin/com/hmv/server/
│       ├── HttpRangeServer.kt   核心：ServerSocket HTTP+Range 服务
│       ├── MediaRepository.kt   媒体数据源抽象 + MediaItem 模型
│       ├── FileMediaRepository.kt 文件系统实现
│       ├── RangeParser.kt       HTTP Range 头解析
│       └── RangeReadable.kt     可 seek 只读源接口
├── anaDocs/                     方案设计文档
└── prjTracker/                  项目状态记录
```

## 构建与运行

```bash
./gradlew :core-server:test      # 运行服务端单测（15 项）
./gradlew :app:assembleDebug     # 构建调试 APK
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`

## HTTP API

| 端点 | 说明 |
|------|------|
| `GET /media` | 返回 JSON 媒体列表 |
| `GET /media/{id}` | 返回整文件字节流（200） |
| `GET /media/{id}` + `Range: bytes=100-199` | 206 Partial Content |

支持 `bytes=start-`（开区间）、`bytes=-N`（后缀）、越界返回 416、HEAD 请求。

## 工作原理

```
设备 A                              设备 B
┌─────────────────────┐    LAN    ┌─────────────────────┐
│  MediaServerService │◄─────────│  NsdHelper 发现      │
│  HTTP+Range Server  │          │  点击设备 → 拉取列表  │
│  mDNS 注册          │  /media  │  ExoPlayer 流式播放   │
└─────────────────────┘          └─────────────────────┘
```

1. 每台设备启动后扫描本地媒体，开启 HTTP 服务并注册 mDNS
2. 设备间通过 mDNS 自动发现彼此
3. 点击远程设备 → 拉取 `/media` JSON 列表
4. 点击媒体项 → ExoPlayer 播放远程 URL，支持拖动（Range 206）

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | HTTP 服务端与客户端通信 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | API 33+ 媒体文件读取 |
| `READ_EXTERNAL_STORAGE` | API ≤ 32 媒体文件读取 |
| `FOREGROUND_SERVICE` | HTTP 服务后台常驻 |

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
- [ ] M5 缩略图 / 播放进度保存 / UI 打磨
- [ ] R8 混淆优化

## License

MIT
