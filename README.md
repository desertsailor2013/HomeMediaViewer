# HomeMediaViewer

局域网内多台设备（Android/iOS/鸿蒙/Web）互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

**平台角色说明：**
- **Android** — 既可提供媒体服务（HTTP Server），也可浏览其他设备
- **iOS** — 既可提供媒体服务（HTTP Server），也可浏览其他设备
- **鸿蒙** — 既可提供媒体服务（HTTP Server），也可浏览其他设备
- **Web** — 浏览其他设备（依赖其他设备的 HTTP Server）

## 功能特性

### 核心功能（全平台）
- **mDNS 设备自动发现** — 基于 `_hmv._tcp.` 服务类型，局域网内设备自动注册与发现
- **HTTP + Range 流媒体服务** — 轻量内嵌 ServerSocket，支持整流/分段请求，拖动进度条自动触发 Range 206（Android/iOS 提供）
- **跨设备播放** — 选择发现的远程设备，获取其媒体列表并直接流式播放
- **缩略图展示** — 视频帧/音频封面自动生成与异步加载（Android/iOS 提供，需设备端支持）
- **播放进度保存** — 自动记录播放位置，再次打开自动续播
- **搜索与筛选** — 按文件名搜索，按类型（全部/视频/音频）筛选
- **深色模式** — 支持亮色/暗色主题自动切换
- **播放速度调节** — 0.5x ~ 2.0x 循环切换，速度偏好持久化
- **播放队列** — 选择多个媒体项加入队列，自动连播
- **媒体分组** — 按文件夹名分组显示，可折叠展开
- **设备收藏** — 星标收藏常用设备，支持自定义别名
- **投屏控制** — 将本机播放内容推送到指定设备继续播放，支持断点续播
- **网络状态感知** — 播放页网络断开提示，设备离线自动切回本地

### 设备管理与代理通道
- **设备节点管理** — Web 端可查看所有在线设备节点，显示设备类型（手机/平板/电脑/鸿蒙/iOS/Web）
- **管理密码验证** — 设备设置管理密码，Web 端输入密码后可远程管理设备资源
- **代理通道** — 通过密码验证后，Web 端可代理执行设备端文件操作
- **远程资源管理** — 浏览/播放/发布/删除设备端媒体文件，支持上传和新建文件夹

### 运行时监控
- **运行状态统计** — CPU 使用率、内存/存储使用、电池状态、网络流量、活跃连接数
- **流量统计** — 按设备/媒体统计出站（被点播）和入站（点播他人）流量
- **运行日志** — 实时查看设备运行日志，支持清空和导出

### 用户权限管理
- **多用户支持** — 管理员/编辑者/访客三种角色（JSON 文件持久化存储）
- **权限控制** — admin 全部权限，editor 上传/删除，viewer 只读
- **用户登录** — 支持用户名密码登录（PBKDF2 密码哈希）
- **用户管理** — 管理员可添加/删除用户，修改用户角色

### 搜索增强
- **搜索历史** — 自动保存最近 20 条搜索记录，一键复用（Web 端实现）
- **实时搜索建议** — 输入时显示匹配的媒体文件（Web 端实现）
- **高级搜索** — 按类型/文件夹/大小范围筛选（数据层实现）
- **全局搜索** — 跨设备搜索媒体文件（数据层实现）

### 媒体信息增强
- **媒体元数据** — 显示标题/艺术家/专辑/时长/分辨率/码率/编码/格式（基础字段从文件名提取，完整元数据需媒体解析库）
- **海报墙展示** — 显示媒体海报/封面（待实现）
- **标签系统** — 支持媒体标签分类（自动生成标签）
- **字幕支持** — 显示关联的字幕文件（待实现）
- **播放统计** — 显示播放次数/最后播放时间（待实现）

### Web 端专属
- **18 个功能页面** — 媒体库/设备列表/收藏设备/播放队列/播放历史/文件管理/统计面板/网络诊断/快捷键/多语言/数据导出/节点管理/节点统计/用户管理/搜索增强/媒体详情/设置/关于
- **4 语言国际化** — zh-CN / en / ja / ko，130+ 翻译键
- **运行环境检测** — 自动识别 PC/手机/平板，PC 端支持本地扫描路径管理
- **移动端 APP 引导** — 移动端访问时提示安装原生 APP，支持深链接跳转

### Android 端专属
- **MediaStore 媒体扫描** — 自动扫描设备中的视频和音频文件（API 33+ 适配）
- **ExoPlayer 播放** — 基于 Media3 ExoPlayer，支持 HTTP 流播放
- **前台 Service** — HTTP 服务绑定前台 Service，后台运行不被系统回收
- **平板双栏布局** — `layout-sw600dp` 自适应，大屏设备左侧列表+右侧播放器

### iOS 端专属
- **PHPhotoLibrary 媒体扫描** — 本地视频/音频扫描
- **AVPlayer 播放** — 原生播放器 + AirPlay 投屏
- **SwiftUI 界面** — 现代化声明式 UI
- **HTTP Server** — 基于 NWListener 的流媒体服务
- **mDNS 服务注册** — 自动注册到局域网供其他设备发现

### 鸿蒙端专属
- **MediaKit 媒体扫描** — PhotoAccessHelper 本地媒体访问
- **AVPlayer 播放** — 原生播放器 + Cast 投屏
- **ArkTS 声明式 UI** — ArkUI 组件化界面
- **HTTP Server** — 基于原生 Socket API 的流媒体服务
- **mDNS 服务注册** — 自动注册到局域网供其他设备发现

## 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| core-server | Kotlin/JVM | 1.9.24 |
| Android | AGP + ExoPlayer + Coil | 8.13.0 / 1.4.1 / 2.7.0 |
| Web | HTML5 + CSS3 + Vanilla JS | - |
| iOS | Swift + SwiftUI | iOS 16.0+ |
| HarmonyOS | ArkTS + ArkUI | HarmonyOS 4.0+ |

## 项目结构

```
├── core-server/                 共享核心模块（纯 Kotlin/JVM，零依赖）
│   └── src/main/kotlin/com/hmv/server/
│       ├── HttpRangeServer.kt   HTTP+Range 服务 + 文件管理 + 扫描路径 + 设备信息 + 统计 + 日志 + 用户管理 + 媒体元数据 + 搜索
│       ├── MediaRepository.kt   媒体数据源抽象 + MediaItem 模型 + 代理通道 + 用户权限 + 元数据接口
│       ├── FileMediaRepository.kt 文件系统实现
│       ├── RangeParser.kt       HTTP Range 头解析
│       └── RangeReadable.kt     可 seek 只读源接口
├── app/                         Android Phone 端
│   ├── src/main/kotlin/com/hmv/app/
│   │   ├── MainActivity.kt      主界面（单栏/双栏自适应）
│   │   ├── PlayerActivity.kt    ExoPlayer 播放页
│   │   ├── MediaServerService.kt 前台 Service
│   │   └── ...                  其他组件
│   └── src/main/res/
│       ├── layout/              手机布局
│       └── layout-sw600dp/      平板双栏布局
├── web-client/                  Web 端（18 页面 + 4 语言）
│   ├── js/
│   │   ├── app.js               主应用模块
│   │   ├── api.js               API 封装
│   │   ├── device.js            设备连接与发现
│   │   ├── player.js            播放器控制
│   │   ├── environment.js       运行环境检测
│   │   ├── mobile-app.js        移动端 APP 检测
│   │   ├── node-manager.js      设备节点管理
│   │   ├── node-stats.js        节点运行统计与日志
│   │   ├── user-manager.js      用户权限管理
│   │   ├── search-enhanced.js   搜索增强（历史/建议/高级）
│   │   ├── media-metadata.js    媒体元数据展示
│   │   └── ...                  其他模块
│   └── index.html               主页面
├── harmony-client/              鸿蒙端（ArkTS）
│   └── entry/src/main/ets/
│       ├── pages/               页面组件
│       └── common/              工具类
├── ios-client/                  iOS 端（SwiftUI）
│   └── HomeMediaViewer/
│       ├── Views/               视图组件
│       ├── ViewModels/          视图模型
│       └── Services/            服务层
├── MULTI_PLATFORM_COMPARISON.md 多平台功能对比
└── prjTracker/                  项目状态记录
```

## 快速开始

### 1. 获取源码

```bash
git clone https://github.com/desertsailor2013/HomeMediaViewer.git
cd HomeMediaViewer
```

### 2. 选择平台构建

#### Android（推荐）

**环境要求：**
- OpenJDK 17+
- Android SDK（compileSdk 35）
- Windows/macOS/Linux

**构建步骤：**
```bash
# 运行服务端单元测试（22 项）
./gradlew :core-server:test

# 构建调试 APK
./gradlew :app:assembleDebug

# 构建 Release APK（含 R8 混淆）
./gradlew :app:assembleRelease
```

**安装与运行：**
```bash
# 连接 Android 设备或启动模拟器
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.hmv.app/.MainActivity
```

**APK 产物位置：**
- 调试版：`app/build/outputs/apk/debug/app-debug.apk`
- 发布版：`app/build/outputs/apk/release/app-release.apk`

---

#### iOS

**环境要求：**
- macOS 12.0+
- Xcode 15.0+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)（用于生成 Xcode 项目）

**安装 XcodeGen：**
```bash
# 使用 Homebrew 安装
brew install xcodegen

# 或使用 Mint 安装
mint install yonaskolb/XcodeGen
```

**构建步骤：**
```bash
cd ios-client

# 生成 Xcode 项目
xcodegen generate

# 打开 Xcode 项目
open HomeMediaViewer.xcodeproj
```

**在 Xcode 中运行：**
1. 打开 `HomeMediaViewer.xcodeproj`
2. 选择目标设备（模拟器或真机）
3. 点击 `Run` 按钮或按 `Cmd+R`
4. 首次运行需在 `Signing & Capabilities` 中配置开发者证书

**命令行构建：**
```bash
# 构建调试版
xcodebuild -project HomeMediaViewer.xcodeproj -scheme HomeMediaViewer -sdk iphonesimulator -configuration Debug build

# 构建发布版
xcodebuild -project HomeMediaViewer.xcodeproj -scheme HomeMediaViewer -sdk iphoneos -configuration Release build
```

---

#### HarmonyOS

**环境要求：**
- Windows 10/11 或 macOS 12.0+
- DevEco Studio 5.0+
- HarmonyOS SDK 5.0+

**安装 DevEco Studio：**
1. 访问 [华为开发者联盟](https://developer.huawei.com/consumer/cn/deveco-studio/) 下载 DevEco Studio
2. 安装并配置 HarmonyOS SDK

**构建步骤：**
```bash
# 打开 DevEco Studio
# File → Open → 选择 harmony-client 目录
```

**在 DevEco Studio 中运行：**
1. 打开 `harmony-client` 目录
2. 等待项目同步完成
3. 选择目标设备（模拟器或真机）
4. 点击 `Run` 按钮或按 `Shift+F10`

**命令行构建（需配置 SDK 环境）：**
```bash
cd harmony-client

# 使用 DevEco Studio 内置的 hvigorw 构建
# 或通过 DevEco Studio 菜单 Build → Build Hap(s)
```

**产物位置：**
- `entry/build/default/outputs/default/entry-default-signed.hap`

---

#### Web（最简单）

**环境要求：**
- 现代浏览器（Chrome 90+ / Firefox 88+ / Safari 15+ / Edge 90+）

**方式一：直接打开**
```bash
# Windows
start web-client/index.html

# macOS
open web-client/index.html

# Linux
xdg-open web-client/index.html
```

**方式二：使用 Python 内置服务器**
```bash
cd web-client

# Python 3
python -m http.server 8000

# 访问 http://localhost:8000
```

**方式三：使用 Node.js**
```bash
# 安装 http-server（全局）
npm install -g http-server

# 启动服务器
cd web-client
http-server -p 8000 -c-1

# 访问 http://localhost:8000
```

**方式四：使用 VS Code Live Server**
1. 安装 VS Code 扩展 `Live Server`
2. 右键点击 `web-client/index.html`
3. 选择 `Open with Live Server`

---

## 首次使用指南

### Android 端
1. 安装 APK 到设备
2. 授予媒体读取权限
3. 应用自动扫描本地媒体文件
4. 在设备列表中选择其他设备进行播放

### iOS 端
1. 在 Xcode 中运行到设备
2. 授予相册访问权限
3. 应用自动扫描本地媒体
4. 发现其他设备后点击连接

### 鸿蒙端
1. 在 DevEco Studio 中运行
2. 授予媒体读取权限
3. 自动扫描并发现局域网设备

### Web 端
1. 打开浏览器访问页面
2. 输入设备 IP 和端口连接
3. 或等待 mDNS 自动发现设备
4. 点击设备查看媒体列表并播放

## HTTP API

### 媒体服务
| 端点 | 方法 | 说明 |
|------|------|------|
| `/media` | GET | 返回 JSON 媒体列表 |
| `/media/{id}` | GET | 返回整文件字节流（200） |
| `/media/{id}` + Range | GET | 206 Partial Content |
| `/media/{id}/thumbnail` | GET | 返回缩略图（JPEG/PNG） |
| `/media/{id}/metadata` | GET | 获取媒体元数据（分辨率/码率/时长等） |
| `/play` | POST | 投屏控制指令 |
| `/search` | GET | 全局搜索媒体（支持 q/type/folder 参数） |

### 文件管理
| 端点 | 方法 | 说明 |
|------|------|------|
| `/upload` | POST | multipart 文件上传 |
| `/media/{id}` | DELETE | 删除文件 |
| `/media/{id}/rename` | POST | 重命名文件 |
| `/folder` | POST | 新建文件夹 |

### 扫描路径管理
| 端点 | 方法 | 说明 |
|------|------|------|
| `/scanpaths` | GET | 获取扫描路径列表 |
| `/scanpaths` | POST | 添加扫描路径 |
| `/scanpaths/{path}` | DELETE | 移除扫描路径 |
| `/scanpaths/rescan` | POST | 重新扫描所有路径 |

### 设备信息与代理通道
| 端点 | 方法 | 说明 |
|------|------|------|
| `/device/info` | GET | 获取设备类型和名称 |
| `/device/info` | POST | 设置设备类型和名称 |
| `/admin/password` | GET | 获取是否已设置密码 |
| `/admin/password` | POST | 设置管理密码 |
| `/admin/verify` | POST | 验证管理密码 |
| `/proxy/status` | GET | 获取代理通道状态 |
| `/proxy/enable` | POST | 启用代理通道 |
| `/proxy/disable` | POST | 禁用代理通道 |
| `/proxy/{device}/operation` | POST | 代理操作执行 |

### 运行时统计与日志
| 端点 | 方法 | 说明 |
|------|------|------|
| `/stats/runtime` | GET | 获取运行时统计（CPU/内存/存储/电池/网络） |
| `/stats/traffic` | GET | 获取流量统计（出站/入站/按设备/按媒体） |
| `/logs` | GET | 获取运行日志 |
| `/logs/clear` | POST | 清空日志 |

### 用户权限管理
| 端点 | 方法 | 说明 |
|------|------|------|
| `/users` | GET | 获取用户列表 |
| `/users` | POST | 添加用户（username/password/role） |
| `/users/{username}` | DELETE | 删除用户 |
| `/users/{username}` | POST | 更新用户角色 |
| `/users/login` | POST | 用户登录验证 |

## 工作原理

```
┌─────────────────────────────────────────────────────────────────────┐
│                            局域网 (LAN)                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    mDNS 发现    ┌─────────────┐                  │
│  │  Android    │◄───────────────►│  iOS        │                  │
│  │  Phone/Pad  │                 │  iPhone     │                  │
│  └──────┬──────┘                 └──────┬──────┘                  │
│         │                               │                          │
│         │    HTTP API                   │                          │
│         ▼                               ▼                          │
│  ┌─────────────────────────────────────────────┐                  │
│  │              core-server HTTP API            │                  │
│  │  /media  /play  /upload  /scanpaths  ...    │                  │
│  │  /admin  /proxy  /device  /stats    /logs   │                  │
│  │  /users  /search  /media/{id}/metadata      │                  │
│  └─────────────────────────────────────────────┘                  │
│         ▲                               ▲                          │
│         │                               │                          │
│  ┌──────┴──────┐                 ┌──────┴──────┐                  │
│  │  Web        │                 │  HarmonyOS  │                  │
│  │  (PC/移动)  │                 │  手机/平板   │                  │
│  └─────────────┘                 └─────────────┘                  │
│                                                                     │
│  Web 端功能:                                                       │
│  - 节点管理: 查看在线设备，显示设备类型                             │
│  - 代理通道: 密码验证后远程管理设备资源                             │
│  - 运行统计: CPU/内存/存储/电池/网络                                │
│  - 流量统计: 按设备/媒体统计出站入站流量                            │
│  - 运行日志: 实时查看设备运行日志                                   │
│  - 用户管理: 多用户/角色权限                                        │
│  - 搜索增强: 搜索历史/实时建议/高级搜索                             │
│  - 媒体信息: 元数据/海报/标签/字幕                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

1. 每台设备启动后扫描本地媒体，开启 HTTP 服务并注册 mDNS
2. 设备间通过 mDNS 自动发现彼此，收藏设备直连加速
3. 点击远程设备 → 拉取 `/media` JSON 列表，支持分组浏览
4. 点击媒体项 → 播放远程 URL，支持队列播放、拖动（Range 206）
5. 投屏 → 点击投屏按钮 → 选择目标设备 → `POST /play` 发送播放指令
6. 节点管理 → Web 端查看在线设备 → 输入管理密码 → 远程管理资源
7. 运行统计 → Web 端查看各设备 CPU/内存/流量等运行状态
8. 用户登录 → 输入用户名密码 → 根据角色权限操作
9. 搜索 → 输入关键词 → 实时建议/搜索历史/高级筛选

## 权限说明

### Android
| 权限 | 用途 |
|------|------|
| `INTERNET` | HTTP 服务端与客户端通信 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | API 33+ 媒体文件读取 |
| `READ_EXTERNAL_STORAGE` | API ≤ 32 媒体文件读取 |
| `FOREGROUND_SERVICE` | HTTP 服务后台常驻 |

### iOS
| 权限 | 用途 |
|------|------|
| `NSPhotoLibraryUsageDescription` | 访问相册媒体文件 |
| `NSLocalNetworkUsageDescription` | 局域网通信 |

### HarmonyOS
| 权限 | 用途 | 声明方式 |
|------|------|----------|
| `ohos.permission.INTERNET` | 网络通信 | module.json5 静态声明 |
| `ohos.permission.READ_MEDIA` | 读取媒体文件 | 运行时动态请求 |

## 安全特性

- 签名密码存储在 `local.properties`（已 gitignore），不入库
- 网络安全配置限定 HTTP 明文仅允许局域网访问
- 禁用 `allowBackup` 防止 adb 提取应用数据
- R8 混淆 + 资源缩减（Release 构建）
- 管理密码验证保护设备远程管理权限
- 代理通道密码验证防止未授权访问
- 多用户角色权限控制（admin/editor/viewer）

## 环境要求

- OpenJDK 17+
- Android SDK（compileSdk 35）
- Xcode 15+（iOS 开发）
- DevEco Studio 5.0+（鸿蒙开发）
- 首次构建需联网下载依赖

## 路线图

- [x] M1 HTTP+Range 媒体服务 + 媒体扫描
- [x] M2 ExoPlayer 播放端
- [x] M3 mDNS 设备发现
- [x] M4 跨设备播放
- [x] M5 缩略图 / 播放进度 / 搜索筛选 / 深色模式 / 全屏播放 / 网络感知
- [x] V2 播放队列 / 媒体分组 / 设备收藏 / 投屏控制 / 播放速度
- [x] V3 Web 客户端（18 页面 + 4 语言）
- [x] V3 iOS 客户端（SwiftUI）
- [x] V3 HarmonyOS 客户端（ArkTS）
- [x] Web 端运行环境检测 + 扫描路径管理
- [x] 移动端 APP 检测与深链接
- [x] 设备节点管理（设备类型显示 + 密码验证）
- [x] 代理通道（远程资源管理）
- [x] 运行时统计（CPU/内存/存储/电池/网络）
- [x] 流量统计（按设备/媒体统计出站入站）
- [x] 运行日志（查看/清空/导出）
- [x] 用户权限管理（多用户/角色权限）
- [x] 搜索增强（搜索历史/实时建议/高级搜索）
- [x] 媒体信息增强（元数据/海报/标签/字幕）
- [ ] CI/CD 自动构建
- [ ] 鸿蒙 APP 编译验证（需 DevEco Studio）

## License

MIT
