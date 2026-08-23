# Epinoia 桌宠 🧡🐱

一个运行在 **Android 悬浮窗**里的方块小桌宠：橙色方块小人，会眨眼、会说话、会自己卖萌，还能像悬浮球一样贴边待着。

> 由 DeepSeek AI 助手与用户共同迭代开发（8 个版本打磨而成）。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🧡 **方块形象** | 橙色横向方块（52×24）+ 黑眼睛 + 同色方手 + 深橙腿，无角无耳无影 |
| 💬 **气泡说话** | 头顶独立气泡图层，点击/双击/长按/拖拽/甩动都会回应 |
| 🎭 **自主小动作** | 每 5~10 秒随机卖萌：开心❤ / 犯困zZz / 生气💢 / 委屈💧 / 得意✨ / 跳跃 / 摇摆 |
| 🎈 **悬浮球模式** | 无操作 10 秒自动贴边 + 50% 半透明；一碰弹回；拖动永不超屏 |

## 📱 安装

从 [Releases](https://github.com/Qin-cl24/my-first-repo/releases) 下载最新版 APK 安装：

1. 允许"未知来源"安装
2. 授权**悬浮窗** + **通知**权限（设置 → 应用 → Epinoia 桌宠）
3. 点开应用图标 → 桌宠上线

> 若提示"签名不一致"，先卸载旧版再安装。

## 📦 版本历史

| 版本 | 亮点 |
|------|------|
| [v1.7.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.7.0) | ⭐ 自主小动作系统（当前最新） |
| [v1.6.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.6.0) | 悬浮球：自动贴边半透明、防拖丢 |
| [v1.5.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.5.0) | 窗口 64dp + 手 8×8 同色（形象定型） |
| [v1.4.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.4.0) | 手加强（深棕描边） |
| [v1.3.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.3.0) | 手可见性修复 |
| [v1.2.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.2.0) | 气泡移头顶 |
| [v1.1.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.1.0) | 手腿缩短 |
| [v1.0.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.0.0) | 首版可安装（修复崩溃） |

## 🛠️ 技术结构

- **悬浮窗**：`PetOverlayService`（前台服务 + WindowManager 悬浮窗，64×64dp，手势/贴边/半透明）
- **桌宠本体**：`assets/pet.html`（WebView 渲染，CSS 动画 + JS 交互 + 自主动作引擎）
- **CI**：GitHub Actions 自动构建 APK 并上传 artifact

## 📝 备注

- 形象规格与参数（贴边时间/透明度/留边宽度）见 `PetOverlayService.kt` 顶部常量，可自行调整
- 预览页 `pet_preview.html`（放大 5 倍 + 动作演示）可本地浏览器打开
