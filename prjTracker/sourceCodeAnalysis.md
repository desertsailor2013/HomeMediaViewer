# HomeMediaViewer 源码文件分析

> 更新时间：2026-09-19

---

## 汇总

| 项目 | 数量 |
|------|------|
| 总文件数 | 54 |
| 总代码行 | 3,668 |
| Kotlin 源码 | 22 文件，2,726 行 |
| XML 资源 | 26 文件，798 行 |
| 构建配置 | 6 文件，144 行 |

---

## 1. Kotlin 源码 (app)

### 1.1 核心界面

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `MainActivity.kt` | 366 | 主界面：权限申请、媒体扫描、设备发现与列表展示、搜索筛选、分组切换、播放队列启动、收藏设备直连 |
| `PlayerActivity.kt` | 487 | 播放页：ExoPlayer 播放控制、队列播放、全屏切换、播放进度保存/恢复、网络状态监听、投屏指令接收、速度调节与持久化 |

### 1.2 服务与后台

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `MediaServerService.kt` | 136 | 前台 Service：承载 HTTP 服务器、注册 mDNS 服务、接收投屏指令并广播 |
| `MediaScanner.kt` | 93 | MediaStore 扫描视频/音频文件，提取 folderName，过滤无效文件 |
| `NsdHelper.kt` | 134 | mDNS 注册与发现：`_hmv._tcp.` 服务注册、设备发现回调 |

### 1.3 适配器

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `DeviceAdapter.kt` | 69 | 设备列表 RecyclerView 适配器：显示设备名/别名、收藏星标、点击/收藏回调 |
| `MediaAdapter.kt` | 202 | 媒体列表适配器：分组 Header + 可折叠、缩略图加载、搜索过滤、类型筛选、DiffUtil |
| `QueueAdapter.kt` | 51 | 播放队列适配器：显示队列项标题、当前播放高亮、点击跳转、移除按钮 |
| `DeviceCastAdapter.kt` | 32 | 投屏设备选择器适配器：显示设备名/IP:Port，点击选择目标设备 |

### 1.4 数据管理

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `DeviceFavoritesManager.kt` | 105 | 设备收藏管理：SharedPreferences + JSON 存储收藏列表、别名、IP:Port 更新 |
| `ContentMediaRepository.kt` | 49 | content:// URI → RangeReadable 桥接，通过 FileChannel 实现 seek |
| `RemoteMediaClient.kt` | 107 | 远程设备通信：拉取媒体列表、发送投屏指令（POST /play） |
| `PlayProgressManager.kt` | 68 | 播放进度持久化：SharedPreferences 存储位置，95% 以上自动清除 |
| `PlaybackSpeedManager.kt` | 32 | 播放速度偏好持久化：SharedPreferences 存储/恢复速度设置 |

### 1.5 工具类

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `NetworkMonitor.kt` | 67 | 网络状态监听：ConnectivityManager 回调，网络断开/恢复通知 |

---

## 2. Kotlin 源码 (core-server)

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `HttpRangeServer.kt` | 342 | 核心 HTTP 服务：ServerSocket 监听、GET/HEAD/POST 请求处理、Range 206、缩略图、投屏指令 |
| `MediaRepository.kt` | 49 | 媒体数据源抽象 + MediaItem 数据类（含 folderName） |
| `FileMediaRepository.kt` | 23 | 文件系统实现：扫描目录文件，返回 MediaItem 列表 |
| `RangeParser.kt` | 41 | HTTP Range 头解析：支持 bytes=start-end、bytes=start-、bytes=-N |
| `RangeReadable.kt` | 20 | 可 seek 只读源接口：定义 read/size/position 方法 |

---

## 3. Kotlin 测试

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `HttpRangeServerTest.kt` | 195 | HTTP 服务器单元测试：媒体列表、Range 请求、越界 416、缩略图端点 |
| `RangeParserTest.kt` | 58 | Range 解析器单元测试：各种 Range 格式、边界条件 |

---

## 4. XML 资源

### 4.1 布局文件

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `activity_main.xml` | 148 | 主界面布局：设备列表、搜索框、筛选按钮、媒体列表、分组切换 |
| `activity_player.xml` | 97 | 播放页布局：PlayerView、队列按钮、速度按钮、全屏按钮、投屏按钮 |
| `item_media.xml` | 41 | 媒体列表项：缩略图、标题、副标题 |
| `item_device.xml` | 37 | 设备列表项：设备名、收藏按钮 |
| `item_device_selector.xml` | 38 | 投屏设备选择项：设备图标、名称、IP:Port |
| `item_folder_header.xml` | 32 | 分组 Header：展开/折叠箭头、文件夹名、媒体数量 |
| `item_queue.xml` | 47 | 队列列表项：序号、标题、副标题、移除按钮 |
| `sheet_queue.xml` | 32 | 队列底部弹窗：RecyclerView + 空状态提示 |
| `sheet_device_selector.xml` | 32 | 设备选择器弹窗：RecyclerView + 空状态提示 |

### 4.2 值资源

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `strings.xml` | 59 | 字符串资源：所有 UI 文本 |
| `colors.xml` | 23 | 颜色定义：品牌色、背景、文本、筛选按钮色 |
| `values-night/colors.xml` | 22 | 暗色模式颜色覆盖 |
| `themes.xml` | 7 | 主题定义 |

### 4.3 配置文件

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `AndroidManifest.xml` | 37 | 应用清单：权限声明、Activity/Service 注册 |
| `network_security_config.xml` | 11 | 网络安全配置：限定 HTTP 明文仅允许局域网 |

### 4.4 Drawable 资源

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `ic_launcher.xml` | 16 | 应用启动图标 |
| `ic_arrow_down.xml` | 11 | 分组展开箭头 |
| `ic_arrow_right.xml` | 11 | 分组折叠箭头 |
| `ic_cast_device.xml` | 14 | 投屏设备图标 |
| `ic_close.xml` | 11 | 关闭/移除按钮 |
| `ic_fullscreen_enter.xml` | 11 | 进入全屏图标 |
| `ic_fullscreen_exit.xml` | 11 | 退出全屏图标 |
| `ic_queue.xml` | 11 | 播放队列图标 |
| `ic_speed.xml` | 17 | 播放速度图标 |
| `bg_search.xml` | 6 | 搜索框背景 |

### 4.5 Mipmap 资源

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `mipmap-anydpi-v26/ic_launcher.xml` | 16 | 自适应启动图标配置 |

---

## 5. 构建配置

| 文件 | 行数 | 功能说明 |
|------|------|----------|
| `app/build.gradle.kts` | 51 | 应用模块构建配置：依赖、签名、R8 混淆 |
| `core-server/build.gradle.kts` | 19 | 服务端模块构建配置：纯 Kotlin/JVM |
| `build.gradle.kts` | 6 | 根项目构建配置 |
| `settings.gradle.kts` | 23 | 项目模块包含 |
| `gradle/libs.versions.toml` | 28 | 版本目录：依赖版本统一管理 |
| `app/proguard-rules.pro` | 17 | R8 混淆规则：Media3/Coil/NSD/JSON 保留 |

---

## 6. 模块分布

| 模块 | Kotlin | XML | 测试 | 配置 | 合计 |
|------|--------|-----|------|------|------|
| app (main) | 16 | 26 | 0 | 2 | 44 |
| core-server (main) | 5 | 0 | 0 | 1 | 6 |
| core-server (test) | 0 | 0 | 2 | 0 | 2 |
| 根目录 | 0 | 0 | 0 | 4 | 4 |
| **合计** | **21** | **26** | **2** | **7** | **56** |

---

## 7. 代码量 Top 10

| 排名 | 文件 | 行数 |
|------|------|------|
| 1 | `PlayerActivity.kt` | 487 |
| 2 | `MainActivity.kt` | 366 |
| 3 | `HttpRangeServer.kt` | 342 |
| 4 | `MediaAdapter.kt` | 202 |
| 5 | `HttpRangeServerTest.kt` | 195 |
| 6 | `activity_main.xml` | 148 |
| 7 | `MediaServerService.kt` | 136 |
| 8 | `NsdHelper.kt` | 134 |
| 9 | `RemoteMediaClient.kt` | 107 |
| 10 | `DeviceFavoritesManager.kt` | 105 |
