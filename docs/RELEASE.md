# 发布指南（Release Guide）

本文档说明 FlowGallery 的版本规范与发布流程。CI（GitHub Actions）负责自动构建与发布，本页只描述**人为操作步骤**与**文档规范**。

---

## 1. 版本规范（Semantic Versioning）

格式：`MAJOR.MINOR.PATCH` → tag `vMAJOR.MINOR.PATCH` → versionCode `MAJOR*10000 + MINOR*100 + PATCH`

| 变更类型 | 示例 | 版本号 |
|---|---|---|
| 破坏性/大功能 | SMB 支持 | 1.0.0 → **2.0.0** |
| 新功能（向后兼容） | 索引、日志 Tab | 1.0.3 → **1.1.0** |
| 修复/小优化 | 缩略图修复、UI 微调 | 1.1.0 → **1.1.1** |

- tag 必须与 versionName 一致：`v1.1.0` ↔ versionName `1.1.0`
- versionCode 由 CI 从 tag 自动推导，**不要**手动改 build.gradle

---

## 2. 发布流程

```bash
# 1. 合并功能分支到 main（PR 流程）
# 2. 打 tag 并推送 → CI 自动构建 + 发布
git tag v1.1.0
git push origin v1.1.0

# 3. 等待 Actions 完成（约 2 分钟）
gh run watch

# 4. 验证 Release
gh release view v1.1.0
```

CI 自动完成：
- `assembleRelease`（正式签名，keystore 来自 GitHub Secrets）
- 上传 `FlowGallery-vX.X.X.apk` 到 Release
- 版本号从 tag 推导

---

## 3. Release Notes 模板

每个 Release 的说明按以下结构编写（中英皆可，建议中文）：

```markdown
## FlowGallery vX.X.X

**构建**：GitHub Actions 自动构建 · 正式签名

### ✨ 新功能
- 描述功能（提交 hash 可附）

### 🐛 修复
- 描述修复

### 🔧 其他
- 说明（依赖升级、性能等）

### 📦 安装
- 下载 `FlowGallery-vX.X.X.apk` 安装（正式签名，可覆盖安装旧版本）
```

### 写作规范
- **新功能**：面向用户的描述（做了什么、怎么用），不写实现细节
- **修复**：写明"修复了什么问题"
- **已知问题**：如有，单独一节 `### ⚠️ 已知问题`
- **图片**：可选附 UI 截图（拖入 Release 编辑页）

---

## 4. 分支策略

| 分支 | 用途 | 合并方式 |
|---|---|---|
| `main` | 稳定发布分支 | 仅通过 PR 合并 |
| `feat/*` | 功能开发（如 `feat/smb-support`、`feat/ui-ux`） | PR → main |
| `fix/*` | 修复分支 | PR → main |

- 功能分支**必须**经过 PR review 后合并
- 每个功能独立分支，互不阻塞

---

## 5. 安装与验证

- APK 路径：`app/build/outputs/apk/release/app-release.apk`（本地构建）
- Release 资产：`FlowGallery-vX.X.X.apk`（CI 构建）
- 验证签名：`apksigner verify --print-certs app-release.apk`
- 真机安装：`adb install -r FlowGallery-vX.X.X.apk`

---

## 6. 历史 Release

| 版本 | 内容 | 日期 |
|---|---|---|
| v1.0.0 | 首个正式版（瀑布流、图包、查看器） | 2026-08-15 |
| v1.0.1 | 修复/图标 | 2026-08-15 |
| v1.0.2 | 自更新 + 版本显示 | 2026-08-15 |
| v1.0.3 | 悬浮胶囊、双击缩放 | 2026-08-15 |
| v1.1.0 | UI/UX 优化（查看器 Pager、全屏、播放按钮） | 2026-08-16 |
