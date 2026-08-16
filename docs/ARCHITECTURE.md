# FlowGallery 架构文档

版本：v1.0（对应 feat/folder-source 分支，2026-08-16）

---

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose（Material 3，BOM 2024.12.01） |
| 构建 | AGP 8.9.2 · Gradle 8.11.1 · compileSdk 36 · minSdk 26 |
| 图片 | Coil 2.7.0（coil-compose + coil-video） |
| 视频 | Media3 ExoPlayer 1.4.1 |
| 持久化 | SharedPreferences（文件夹/偏好）+ JSON 文件（索引/扫描缓存） |
| 发布 | GitHub Actions（tag `v*` 自动构建签名 APK → Release） |

---

## 2. 模块结构

```
app/src/main/java/com/flowgallery/app/
├── MainActivity.kt          # 入口：导航骨架、覆盖层管理、返回键分层、Toast
├── FlowGalleryApp.kt        # Application：Coil ImageLoader 注册
├── data/
│   ├── model/Models.kt      # Folder / ImageItem / SubFolder / ViewerState / 枚举
│   ├── source/              # ★ 文件夹源抽象层
│   │   ├── SourceType.kt    #   LOCAL / SMB / FTP / SFTP / WEBDAV
│   │   ├── FolderSource.kt  #   源接口：listFiles / openStream / testConnection
│   │   ├── ScanEntry.kt     #   目录项（含第一层子文件夹归属）
│   │   ├── SourceRegistry.kt#   类型 → 实现注册表
│   │   └── LocalFolderSource.kt  # SAF 实现
│   ├── index/               # ★ 元数据索引层
│   │   ├── IndexEntry.kt    #   索引条目（宽高/时长/大小/时间/MD5）
│   │   ├── IndexStore.kt    #   持久化（filesDir/index.json）
│   │   └── MediaIndexer.kt  #   增量索引器（复用/提取/自愈）
│   ├── repository/ImageRepository.kt  # 文件夹持久化 + 扫描编排 + 去重
│   ├── SmartVideoFrameDecoder.kt      # 视频黑帧跳过缩略图解码器
│   └── Updater.kt           # 自更新（GitHub Releases）
├── viewmodel/GalleryViewModel.kt      # 状态编排：扫描/索引/导航/收藏
└── ui/
    ├── theme/               # 深色主题（#0a0a0c 背景、#7c5cff 强调）
    ├── components/          # ImageViewer（Pager 查看器）/ WaterfallGrid / 弹窗
    └── screens/             # Home / Favorites / Index / Settings / Search / Logs
```

---

## 3. 数据流（核心链路）

### 3.1 扫描 + 索引 + 首页

```
启动
 ├─ init: IndexStore.load() → mediaIndex（内存索引）
 ├─ loadScanCache() → 立即显示上次内容（applyIndex 填充维度）
 └─ refreshFolders() → rescan（后台）
      ├─ SourceRegistry.get(folder.source).listFiles(folder)   // 零 IO 列目录
      ├─ 构建 ImageItem（width=0，维度由索引补）
      ├─ applyIndex(images)：用 mediaIndex 填充维度（只填完整 w>0&&h>0）
      ├─ 兜底：匹配失败/无效的 item 保留旧维度（不闪变 0）
      ├─ needsIndexing(images)？
      │    ├─ 有未索引/变更/到期重试 → 自动索引 + Toast「正在索引新内容…」
      │    └─ 全部已索引 → 跳过（不空转）
      └─ saveScanCache（下次启动秒显）
```

### 3.2 索引器（MediaIndexer）

```
merge(items, existing, force, onProgress, onCancelCheck)
 ├─ 复用条件：entry 存在 && 完整维度(w>0&&h>0) && size/mtime 未变
 │   （0 维度坏条目不复用 → 自动重新提取 → 自愈）
 ├─ 提取：图片=BitmapFactory bounds；视频=MMR 宽高+时长（缺维度用首帧兜底）
 ├─ 哈希：MD5（按 source.openStream 读取）
 └─ 持久化：IndexStore.save（index.json）
```

### 3.3 查看器（ImageViewer）

```
HorizontalPager（key=item.id，userScrollEnabled=!multiTouchActive）
 ├─ 图片页 → ZoomableImage（单手势循环）
 │    ├─ 双指捏合：zoom=target.coerceIn(1f,4f)，缩小自由回 1x，首帧跳过
 │    ├─ 双击：以点击处为锚点（视口中心 pivot 公式）放大 coverScale/还原
 │    ├─ 放大后单指：平移（clampOffsets 边界约束）
 │    └─ 单击：切换工具栏（300ms 延迟防双击误触）
 ├─ 视频页 → VideoPlayerView（Media3，单一实例跨全屏，进度保留）
 └─ 底部：缩略图条 + 文件名/分辨率（直接读 currentItem）
```

---

## 4. 文件夹源抽象（feat/folder-source）

**目标**：扫描/索引/读取全链路通过源接口，新增后端（SMB/FTP/SFTP/WebDAV）零侵入。

```kotlin
interface FolderSource {
    val type: SourceType
    suspend fun listFiles(folder, onProgress): List<ScanEntry>
    fun openStream(item): InputStream?
    suspend fun testConnection(config): Result<Unit>
}
```

**新增一个源**（如 SMB）：
1. `class SmbFolderSource(ctx) : FolderSource` — listFiles 用 jcifs 列目录、
   openStream 返回 SMB 流、testConnection 探测共享
2. `SourceRegistry` 注册：`put(SourceType.SMB, SmbFolderSource(ctx))`
3. 添加文件夹 UI 增加 SMB 配置弹窗（服务器/共享/账号/密码）
4. 图片加载：Coil 自定义 Fetcher（model 走 smb://）· 视频播放：自定义 DataSource

---

## 5. 索引系统（v1.1.1）

- **增量**：只处理新增/变更文件（size+mtime 对比），已索引秒过
- **自愈**：0 维度坏条目不复用，自动重新提取；失败条目 24h 节流重试
- **智能触发**：needsIndexing 只在有真新内容时跑自动索引 + Toast
- **持久化**：`index.json`（索引）+ `scan_cache.json`（上次扫描，启动秒显）
- **消费方**：首页比例/HD 徽章/排序、去重（优先用索引 hash）

---

## 6. 发布流程

```
合并 PR → main → git tag vX.Y.Z → push → GitHub Actions：
  checkout → JDK17 → Android SDK 36 → 解码 keystore（Secrets）
  → assembleRelease（版本号从 tag 推导）→ 上传 APK → 创建 Release
```
详见 `docs/RELEASE.md`。

---

## 7. 设计约定

- 深色主题：背景 `#0a0a0c`，表面 `#141418`，强调 `#7c5cff`
- 弹窗风格：拖拽把手 + 图标徽章 + 圆角按钮（统一组件样式）
- 双/三列瀑布流（可切）、底部导航 4 Tab（首页/收藏/索引/设置）
- 查看器：图片/视频交互一致（Pager 滑动、双击/捏合缩放、视频全屏）
- 所有内容变更同步 `docs/PRD.md`（提交前）
