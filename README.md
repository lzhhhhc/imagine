# 绘世 Imagine

一个 **Jetpack Compose** 编写的 Android AI 绘画工作台：标准绘图、NovelAI 原生工作台、
图像修图、Seedance 视频分镜提示词，以及统一的「罗德岛终端」视觉主题。

- 应用名：**绘世**
- 包名：`com.lo.imagine`
- 最低版本：Android 7.0（API 24） · 目标版本：API 35
- 授权：MIT（见 [LICENSE](LICENSE)）

## 功能

| 模块 | 说明 |
| --- | --- |
| 标准绘图 | 提示词输入与润色、风格/画质/画幅、批量张数、多路并发、结果入库 |
| NAI 工作台 | NovelAI 原生接口：模型/采样器/步数、角色卡与场景融合、token 估算 |
| 修图 | 多参考图 + 逐图遮罩局部重绘、画质补齐、结果归档 |
| 导演台 | 与 LLM 逐步访谈生成 Seedance 分镜脚本，人物/环境素材库互通 |
| 作品库 | 生成历史浏览、预览、分享、继续编辑 |
| 设置 | 接口预设与模型选择、主题与气质切换、提示词模板、关于与制作人署名 |

## 主题系统

界面统一使用「罗德岛终端」设计语言，三个维度互相正交：

- **日夜模式**：`ARKNIGHTS_LIGHT` / `ARKNIGHTS_DARK`
- **气质（UiMood）**：冷白科技 / 深色战术 / 柔和插画 —— 只改轮廓与色调，不改日夜
- **圆角标尺**：全应用只允许 `PopRadius` 五档（10 / 12 / 16 / 20 / 胶囊），避免随手写圆角

图标为统一生成的 24 画布 / 1.7 线宽矢量字形，明暗共用同一份路径。

## 构建

需要 **JDK 17** 与 **Android SDK**。

```bash
# 首次：为 ARM64 环境准备 aapt2（脚本会自动替换 SDK 与 Gradle 缓存中的二进制）
chmod +x ./setup_android_env.sh
./setup_android_env.sh

# 编译 Debug APK 并运行单元测试
./gradlew :app:assembleDebug :app:testDebugUnitTest

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

Windows 使用 `gradlew.bat`。

## 配置

API 地址与 Key **不在代码中**，全部由应用内「设置 → 绘图引擎」填写并保存在本机 DataStore。
仓库内不含任何密钥、令牌或私有端点。

## 文档

- [NAI_WORKSPACE.md](NAI_WORKSPACE.md) —— NAI 工作台设计与状态机
- [NAI_ADAPTER.md](NAI_ADAPTER.md) —— NAI 原生接口适配层
- [ARKNIGHTS_THEME_REFACTOR.md](ARKNIGHTS_THEME_REFACTOR.md) —— 主题改造记录
- [MCP_DEVICE_BRIDGE.md](MCP_DEVICE_BRIDGE.md) —— 设备调试桥

## 制作人

**长乐未央** · Discode ID: 1466791961003294804
