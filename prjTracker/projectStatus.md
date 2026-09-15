# HomeMediaViewer — 工程状态记录

> 更新时间：2026-08-12
> 目标：局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。后期扩展 iOS / 鸿蒙。

---

## 1. 当前进度总览

| 里程碑 | 状态 | 说明 |
|-------|------|------|
| M1 HTTP+Range 媒体服务 + 媒体扫描 | ✅ 完成 | 服务端模块 + 单测 18 项全过；APK 打包成功 |
| M2 ExoPlayer 播放端 | 🟡 代码完成/构建通过/待真机验证 | Media3 ExoPlayer 播放页 + 列表已实现，`assembleDebug` 成功；尚未在真机/模拟器验证拖动 |
| M3 mDNS/UDP 设备发现 | ⏳ 未开始 | |
| M4 URL 拼接 + 远程播放 | ⏳ 未开始 | 核心闭环（当前仅 127.0.0.1 本机回环） |
| M5 缩略图/进度保存/权限打磨 | 🟡 权限部分完成 | API33+ READ_MEDIA_VIDEO/AUDIO 已补全，其余未开始 |

---

## 2. 工程结构

```
D:\workspace\aiSrcFac\HomeMediaViewer\
├── settings.gradle.kts          # 含 :app、:core-server 两个模块
├── build.gradle.kts             # 根构建，声明 AGP/Kotlin 插件
├── gradle.properties            # AndroidX 开启等
├── local.properties             # sdk.dir=D:\rdtools\Android\Sdk
├── gradlew / gradlew.bat        # wrapper 8.7（已生成）
├── gradle/
│   ├── libs.versions.toml       # 版本目录：AGP 8.5.2, Kotlin 1.9.24, compileSdk 35
│   └── wrapper/                 # gradle-wrapper.jar + properties
├── core-server/                 # 纯 Kotlin/JVM 模块，零 Android 依赖
│   └── src/main/kotlin/com/hmv/server/
│       ├── MediaRepository.kt   # 媒体数据源抽象 + openForRange 默认实现
│       ├── MediaItem.kt         # 数据模型(id/title/mimeType/size/relativePath)
│       ├── RangeParser.kt       # HTTP Range 头解析
│       ├── RangeReadable.kt     # 可 seek 只读源接口 + FileRangeReadable
│       ├── FileMediaRepository.kt # 文件系统实现
│       └── HttpRangeServer.kt   # 核心：ServerSocket 轻量 HTTP+Range 服务
│   └── src/test/kotlin/com/hmv/server/
│       ├── RangeParserTest.kt   # 11 项解析测试
│       └── HttpRangeServerTest.kt # 8 项真实 HTTP 端到端测试
├── app/                         # Android 应用模块
│   ├── build.gradle.kts         # applicationId com.hmv.app, minSdk 26, target 35
│   └── src/main/
│       ├── AndroidManifest.xml  # INTERNET + ACCESS_NETWORK_STATE + 媒体读取权限
│       ├── kotlin/com/hmv/app/
│       │   ├── MediaScanner.kt          # MediaStore 扫描视频/音频
│       │   ├── ContentMediaRepository.kt # content:// URI → 可 seek RangeReadable
│       │   ├── MediaAdapter.kt          # 媒体列表 RecyclerView 适配器（M2）
│       │   ├── PlayerActivity.kt        # Media3 ExoPlayer 播放页（M2）
│       │   └── MainActivity.kt          # 列表 + 启动服务 + 显示本机 IP/URL
│       └── res/                 # layout(含 player/item_media)/values/drawable/mipmap 图标
├── anaDocs/                     # 方案分析文档（MD/DOCX/PDF）
└── prjTracker/projectStatus.md  # 本文件
```

---

## 3. 已实现功能

### 3.1 HTTP+Range 媒体服务（core-server，已验证）
- **API**：
  - `GET /media` → JSON 媒体列表
  - `GET /media/{id}` → 整文件字节流（200）
  - `GET /media/{id}` + `Range: bytes=100-199` → 206 Partial Content
  - 支持 `bytes=start-`（开区间）、`bytes=-N`（后缀）、越界返回 416
  - HEAD 请求返回头无体
- **实现要点**：
  - 轻量，无第三方 HTTP 库，直接 ServerSocket + 线程池
  - `RangeReadable` 抽象支持 seek（普通文件用 RandomAccessFile，Android 端用 FileChannel）
  - JSON 响应含 id/title/mimeType/size/path
  - 每连接独立处理，`Connection: close`，64KB 缓冲区

### 3.2 媒体扫描（app，未端到端验证）
- `MediaScanner`：MediaStore 扫描 VIDEO/AUDIO 集合，过滤 size<=0
- `ContentMediaRepository`：content:// URI 通过 AssetFileDescriptor.dup() + FileChannel.position() 实现 seek
- `MainActivity`：申请权限（API 33+ 用 READ_MEDIA_VIDEO/AUDIO，API<33 用 READ_EXTERNAL_STORAGE）、启动服务、列出本机 IPv4 访问地址

### 3.3 ExoPlayer 播放端（M2，构建通过、待真机验证）
- `PlayerActivity`：Media3 ExoPlayer 播放 `http://ip:端口/media/{id}`，含 loading/错误态，onStop 释放播放器
- `MediaAdapter` + `item_media.xml`：RecyclerView 展示标题 + 类型/大小，点击进入播放页
- `MainActivity`：点击条目经 `server.port` 拼 127.0.0.1 本机 URL 播放（M2 验收形态；M4 换真实 IP）
- 依赖：`androidx.media3:media3-exoplayer/ui:1.4.1`，`AndroidManifest` 注册 PlayerActivity

---

## 4. 验证结果

- core-server 单测 **18/18 通过**：
  - RangeParserTest：10 项（空/多范围/开区间/闭区间/后缀/大小写/非法等）
  - HttpRangeServerTest：8 项真实 HTTP 端到端（整流 200 / Range 206 切片校验 / 后缀 / 416 / 404 / JSON 列表 / HEAD）
- `app-debug.apk` 打包成功（含 M2 播放端代码），产物：`app/build/outputs/apk/debug/app-debug.apk`（约 6.4MB）
- 构建命令：
  ```
  gradlew :core-server:test
  gradlew :app:assembleDebug
  ```
- **待真机验证**：播放器 Range 拖动、MediaStore 扫描结果均未在真机/模拟器确认

---

## 5. 关键修复记录（重要，避免回退）

1. **HTTP 头读取死锁**：原 `parseRequest` 用 `readNBytes(32*1024)`，客户端发完头等响应、流不 EOF → 永久阻塞。改为逐字节读到 `\r\n\r\n` 或 `\n\n`，并限制 64KB 防恶意头部。
2. **RandomAccessFile 无 FileDescriptor 构造**：content:// 无法直接 RAF。引入 `RangeReadable` 抽象 + `openForRange()` 默认实现，Android 端用 AssetFileDescriptor dup + FileChannel.position 实现 seek。
3. **fd 生命周期 bug**：`openAssetFileDescriptor(...).use{}` 会关闭底层 fd。用 `parcelFileDescriptor.dup()` 复制后再交给 RangeReadable，close 时再释放。

---

## 6. 环境说明

| 项 | 值 |
|----|----|
| Java | OpenJDK 17.0.18（JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot）|
| Android SDK | D:\rdtools\Android\Sdk（platforms: android-35；build-tools: 34/35）|
| Gradle | wrapper 8.7（gradle-wrapper 已入库）|
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| 构建 | `gradlew`（首次下载依赖后即可独立构建；无需本机装 gradle）|

**注意事项**：
- `local.properties` 指向 `D:\rdtools\Android\Sdk`（本机专用，不入库）
- 首次构建需联网下载 AGP/Kotlin/AndroidX 依赖（本机已缓存，二次构建快）

---

## 7. 下一步计划（M2 收尾 + M3 设备发现）

- [ ] M2 真机/模拟器验证：安装 APK → 浏览器访问 `http://<ip>:<port>/media` 检查列表 → 播放器拖动测试（验证 Range 206）
- [ ] M2 改进：MediaScanner 同步扫描改协程/线程，避免主线程卡顿/ANR
- [ ] M3：mDNS/DNS-SD（或 UDP 组播兜底）设备发现设计
- [ ] M3：在线设备列表 UI（顶部设备栏/Tab 切换）
- [ ] M4：URL 拼接真实 IP + 远程播放，闭环多设备互播

---

## 8. 待办 / 风险

- M2 播放端代码已并入工作区但**尚未提交 git**（本次已提交）；真机验证待做
- M3 设备发现（mDNS/DNS-SD 或 UDP 组播）尚未设计
- **主线程扫描风险**：`MainActivity.startServer()` 直接在主线程同步 `MediaScanner.scan()`，媒体库大时会卡 UI/ANR，需异步化
- **构建产物入库**：`.gitignore` 之前只匹配根 `/build`，`app/build/`、`core-server/build/` 曾被提交；已在本轮通过 `git rm --cached` 剔除并补充 ignore 规则
- 大文件内存压力：目前流式分块读写，安全
- MediaScanner 与播放器均未在真机端到端验证（单测仅覆盖 core-server）
