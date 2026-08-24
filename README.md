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
| 🫶 **连击计数** | 2 秒内连戳 3/5/8 次触发递进反应（三连击→五连击→八连击！） |
| 🌙 **时段感知** | 深夜催睡 / 早晨温柔 / 中午提醒，按小时换说话风格 |
| 😴 **孤独递进** | 5/10/15/20 分钟无互动：偷看→吹泡泡→打瞌睡→睡着，一碰就醒 |
| 💧 **喝水提醒** | 每 2 小时提醒，越不理越凶（卖萌版） |
| 🏃 **边缘跑** | 每 30~60 秒随机沿屏幕边缘滑跑一段，然后贴边 |
| 🎧 **听歌自嗨** | 戴上耳机 🎧 左右摇摆，周围飘小音符 ♪♫ |
| ✨ **动作弹出** | 贴边半透明时做小动作会先弹出来刷存在感 |
| 🙈 **躲猫猫** | 每 3~7 分钟随机淡出消失，在屏幕随机位置突然现身 |
| 😲 **更多动作** | 惊吓!/思考?/庆祝🎉/撒娇🥺/小跑……动作池共 13 种随机出现 |

## 📱 安装

从 [Releases](https://github.com/Qin-cl24/my-first-repo/releases) 下载最新版 APK 安装：

1. 允许"未知来源"安装
2. 授权**悬浮窗** + **通知**权限（设置 → 应用 → Epinoia 桌宠）
3. 点开应用图标 → 桌宠上线

> 若提示"签名不一致"，先卸载旧版再安装。

## 📦 版本历史

| 版本 | 亮点 |
|------|------|
| [v1.11.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.11.0) | ⭐ 5 个新动作：惊吓!/思考?/庆祝🎉/撒娇/小跑（动作池 13 种）（当前最新） |
| [v1.10.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.10.0) | 动作弹出（贴边时做动作会弹出来）+ 躲猫猫（随机消失→随机位置现身） |
| [v1.9.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.9.0) | 边缘跑（随机沿屏幕边缘滑跑）+ 听歌自嗨（🎧摇摆+音符） |
| [v1.8.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.8.0) | 4 大新功能：连击计数 / 时段感知 / 孤独递进 / 喝水提醒 |
| [v1.7.0](https://github.com/Qin-cl24/my-first-repo/releases/tag/v1.7.0) | 自主小动作系统 |
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
