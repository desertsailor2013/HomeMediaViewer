# HomeMediaViewer — 工程状态记录

> 更新时间：2026-09-19
> 目标：局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。后期扩展 iOS / 鸿蒙。

---

## 1. 当前进度总览

| 里程碑 | 版本 | 状态 | 说明 |
|-------|------|------|------|
| M1 HTTP+Range 媒体服务 + 媒体扫描 | v0.1 | ✅ 完成 | 服务端模块 + 单测 20 项全过 |
| M2 ExoPlayer 播放端 | v0.1 | ✅ 完成 | 进度保存 + 横屏全屏播放 |
| M3 mDNS 设备发现 | v0.1 | ✅ 完成 | 局域网自动发现 |
| M4 跨设备播放 | v0.1 | ✅ 完成 | 含网络断开检测 |
| M5 缩略图/进度/搜索/深色/全屏/离线提示 | v0.1 | ✅ 完成 | 2026-09-18 全部实现 |
| 代码质量优化（安全/泄漏/性能/规范） | v0.1 | ✅ 完成 | 19 项改进全部修复（2026-09-19） |
| **V2 功能增强** | **v0.2** | **⏳ 规划中** | 播放队列 / 媒体分组 / 设备收藏 / 投屏控制 / 播放变速 |

---

## 2. 已实现功能（v0.1）

### 2.1 HTTP+Range 媒体服务（core-server）
- **API**：
  - `GET /media` → JSON 媒体列表（含 thumbnailUri）
  - `GET /media/{id}` → 整文件字节流（200）
  - `GET /media/{id}` + `Range: bytes=100-199` → 206 Partial Content
  - `GET /media/{id}/thumbnail` → 缩略图（JPEG/PNG）
  - 支持 `bytes=start-`（开区间）、`bytes=-N`（后缀）、越界返回 416
  - HEAD 请求返回头无体
- **实现要点**：
  - 轻量，无第三方 HTTP 库，直接 ServerSocket + 有界线程池
  - `RangeReadable` 抽象支持 seek（普通文件用 RandomAccessFile，Android 端用 FileChannel）

### 2.2 媒体扫描与缩略图
- `MediaScanner`：MediaStore 扫描 VIDEO/AUDIO 集合，过滤 size<=0，自动生成 thumbnailUri
- `ContentMediaRepository`：content:// URI 通过 AssetFileDescriptor.dup() + FileChannel.position() 实现 seek
- `MediaAdapter`：Coil 异步加载缩略图，支持视频帧解码，DiffUtil 增量刷新

### 2.3 ExoPlayer 播放端
- `PlayerActivity`：Media3 ExoPlayer 播放，含 loading/错误态
- **播放进度保存**：PlayProgressManager 基于 SharedPreferences，95% 以上自动清除
- **横屏全屏**：进入自动横屏沉浸式，全屏按钮切换，configChanges 防旋转重建

### 2.4 设备发现与跨设备播放
- `NsdHelper`：mDNS 注册（`_hmv._tcp.`）与发现
- `RemoteMediaClient`：拉取远程设备媒体列表 + 缩略图 URL
- 设备离线自动切回本地媒体

### 2.5 搜索与筛选
- EditText 实时搜索（按文件名）
- 全部/视频/音频类型筛选按钮
- 搜索 + 类型可叠加使用

### 2.6 深色模式
- `values/colors.xml` 定义 14 个主题色
- `values-night/colors.xml` 暗色模式覆盖
- 所有 layout 使用主题色引用

### 2.7 网络状态感知
- `NetworkMonitor`：基于 ConnectivityManager 监听网络连接/断开
- 播放页：远程播放时实时监听，断开显示错误，恢复自动重试
- 主页面：网络断开 Toast 提示，设备离线自动移除

### 2.8 安全与工程化
- 签名密码存储在 `local.properties`（gitignore）
- `network_security_config.xml` 限定 HTTP 明文仅允许局域网
- `allowBackup=false` 防止 adb 提取数据
- R8 混淆 + 资源缩减（Release 构建）
- ProGuard 规则覆盖 Media3/Coil/NSD/JSON

---

## 3. 验证结果

- core-server 单测 **20/20 通过**：
  - RangeParserTest：10 项
  - HttpRangeServerTest：12 项（含 4 项 thumbnail 端点测试）
- `app:compileDebugKotlin` 编译通过
- `app:assembleRelease` R8 混淆构建通过

---

## 4. V2 功能增强规划

| # | 功能 | 优先级 | 说明 |
|---|------|--------|------|
| V2-1 | 播放队列 / 连续播放 | 高 | 添加到队列 + 自动连播，长视频追剧场景刚需 |
| V2-2 | 媒体分组浏览 | 高 | 按文件夹 / 日期 / 类型分组，大媒体库定位更高效 |
| V2-3 | 设备别名与收藏 | 中 | 自定义别名（"客厅电视"）+ 收藏常用设备，省去重新发现等待 |
| V2-4 | 跨设备投屏控制 | 中 | 将本机播放内容推送到指定设备继续播放，断点续播 |
| V2-5 | 播放速度调节 | 低 | 0.5x / 1.25x / 1.5x / 2x 变速，网课/会议录像场景 |

### V2 技术要点

**V2-1 播放队列**
- `PlayerActivity` 支持接收 `List<MediaItem>` 列表
- ExoPlayer `MediaSource` 拼接为 `ConcatenatingMediaSource`
- UI：底部迷你播放条 + 队列列表页面

**V2-2 媒体分组**
- `MediaScanner` 提取 `relativePath` 中的文件夹名
- `MediaAdapter` 支持分组 header + 展开/折叠
- UI：顶部 Tab 或侧滑菜单切换分组视图

**V2-3 设备收藏**
- `SharedPreferences` 存储收藏设备列表（name + host + port）
- `DeviceAdapter` 增加收藏按钮（星标）
- 启动时优先加载收藏设备，发现后自动匹配

**V2-4 投屏控制**
- 新增 `POST /play` API，推送播放 URL 到目标设备
- 目标设备 `PlayerActivity` 支持接收远程播放指令
- 断点续播：传递当前播放位置

**V2-5 播放变速**
- ExoPlayer `setPlaybackSpeed()` 已原生支持
- UI：播放控制栏增加变速按钮 + 当前倍速显示

---

## 5. 环境说明

| 项 | 值 |
|----|----|
| Java | OpenJDK 17 |
| Android SDK | compileSdk 35 |
| Gradle | wrapper 8.7 |
| AGP | 8.13.0 |
| Kotlin | 1.9.24 |
| Coil | 2.7.0 |
