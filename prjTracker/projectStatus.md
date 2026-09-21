# HomeMediaViewer — 工程状态记录

> 更新时间：2026-09-21
> 目标：局域网内多台设备（Android/iOS/鸿蒙/Web）互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

---

## 1. 当前进度总览

| 里程碑 | 版本 | 状态 | 说明 |
|-------|------|------|------|
| M1 HTTP+Range 媒体服务 + 媒体扫描 | v0.1 | ✅ 完成 | 服务端模块 + 单测 22 项全过 |
| M2 ExoPlayer 播放端 | v0.1 | ✅ 完成 | 进度保存 + 横屏全屏播放 |
| M3 mDNS 设备发现 | v0.1 | ✅ 完成 | 局域网自动发现 |
| M4 跨设备播放 | v0.1 | ✅ 完成 | 含网络断开检测 |
| M5 缩略图/进度/搜索/深色/全屏/离线提示 | v0.1 | ✅ 完成 | 2026-09-18 全部实现 |
| 代码质量优化（安全/泄漏/性能/规范） | v0.1 | ✅ 完成 | 19 项改进全部修复 |
| V2 功能增强 | v0.2 | ✅ 完成 | 播放队列/媒体分组/设备收藏/投屏控制/播放速度 |
| V2 优化点 | v0.2 | ✅ 完成 | 分组可折叠/队列管理面板/设备选择器/收藏自动重连/速度持久化 |
| V3 Web 客户端 | v0.3 | ✅ 完成 | 21 个 JS 模块 / 4 语言国际化 / 18 个功能页面 |
| V3 后端 API 扩展 | v0.3 | ✅ 完成 | 文件上传/删除/重命名/新建文件夹 |
| V3 Android PAD 客户端 | v0.3 | ✅ 完成 | layout-sw600dp 双栏布局 |
| V3 iOS 客户端 | v0.3 | ✅ 完成 | SwiftUI + AVPlayer + HTTP Server |
| V3 HarmonyOS 客户端 | v0.3 | ✅ 完成 | ArkTS + AVPlayer + HTTP Server |
| V3 节点管理与监控 | v0.3 | ✅ 完成 | 设备类型显示/代理通道/运行统计/日志 |
| V3 用户权限管理 | v0.3 | ✅ 完成 | 多用户/角色权限/PBKDF2密码哈希 |
| V3 搜索增强 | v0.3 | ✅ 完成 | 搜索历史/实时建议/高级搜索 |
| V3 媒体元数据 | v0.3 | ✅ 完成 | 标签系统/基础元数据提取 |

---

## 2. 已实现功能

### 2.1 core-server（共享核心模块）

**HTTP API（31 个端点）：**

| 类别 | 端点 | 方法 |
|------|------|------|
| 媒体 | `/media` | GET |
| 媒体 | `/media/{id}` | GET (200/206) |
| 媒体 | `/media/{id}/thumbnail` | GET |
| 媒体 | `/media/{id}/metadata` | GET |
| 搜索 | `/search?q=&type=&folder=` | GET |
| 投屏 | `/play` | POST |
| 文件管理 | `/upload` | POST |
| 文件管理 | `/media/{id}` | DELETE |
| 文件管理 | `/media/{id}/rename` | POST |
| 文件管理 | `/folder` | POST |
| 扫描路径 | `/scanpaths` | GET/POST |
| 扫描路径 | `/scanpaths/{path}` | DELETE |
| 扫描路径 | `/scanpaths/rescan` | POST |
| 设备信息 | `/device/info` | GET/POST |
| 管理密码 | `/admin/password` | GET/POST |
| 管理密码 | `/admin/verify` | POST |
| 代理通道 | `/proxy/status` | GET |
| 代理通道 | `/proxy/enable` | POST |
| 代理通道 | `/proxy/disable` | POST |
| 代理通道 | `/proxy/{device}/operation` | POST |
| 统计 | `/stats/runtime` | GET |
| 统计 | `/stats/traffic` | GET |
| 日志 | `/logs` | GET |
| 日志 | `/logs/clear` | POST |
| 用户 | `/users` | GET/POST |
| 用户 | `/users/{username}` | DELETE/POST |
| 用户 | `/users/login` | POST |

**MediaRepository 接口：**
- list/findById/openStream/openForRange/getThumbnail
- uploadFile/deleteFile/renameFile/createFolder
- getScanPaths/addScanPath/removeScanPath/rescanAll
- getAdminPassword/setAdminPassword/verifyAdminPassword
- getProxyStatus/enableProxy/disableProxy/proxyOperation
- getDeviceType/getDeviceName/setDeviceInfo
- getRuntimeStats/getTrafficStats/getLogs/addLog/clearLogs
- getUsers/addUser/deleteUser/updateUserRole/verifyUser/checkPermission
- getMediaMetadata/searchMedia

**实现：**
- `FileMediaRepository`：桌面/测试用，直接文件系统访问
- `ContentMediaRepository`：Android 端，MediaStore content:// URI

**单元测试：** 22 项全部通过

### 2.2 Android 客户端

| 功能模块 | 实现状态 |
|----------|----------|
| MediaStore 媒体扫描 | ✅ 已实现 |
| ExoPlayer 播放 | ✅ 已实现 |
| 前台 Service | ✅ 已实现 |
| HTTP Server | ✅ 已实现（Java Socket） |
| mDNS 设备发现 | ✅ 已实现 |
| mDNS 服务注册 | ✅ 已实现 |
| 播放进度保存 | ✅ 已实现 |
| 播放速度调节 | ✅ 已实现 |
| 播放队列 | ✅ 已实现 |
| 媒体分组 | ✅ 已实现 |
| 设备收藏 | ✅ 已实现 |
| 投屏控制 | ✅ 已实现 |
| 缩略图 | ✅ 已实现 |
| 搜索筛选 | ✅ 已实现 |
| 深色模式 | ✅ 已实现 |
| 平板双栏布局 | ✅ 已实现 |
| 网络状态感知 | ✅ 已实现 |

### 2.3 iOS 客户端

| 功能模块 | 实现状态 |
|----------|----------|
| PHPhotoLibrary 媒体扫描 | ✅ 已实现 |
| AVPlayer 播放 | ✅ 已实现 |
| SwiftUI 界面 | ✅ 已实现 |
| HTTP Server | ✅ 已实现（NWListener） |
| mDNS 设备发现 | ✅ 已实现 |
| mDNS 服务注册 | ✅ 已实现 |
| 播放进度保存 | ✅ 已实现 |
| 播放速度调节 | ✅ 已实现 |
| 播放队列 | ✅ 已实现 |
| 媒体分组 | ✅ 已实现 |
| 设备收藏 | ✅ 已实现 |
| 投屏控制 | ✅ 已实现 |
| 文件管理 | ✅ 已实现 |

### 2.4 HarmonyOS 客户端

| 功能模块 | 实现状态 |
|----------|----------|
| MediaKit 媒体扫描 | ✅ 已实现 |
| AVPlayer 播放 | ✅ 已实现 |
| ArkTS 声明式 UI | ✅ 已实现 |
| HTTP Server | ✅ 已实现（Native Socket） |
| mDNS 设备发现 | ✅ 已实现 |
| mDNS 服务注册 | ✅ 已实现 |
| 播放进度保存 | ✅ 已实现 |
| 播放速度调节 | ✅ 已实现 |
| 播放队列 | ✅ 已实现 |
| 媒体分组 | ✅ 已实现 |
| 设备收藏 | ✅ 已实现 |
| 投屏控制 | ✅ 已实现 |
| 文件管理 | ✅ 已实现 |

### 2.5 Web 客户端

| 功能模块 | 实现状态 |
|----------|----------|
| 21 个 JS 模块 | ✅ 已实现 |
| 18 个功能页面 | ✅ 已实现 |
| 4 语言国际化 | ✅ 已实现 |
| 运行环境检测 | ✅ 已实现 |
| 移动端 APP 引导 | ✅ 已实现 |
| 设备节点管理 | ✅ 已实现 |
| 节点运行统计 | ✅ 已实现 |
| 用户权限管理 | ✅ 已实现 |
| 搜索增强 | ✅ 已实现 |
| 媒体元数据 | ✅ 已实现 |
| 文件管理 | ✅ 已实现 |

---

## 3. 验证结果

- core-server 单测 **22/22 通过**
- `app:compileDebugKotlin` 编译通过
- `app:assembleRelease` R8 混淆构建通过
- README 与代码一致性检查通过

---

## 4. 多端架构

| 端 | 平台 | HTTP Server | mDNS 服务 | 状态 |
|----|------|-------------|-----------|------|
| Phone | Android | HttpRangeServer.kt (Java Socket) | ✅ | ✅ 完成 |
| PAD | Android Tablet | HttpRangeServer.kt (Java Socket) | ✅ | ✅ 完成 |
| PC | Web (浏览器) | ❌ 不支持 | ❌ | ✅ 完成 |
| Phone | iOS | HttpServer.swift (NWListener) | ✅ | ✅ 完成 |
| Phone | HarmonyOS | HttpServer.ets (Native Socket) | ✅ | ✅ 完成 |

**共享核心**：core-server 模块（纯 Kotlin/JVM），Android 端通过 HTTP API 通信。

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
| Media3 | 1.4.1 |
| Material | 1.12.0 |
| iOS | Swift 5.9 / iOS 16.0+ |
| HarmonyOS | ArkTS / HarmonyOS 5.0+ |
