# HomeMediaViewer 开发日报

> 日期：2026-09-19
> 任务：代码质量优化（安全/泄漏/性能/规范）

---

## 今日完成

| # | 任务 | 提交 | 说明 |
|---|------|------|------|
| 1 | 签名密码安全加固 | `f1f71ab` | 密码从 gradle.properties 移至 local.properties，build.gradle 移除硬编码回退 |
| 2 | HTTP 明文限定局域网 | `f1f71ab` | network_security_config.xml 仅允许 127.0.0.1/10.x/172.16.x/192.168.x |
| 3 | 修复 HttpURLConnection 泄漏 | `ae5c788` | try-finally + conn.disconnect()，bufferedReader().use {} |
| 4 | 修复 AssetFileDescriptor 泄漏 | `ae5c788` | afd.use {} 管理生命周期 |
| 5 | 硬编码字符串提取 | `24abe3d` | MainActivity/MediaServerService/MediaAdapter 共 13 处提取至 strings.xml |
| 6 | Regex 预编译 | `650f199` | Thumbnail 路径正则提为 companion object 常量 |
| 7 | 线程池有界化 | `a0bcff6` | CachedThreadPool → FixedThreadPool(cores.coerceAtLeast(4)) |
| 8 | DiffUtil 列表刷新 | `c1dd411` | MediaAdapter 改用 DiffUtil 替代 notifyDataSetChanged |
| 9 | MediaScanner 复用 | `07dc572` | 单个实例扫描 VIDEO + AUDIO |
| 10 | emptyHint 绑定修复 | `d68f644` | 改为 findViewById 绑定 XML 中的 empty_hint |
| 11 | API 33+ getSerializableExtra | `ebe5f35` | 类型化重载适配 |
| 12 | 删除 coil-compose 依赖 | `23dfba2` | 移除未使用的版本目录条目 |
| 13 | 禁用 allowBackup | `8186884` | 防止 adb 提取应用数据 |
| 14 | R8 混淆启用 | `eca637e` | isMinifyEnabled + isShrinkResources + ProGuard 规则 |
| 15 | 冗余 theme 删除 | `9f700bb` | 删除 values-night/themes.xml，DayNight 自动适配 |
| 16 | 缩略图流式传输 | `e052426` | 避免全量读入内存，MIME 检测 + copyTo 流式输出 |

---

## 优化清单统计

| 类别 | 总计 | 已修复 |
|------|------|--------|
| 安全 | 3 | 3 |
| 资源泄漏 | 2 | 2 |
| 硬编码字符串 | 3 | 3 |
| 性能 | 4 | 4 |
| 代码质量 | 4 | 4 |
| 最佳实践 | 3 | 3 |
| **合计** | **19** | **19** |

---

## 下一步

- CI/CD 配置（GitHub Actions 自动构建）
- 单元测试补充（MediaScanner、NsdHelper、NetworkMonitor 等）
