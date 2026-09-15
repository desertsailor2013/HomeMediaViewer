# HomeMediaViewer — 设计状态记录

> 记录时间：2026-09-13
> 上次更新 projectStatus.md：2026-08-12
> 目标：局域网内多台 Android 设备互相发现、浏览、点播彼此的音视频文件，无需拷贝源文件。

---

## 1. 项目基本信息

| 项 | 值 |
|----|----|
| 路径 | `C:\aiSetup\aiSrcFac\HomeMediaViewer` |
| 平台 | Android (Kotlin) |
| 构建 | Gradle 8.13 + AGP 8.13.0 + Kotlin 1.9.24 |
| Application ID | `com.hmv.app` |
| 版本 | 0.1.0 (versionCode=1) |
| 模块 | `:core-server`（纯 JVM）+ `:app`（Android） |

---

## 2. 当前功能状态

| 里程碑 | 状态 | 说明 |
|-------|------|------|
| M1 HTTP+Range 服务器 + 媒体扫描 | ✅ 完成 | 17+ 单测通过 |
| M2 ExoPlayer 播放端 | ⚠️ 代码完成/未验证 | 未在真机/模拟器验证 |
| M3 设备发现 (mDNS) | ❌ 未开始 | |
| M4 跨设备播放 | ❌ 未开始 | 当前仅 127.0.0.1 |
| M5 缩略图/进度/打磨 | ❌ 未开始 | |

---

## 3. 上架差距分析（2026-09-13 生成）

### P0 — 功能完整性（必须）

| # | 工作项 | 说明 |
|---|--------|------|
| 1 | 真机验证 M1+M2 | 从未在真机/模拟器验证过 |
| 2 | 修复主线程阻塞 | MediaScanner.scan() 在主线程同步执行，大媒体库 ANR |
| 3 | 设备发现 (M3) | mDNS/DNS-SD 或 UDP 组播 |
| 4 | 跨设备播放 (M4) | 真实 LAN IP 替换 127.0.0.1 |
| 5 | 前台服务 | HTTP 服务器需绑定前台 Service |

### P1 — 发布配置（上架必需）

| # | 工作项 | 说明 |
|---|--------|------|
| 6 | 签名配置 | 创建 Keystore，配置 release signingConfigs |
| 7 | 启用 R8 混淆/压缩 | isMinifyEnabled=true，编写 proguard-rules.pro |
| 8 | 版本号管理 | 正式版本策略 |
| 9 | targetSdk 适配 | target 35，前台服务类型声明 |
| 10 | 隐私政策 | 应用访问媒体文件，Google Play 要求 |

### P2 — 商店素材

| # | 工作项 | 说明 |
|---|--------|------|
| 11 | 应用截图 | 2-8 张 |
| 12 | Feature Graphic | 1024×500 横幅 |
| 13 | 应用描述 | 中/英文 |
| 14 | 应用图标 | 确认高分辨率 mipmap |
| 15 | 启动页 | Android 12+ SplashScreen API |

### P3 — 工程质量

| # | 工作项 | 说明 |
|---|--------|------|
| 16 | README.md | 项目说明 |
| 17 | LICENSE | 开源许可证 |
| 18 | CHANGELOG.md | 版本变更记录 |
| 19 | CI/CD | GitHub Actions 自动构建+测试 |
| 20 | Android 侧测试 | 当前 0 个 instrumented test |

---

## 4. 预估工作量

| 阶段 | 内容 | 预估 |
|------|------|------|
| M1+M2 验证+修复 | 真机验证、ANR 修复、前台服务 | 1-2 天 |
| M3+M4 功能闭环 | 设备发现+跨设备播放 | 3-5 天 |
| 发布配置 | 签名、R8、隐私政策 | 1 天 |
| 商店素材 | 截图、描述、上架资料 | 1-2 天 |
| **总计** | | **6-10 天** |

---

## 5. 已知风险

1. **主线程扫描 ANR 风险**：MediaScanner 同步执行，需异步化
2. **前台服务缺失**：App 进入后台后 ServerSocket 会被系统杀死
3. **未真机验证**：所有功能仅通过单元测试验证
4. **无签名配置**：无法构建 Release 包
5. **无 ProGuard 规则**：混淆未启用

---

## 6. 下次接续任务

### 优先执行
1. 真机/模拟器安装 APK，验证 M1 媒体扫描 + M2 播放
2. MediaScanner 异步化（协程/线程）
3. 前台 Service 封装 HTTP Server

### 后续执行
4. mDNS 设备发现实现
5. 跨设备播放（真实 IP）
6. 签名 + R8 + 隐私政策
7. 商店素材准备

---

## 7. 环境依赖

| 项 | 值 |
|----|----|
| Java | OpenJDK 17 |
| Android SDK | 需确认本地路径（projectStatus.md 记录为 D:\rdtools\Android\Sdk，当前机器为 C:\aiSetup）|
| 构建命令 | `gradlew :core-server:test` / `gradlew :app:assembleDebug` |
| APK 产物 | `app/build/outputs/apk/debug/app-debug.apk` |
