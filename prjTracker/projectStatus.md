# HomeMediaViewer — 工程状态记录

> 更新时间：2026-09-18
> 目标：局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。后期扩展 iOS / 鸿蒙。

---

## 1. 当前进度总览

| 里程碑 | 状态 | 说明 |
|-------|------|------|
| M1 HTTP+Range 媒体服务 + 媒体扫描 | ✅ 完成 | 服务端模块 + 单测 20 项全过；APK 打包成功 |
| M2 ExoPlayer 播放端 | ✅ 完成 | 进度保存 + 横屏全屏播放 |
| M3 mDNS 设备发现 | ✅ 完成 | 局域网自动发现 |
| M4 跨设备播放 | ✅ 完成 | 含网络断开检测 |
| M5 缩略图/进度/搜索/深色/全屏/离线提示 | ✅ 完成 | 2026-09-18 全部实现 |

---

## 2. 已实现功能

### 2.1 HTTP+Range 媒体服务（core-server）
- **API**：
  - `GET /media` → JSON 媒体列表（含 thumbnailUri）
  - `GET /media/{id}` → 整文件字节流（200）
  - `GET /media/{id}` + `Range: bytes=100-199` → 206 Partial Content
  - `GET /media/{id}/thumbnail` → 缩略图（JPEG/PNG）
  - 支持 `bytes=start-`（开区间）、`bytes=-N`（后缀）、越界返回 416
  - HEAD 请求返回头无体
- **实现要点**：
  - 轻量，无第三方 HTTP 库，直接 ServerSocket + 线程池
  - `RangeReadable` 抽象支持 seek（普通文件用 RandomAccessFile，Android 端用 FileChannel）

### 2.2 媒体扫描与缩略图
- `MediaScanner`：MediaStore 扫描 VIDEO/AUDIO 集合，过滤 size<=0，自动生成 thumbnailUri
- `ContentMediaRepository`：content:// URI 通过 AssetFileDescriptor.dup() + FileChannel.position() 实现 seek
- `MediaAdapter`：Coil 异步加载缩略图，支持视频帧解码

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

---

## 3. 验证结果

- core-server 单测 **20/20 通过**：
  - RangeParserTest：10 项
  - HttpRangeServerTest：12 项（含 4 项 thumbnail 端点测试）
- `app:compileDebugKotlin` 编译通过

---

## 4. 下一步计划

- [ ] R8 混淆优化（缩减 APK 体积）
- [ ] CI/CD 配置（GitHub Actions 自动构建）
- [ ] 单元测试补充（MediaScanner、NsdHelper、NetworkMonitor 等）

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
