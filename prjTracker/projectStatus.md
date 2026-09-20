# HomeMediaViewer — 工程状态记录

> 更新时间：2026-09-19
> 目标：局域网内多台设备（手机/PAD/PC）互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

---

## 1. 当前进度总览

| 里程碑 | 版本 | 状态 | 说明 |
|-------|------|------|------|
| M1 HTTP+Range 媒体服务 + 媒体扫描 | v0.1 | ✅ 完成 | 服务端模块 + 单测 22 项全过 |
| M2 ExoPlayer 播放端 | v0.1 | ✅ 完成 | 进度保存 + 横屏全屏播放 |
| M3 mDNS 设备发现 | v0.1 | ✅ 完成 | 局域网自动发现 |
| M4 跨设备播放 | v0.1 | ✅ 完成 | 含网络断开检测 |
| M5 缩略图/进度/搜索/深色/全屏/离线提示 | v0.1 | ✅ 完成 | 2026-09-18 全部实现 |
| 代码质量优化（安全/泄漏/性能/规范） | v0.1 | ✅ 完成 | 19 项改进全部修复（2026-09-19） |
| **V2 功能增强** | **v0.2** | **✅ 完成** | 播放队列 / 媒体分组 / 设备收藏 / 投屏控制 / 播放变速（2026-09-19） |
| **V2 优化点** | **v0.2** | **✅ 完成** | 分组可折叠 / 队列管理面板 / 设备选择器 / 收藏自动重连 / 速度持久化（2026-09-19） |
| **V3 Web 客户端** | **v0.3** | **✅ 完成** | 13 页面 / 15 文件 / 3580 行 / 4 语言国际化（2026-09-19） |
| **V3 后端 API 扩展** | **v0.3** | **✅ 完成** | 文件上传/删除/重命名/新建文件夹（2026-09-19） |
| **V3 PAD 客户端** | **v0.3** | **⏳ 待开发** | Android Tablet 双栏布局 |

---

## 2. 已实现功能

### 2.1 core-server（共享核心模块）
- **HTTP API**：
  - `GET /media` → JSON 媒体列表（支持 `?path=` 子目录浏览）
  - `GET /media/{id}` → 整文件字节流（200）/ Range 请求（206）
  - `GET /media/{id}/thumbnail` → 缩略图（JPEG/PNG）
  - `POST /upload` → multipart 文件上传（最大 500MB）
  - `DELETE /media/{id}` → 删除文件
  - `POST /media/{id}/rename` → 重命名文件
  - `POST /folder` → 新建文件夹
  - `POST /play` → 投屏控制指令
- **MediaRepository 接口**：list/findById/openStream/openForRange/getThumbnail/uploadFile/deleteFile/renameFile/createFolder
- **实现**：
  - `FileMediaRepository`：桌面/测试用，直接文件系统访问
  - `ContentMediaRepository`：Android 端，MediaStore content:// URI
- **单元测试**：22 项全部通过

### 2.2 Phone 客户端（Android）
- ExoPlayer 播放 + 进度保存 + 横屏全屏
- mDNS 设备发现 + 跨设备播放
- 搜索/筛选/缩略图/深色模式
- 播放队列 + 媒体分组 + 设备收藏 + 投屏控制 + 播放速度
- 优化：分组可折叠 / 队列管理面板 / 设备选择器 / 收藏自动重连 / 速度持久化

### 2.3 Web 客户端（PC 浏览器）
- **15 个文件，3580 行代码**
- **13 个页面**：媒体库/设备列表/收藏设备/播放队列/播放历史/文件管理/统计面板/网络诊断/快捷键/多语言/数据导出/设置/关于
- **10 个功能模块**：api/device/player/queue/favorites/history/filemanager/stats/network/shortcuts/i18n/export/settings
- **4 语言国际化**：zh-CN/en/ja/ko，130+ 翻译键
- **文件管理**：浏览/搜索/上传/删除/重命名/新建文件夹（前后端打通）

---

## 3. 验证结果

- core-server 单测 **22/22 通过**：
  - RangeParserTest：10 项
  - HttpRangeServerTest：12 项
- `app:compileDebugKotlin` 编译通过
- `app:assembleRelease` R8 混淆构建通过

---

## 4. V3 多端拆分规划

详见 `prjTracker/v3-plan.md`

| 端 | 平台 | UI 特征 | 版本 | 状态 |
|----|------|---------|------|------|
| Phone | Android | 单栏布局 | v0.2 | ✅ 完成 |
| PAD | Android Tablet | 双栏布局 | v0.3 | ⏳ 待开发 |
| PC | Web (浏览器) | 响应式布局 | v0.3 | ✅ 完成 |

**共享核心**：core-server 模块（纯 Kotlin/JVM），所有端通过 HTTP API 通信。

---

## 5. PAD 端开发计划

### 目标
在 Phone 端基础上，针对平板大屏优化 UI 布局，提供双栏/多栏体验。

### 核心特性
- **双栏布局**：左侧设备列表/媒体列表，右侧播放器/详情
- **自适应布局**：根据屏幕尺寸自动切换单栏/双栏/三栏
- **大屏优化**：更大的缩略图、更多的信息密度
- **手势支持**：拖拽分屏、滑动返回

### 技术方案
- 复用 core-server 模块（HTTP API）
- 新建 `pad` 模块，基于 phone 模块改造 UI
- 使用 Android Tablet 专用布局资源（layout-sw600dp/layout-sw720dp）
- ExoPlayer 全屏播放优化

---

## 6. 环境说明

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
