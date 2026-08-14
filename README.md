# FlowGallery 📱🖼️

一个 Android 瀑布流图包浏览器 —— 类似 macOS 上的 FlowVision，支持多文件夹图包目录的瀑布流浏览。

![FlowGallery](flowgallery-android.html)

## ✨ 功能特性

- 🌊 **瀑布流网格** — Pinterest 风格双列瀑布流，按图片真实宽高比自适应
- 📁 **多文件夹管理** — 通过系统文件选择器（SAF）添加任意图包文件夹，随时开关/删除
- 🗂️ **文件夹标签栏** — 快速切换单个文件夹或 "All" 聚合视图，带图片计数徽章
- 🖼️ **全屏查看器** — 点击缩略图进入，支持 1x–4x 缩放、平移、左右滑动导航、底部缩略图条
- 💜 **收藏功能** — 卡片和查看器均可收藏图片
- 🏷️ **HD/SD 质量徽章** — 自动按分辨率标记图片质量
- 📊 **统计栏** — 图片总数 / 文件夹数 / HD 数量
- 🔍 **搜索** — 按文件名模糊搜索当前图库
- ⚙️ **设置** — 文件夹开关管理、缓存清理
- 🌙 **深色主题** — Material Design 3，`#0a0a0c` 背景 + `#7c5cff` 紫色强调

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM (ViewModel + StateFlow)
- **图片加载**: Coil
- **文件访问**: Storage Access Framework (SAF)，无需存储权限
- **持久化**: SharedPreferences (JSON)
- **构建**: Gradle 8.11.1 + AGP 8.9.2

## 📦 构建

```bash
./gradlew assembleRelease
# 输出: app/build/outputs/apk/release/app-release.apk
```

## 🚀 使用

1. 安装 APK 后打开应用
2. 点击右下角 **+** 按钮 → **Add New Folder**
3. 在系统文件选择器中选中你的图包文件夹 → **USE THIS FOLDER** → **ALLOW**
4. 回到应用即见瀑布流！点击任意图片可全屏查看

## 📄 许可

MIT License
