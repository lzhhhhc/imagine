# device_bridge —— Operit 本地 MCP 连接

目的：把「不需要抢手机屏幕」的操作放进 MCP 工具面，避免再用辅助功能/前台 UI 自动化。

## 文件位置
- Android 侧源码（导入/存放）：`/sdcard/Download/Operit/mcp_plugins/device_bridge/`
  - `index.js`（stdio MCP 服务，零依赖）、`package.json`、`README.md`
- Linux 侧运行目录：`~/mcp_plugins/device_bridge/`
- 配置：`/sdcard/Download/Operit/mcp_plugins/mcp_config.json`
  - `mcpServers.device_bridge`：`command=node`、`args=["index.js"]`、`env.WS_ROOT=<工作区>`
  - `pluginMetadata.device_bridge`：`type=local`、`disabled=false`、`isInstalled=true`
- 备份：同目录 `mcp_config.json.bak.*`

## 工具
- 工作区：`ws_list` / `ws_read` / `ws_write` / `ws_grep`
- 构建与终端：`gradle` / `sh`
- 网络：`http_get`
- 设备（需 adb 通道）：`adb_status` / `adb_connect` / `adb_shell` / `adb_screenshot` / `adb_ui_dump` /
  `adb_tap` / `adb_swipe` / `adb_text` / `adb_key` / `adb_launch` / `adb_stop` / `adb_install` / `adb_logcat`

## 已验证
- 通过 Operit 自己的 MCP 桥（TCP `127.0.0.1:8752`）注册并启动成功：`toolCount=20`、`ready=true`。
- 端到端调用成功：`ws_list`、`ws_grep`、`adb_status` 均返回真实结果。
- 桥调用脚本：`tools/mcp_bridge_call.py <tool> '<json>'`（App 注册表刷新前也能用）。

## 已知限制（重要）
- 插件跑在 Operit 的 Linux（proot）环境，**看不到 Android `/system`**，因此不能直接执行 `input`/`screencap`/`uiautomator`。
- 设备控制只能走 adb。当前设备：无 root（Shizuku 为 shell 级），`/data/misc/adb` 不可写，adbd 未开 TCP 端口，
  所以 adb 通道需要一次性手动开启：开发者选项 → 无线调试 → 使用配对码配对设备 → 再 `adb_connect 127.0.0.1:<端口>`。
  未开之前 `adb_*` 会返回明确提示，不会静默失败。
- App 的 MCP 插件注册表在启动时扫描并缓存。手动新增的插件需要 **重开 Operit 或打开 MCP 设置页** 后才会作为工具包出现。
  （注意：不要给它造 `market_install_markers` 标记，实测会破坏扫描结果。）

## 不抢屏幕的替代做法
- 设备检查优先用 `super_admin:shell`（Shizuku，shell 级），它不会把 Operit 拉到前台：
  `input tap/swipe`、`screencap`、`uiautomator dump`、`pm install`、`am start`、`logcat` 都可用。
- 只在确实需要看界面时截图；避免使用 `Automatic_ui_base:*`（辅助功能/悬浮窗会抢前台）。
