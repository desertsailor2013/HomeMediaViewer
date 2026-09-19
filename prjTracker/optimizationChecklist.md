# HomeMediaViewer 优化改进清单

> 创建时间：2026-09-18
> 状态说明：✅ 已修复 / 🔄 进行中 / ⏳ 待处理

---

## 安全（高优先级）

| # | 问题 | 文件 | 状态 | 修复内容 |
|---|------|------|------|---------|
| 1 | 签名密码明文提交到 git | `gradle.properties:7-9` | ✅ | 密码移至 `local.properties`（已 gitignore） |
| 2 | build.gradle 硬编码回退密码 | `app/build.gradle.kts:21-23` | ✅ | 回退值改为空字符串 |
| 3 | 全局允许 HTTP 明文 | `AndroidManifest.xml:20` | ✅ | 新建 `network_security_config.xml`，仅允许 `127.0.0.1`、`10.x`、`172.16.x`、`192.168.x` |

---

## 资源泄漏（高优先级）

| # | 问题 | 文件 | 状态 | 修复内容 |
|---|------|------|------|---------|
| 4 | HttpURLConnection 未关闭 | `RemoteMediaClient.kt:22-36` | ✅ | `try-finally { conn.disconnect() }`，`bufferedReader().use {}` |
| 5 | AssetFileDescriptor 未关闭 | `ContentMediaRepository.kt:34` | ✅ | `afd.use {}` 管理生命周期 |

---

## 硬编码字符串（中优先级）

| # | 文件 | 状态 | 修复内容 |
|---|------|------|---------|
| 6 | `MainActivity.kt` | ✅ | 5 处提取至 `strings.xml` |
| 7 | `MediaServerService.kt` | ✅ | 5 处提取至 `strings.xml` |
| 8 | `MediaAdapter.kt` | ✅ | 3 处复用已有 `@string/filter_video` 等 |

---

## 性能（中优先级）

| # | 问题 | 文件 | 状态 | 修复内容 |
|---|------|------|------|---------|
| 9 | 每次请求重编译 Regex | `HttpRangeServer.kt:88` | ✅ | 提为 `companion object { THUMBNAIL_PATTERN }` 常量 |
| 10 | 无限线程池 | `HttpRangeServer.kt:45` | ✅ | 改为 `newFixedThreadPool(max(4, cores))` |
| 11 | `notifyDataSetChanged()` 全量刷新 | `MediaAdapter.kt:58` | ✅ | 改用 `DiffUtil.calculateDiff` + `dispatchUpdatesTo` |
| 12 | 重复创建 MediaScanner | `MainActivity.kt:164-165` | ✅ | 复用单个 `scanner` 实例 |

---

## 代码质量（中优先级）

| # | 问题 | 文件 | 状态 | 建议 |
|---|------|------|------|------|
| 13 | `emptyHint` 未使用的 TextView | `MainActivity.kt:33` | ✅ | 改为 `findViewById(R.id.empty_hint)` |
| 14 | 废弃 API `getSerializableExtra` | `MediaServerService.kt:37` | ✅ | API 33+ 用类型化重载，低版本降级处理 |
| 15 | 未使用的 `coil-compose` 依赖 | `libs.versions.toml:20` | ✅ | 已删除 |
| 16 | `allowBackup="true"` | `AndroidManifest.xml:14` | ✅ | 改为 `false`，防止 adb 提取应用数据 |

---

## 最佳实践（低优先级）

| # | 问题 | 状态 | 建议 |
|---|------|------|------|
| 17 | R8 混淆未开启 | ✅ | release 启用 `isMinifyEnabled=true` + `isShrinkResources=true`，补充 ProGuard 规则 |
| 18 | `values-night/themes.xml` 冗余 | ✅ | 删除，DayNight 主题自动适配 |
| 19 | 缩略图全量读入内存 | ✅ | 改为流式传输：先读 2 字节检测 MIME，再 `copyTo` 流式输出 |

---

## 统计

| 类别 | 总计 | 已修复 | 待处理 |
|------|------|--------|--------|
| 安全 | 3 | 3 | 0 |
| 资源泄漏 | 2 | 2 | 0 |
| 硬编码字符串 | 3 | 3 | 0 |
| 性能 | 4 | 4 | 0 |
| 代码质量 | 4 | 4 | 0 |
| 最佳实践 | 3 | 3 | 0 |
| **合计** | **19** | **19** | **0** |
