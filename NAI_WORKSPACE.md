# 独立 NAI 工作台

入口：普通创作页标题旁「切换 NAI」。NAI 页「普通模式」返回。底部导航保留五项。

## 页面结构（四个等宽 tab）
- **生图**：最上面是画面提示词（右下角有四角星悬浮按钮，向左展开「撤回 / 翻译 / 润色」）→ 画师串（折叠下拉 + 预设）→ 生成参数 → 生成按钮 → 结果。**没有**最终正向预览块。
- **预设**：固定提示词（提示词预设 + 固定正面/后置固定正面/固定负面，各带 512 token 估算）、提示词替换、质量预设（AQT/UCP + 福瑞数据集）。
- **角色**：角色与服装（替代原来的「连接」tab）。每个角色由 中文名 / 英文名 / 角色特征 / 五官外貌 / 服装 / 补充 / 角色负向 / X-Y 坐标 组成，字段拼成该角色的提示词；下拉切换角色，最多 6 个。
- **更多**：连接（跟随后台 API + 高级开关）、配置档案、LLM 润色、请求检查。

## 连接与 LLM
- 连接**默认跟随设置页的绘图 API**（`useBackendApi = true`，取设置页的 baseUrl + apiKey）；只有走独立 NAI 端点时才关掉跟随，改填 NAI Token/Endpoint。
- `completeNaiEndpoint()`：只填到站点根（如 `https://relay.example`）时自动补 `/ai/generate-image`；已带路径（`/api`、`/v1/images/generations` 等）原样保留，不二次拼接。
- 超时：生图 `client` 30/180/210s（connect/read/callTimeout），文本 LLM `llmClient` 15/60/75s；普通模式共享客户端补了 writeTimeout 120s + callTimeout 240s，杜绝上游慢速滴流导致「永远转圈」。
- 页面看门狗：`busy` 超过 220 秒强制 `cancelWaiting()` 并给出「中转/上游卡住」说明；「检查最终请求」额外显示**实际请求地址**。
- 失败文案带状态码解释（401/403 鉴权、429 限流、502/503/504 上游不可用），并原样带出中转的纯文本错误体（如 Cloudflare `error code: 502`），Token 脱敏。
- LLM 润色复用设置页的润色 LLM 配置，不另填地址与 Key。
- 解析函数 `naiEffectiveEndpoint` / `naiEffectiveToken` 为纯函数，单测覆盖。

## 模式与输入框操作
- **模式记忆**：进入 NAI 页即记住当前模式，切到别的底部 tab 再回到「创作」仍回 NAI 页；点「普通模式」会先清记忆再回普通页，之后不会被拽回 NAI。只在进程内存里记，杀进程后回普通模式（配置本身仍持久化）。
- **输入框四角星**：画面提示词右下角的四角星，点击向左展开「撤回 / 翻译 / 润色」。撤回基于快照栈（打字停顿 900ms 记一份，翻译/润色应用前再记一份，栈深 40，空栈时提示）；翻译走设置页 LLM 的中英互译，保留 tag 与 `1.2::name::` 权重结构。

## 提示词注入
- 顺序与参考项目一致：数据集前缀（fur dataset） → 画师串 → 固定前置 → 主体 → 固定后置 → 质量词。
- 画师串走 `assembleNaiPrompt` 的 `artists` 形参（对齐参考项目「前置前」注入位），不再拼进正文 raw；正文里 `1girl`/`solo` 这类身份词也不再被提前，用户书写顺序即最终顺序。
- 提示词替换：每行「原词=新词」，兼容「触发词=位置|替换词」（取竖线后的替换词），作用于固定词、主体、画师串与负面词。
- 质量词由本应用按模型版本注入，`qualityToggle=false` 防重复；5 系额外发送 `tag_hint_uc_preset` / `tag_hint_qt` / `straight_alpha`。
- `skip_cfg_above_sigma` 由「多样性」开关控制（4.5 为 19，5 系为 58）。
- 角色写入 `v4_prompt`、`v4_negative_prompt`、`characterPrompts`，角色提示词由特征/五官/服装拼装。

## NAI 专属提示词工程（`NaiPolish.kt` 的 `NaiTask`）
- `NaiTask.POLISH` 润色：`naiPolishInstructions(profile, depth, artists, style)`，按 4.5 / 5 与 LIGHT/MEDIUM/DEEP 分档；工作台润色固定用 MEDIUM 档，并带上已选画师串作为上下文（应用另行注入，不让模型重写画师）。
- `NaiTask.CAPTION` 反推：`naiCaptionInstructions(profile)`，只看可见信息、不猜作者、不写质量词、不输出负面词，按「人数身份 → 外貌 → 服装 → 姿态 → 镜头 → 场景 → 光影」出单行标签流。`ImageRepository.reversePrompt` 在目标模型是 NAI 时走这条，通用八维度反推只留给非 NAI 模型。
- `NaiTask.TRANSLATE` 翻译：`naiTranslateInstructions(profile, direction)`，只搬语义，保留 `1.2::artist:name::` 权重、括号强调与 `|` 分区，不重排不合并。
- 工作台「LLM 润色」里填的任务 system 在 NAI 模型下只作为「用户附加要求」追加，不再覆盖专属规则；非 NAI 模型仍走设置页通用模板。

## 等待与卡死防护
- `NaiWorkspaceState.busy` 由 `run(scope, client) { }` 在协程内部置位/复位：作用域已取消（点得太快、页面正在销毁）时不会留下"永久转圈"。
- 等待期显示 `阶段 · 已等待 Ns`（保存配置 / 请求出图 / 解码图片 / 写入作品库）+「取消等待」按钮；取消会同时 `job.cancel()` 与 `NaiNativeClient.cancelActive()`（OkHttp `execute()` 不受协程取消影响，必须显式掐连接）。
- 离开 NAI 页（`DisposableEffect.onDispose`）与重新进入（`reconcile`）都会对齐等待态；「普通模式」按钮不再被 busy 锁死。
- 出图请求前不再写设置存储（配置由 `ImagineApp` 的 `snapshotFlow` 收集器落盘），收集器对每次写入 `withTimeoutOrNull(5s)` 并吞掉失败，避免 DataStore 无响应把整页/整条生成链路挂住。
- 配置读取 `naiWorkspaceFlow().first()` 也有 5 秒超时；超时报错并按内存默认值进入，不再无限 `LinearProgressIndicator`。
- 润色/翻译走 `llmClient`（15/60/75s），不再复用生图的 180/210s 超时。
- Endpoint 只有站点根路径时直接报错提示补 `/ai/generate-image`，避免请求挂到超时。

## 出图落盘
- 生成/修图完成后走统一入口 `ImageUtils.archiveResult`：先写应用内历史（作品库），再写系统相册 `Pictures/Imagine`。
- 设置页「生成」组有**出图自动存相册**开关（默认开启）；关闭后仅写作品库。
- 相册写流失败会删除挂起行，避免相册留下永远不可见的记录。

## 范围
未加入 Vibe Transfer、角色参考图、图生图、局部重绘、云端队列与聊天触发。
主提示词中禁止 `|`，多角色请用角色卡。当前任务绑定页面作用域，离开会取消本地等待。

## 参考与验证
参考 https://github.com/damoshen123/st-chatu8 的 `html/settings/novelai.html`、`html/settings/character.html` 与 `main/index.js`：分区结构、AQT/UCP 质量词、采样与噪点表、配置档案、提示词替换语法、注入顺序、角色与服装字段、v4 角色参数。独立 Kotlin 实现，未打包第三方源码。
47 项单元测试通过（含画师串先于身份词/主体、NAI 三套任务提示词互不相同、busy 不会因作用域取消而残留），assembleDebug 成功。设计系统分层：`PopRadius` 五档圆角标尺（chip 10 / field 12 / card 16 / sheet 20 / pill 999）统一造型尺寸；`theme/PanelMaterial.kt` 提供主题材质层（波普半调网点+双色印刷条 / 深夜左上辉光+顶部发光线+星点 / 像素机台标签条+跑马灯+CRT 扫描线 / 包豪斯三原色块+黑粗线），已接入 `Panel`、两个弹窗、设置页 `SettingsConsoleGroup`；`ScreenHeader` 增加主题装饰条（波普双色条 / 深夜发光渐线 / 像素扫描轨），`SectionMarker` 六主题各一套卷首符，`AppDock` 底栏加主题轨道，`PopMediaCard` 加主题边缘语言，`ImagineApp` 六套页面转场；主题选择器预览色已与真实 ColorScheme 对齐（此前波普/动漫/包豪斯/罗德岛预览色全部错误）；「跟随系统」主题整体下线（枚举项、AppSettings 默认值、ThemeMode.fromId 回退三处一并移除，旧存档 system 归一为 paper），主题卡片缩至 76dp 宽、42dp 高。性能与污染修复：装饰动画全面静态化（面板材质层/分组标记/页头/底栏/徽章/图标按钮不再挂 rememberInfiniteTransition，动效只保留屏幕背景与加载态，避免一屏十几组无限动画把整机拖成幻灯片）；首页润色不再被 NAI 工程污染（naiMode 仅在 naiProfile != null 即显式开启 NaiOptions 时启用，genModel 带 nai 或选过 NovelAI 模板不再触发——画师串因此不再被塞进润色 system、不再回显）。性能与污染修复：动效全面收敛——面板材质/分组标记/页头装饰/底栏全部改为静态绘制（每屏只保留背景氛围与加载态两类动画），按主题加的横滑/慢淡入转场撤回统一淡入缩放（反馈「像一堆 PPT」）；首页润色不再被 NAI 污染——naiMode 与 NOVELAI 模板只跟显式 NaiProfile（NAI 工作台）绑定，genModel 是 NAI 中继模型名或残留 NovelAI 模板选择均走通用工程，画师串不再作为上下文传给润色 LLM（此前会被回显进首页提示词）。模型预设实测复核（无 Key 探测端点 + 公开模型表核对）：移除阿里云百炼图像预设（compatible-mode 无 images/generations，chat/models 均 401 而 images 404）；OpenRouter 预设模型由虚构的 black-forest-labs/flux.1-schnell:free（模型表 443 个中 FLUX 为 0）改为真实存在的 google/gemini-2.5-flash-image；硅基流动默认模型改为 FLUX.1-Kontext/dev 且修图协议改 generations_image（实测无 /images/edits 端点，旧默认必 404）。绘图引擎弹窗交互重构：删除与「连接档案·平台预设」重复的「服务身份」下拉（同一件事两个入口，上一轮合并未做干净）；修图协议改为跟随平台预设自动设置并显示说明（选错协议=404，不再暴露给用户乱点），仅自定义/本地预设连接显示切换 chips；选中预设后自动拉取该端点模型列表（有 Key 时，两个弹窗同规则），模型选择不再需要手动找刷新按钮。概念重构——供应商与预设分离：AppSettings 移除 providerId 身份字段（旧键 provider 迁移为 lastPresetId，仅作连接档案选中态高亮），供应商改为 PlatformPreset.platformOf(baseUrl) 从端点实时推导（换模型不改端点=供应商不变；表外端点自然为 CUSTOM）；修图协议是否可切换、设置页入口卡副标题均以推导结果为准。预设交互再重做：删除 LLM「常用通道」内置列表（写死的通道会过期，是噪音）；连接预设改为单一下拉（绘图＝平台+本地预设，语言＝本地档案），新建/重命名/删除全部行内完成，四个命名与删除弹窗全部移除——选择预设、编辑预设全流程零弹窗；内置预设不再参与选中态判断，平台身份仍由 baseUrl 推导。内置平台预设全部移除：删除 PlatformPreset 枚举（8 个平台的内置地址/模型/协议）与 lastPresetId 字段，预设只保留用户自建（绘图 CustomPreset、语言 CustomLlmPreset）；AppSettings 默认 baseUrl/model 置空、editMode 默认 generations_image，不再引任何内置表；修图协议不再按平台自动判定，始终由用户切换并附选择说明。双通道模型切换：NAI 页头右上角新增本通道模型切换（TinyBadge → NAI_NATIVE_MODELS 选择器，含 naiModelLabel 显示名），只改写 NaiWorkspaceState.config.model，与首页绘图接口/模型互不影响；首页切换弹窗补上通道说明。两条通道各自持有模型：首页=settings.model（预设切换），NAI=工作台 config.model。双通道改为「同一份预设、各记各的选择」：NAI 页头角标与首页同款，点开是同一份用户预设列表；NAI 通道新增独立选中记忆（DataStore 键 active_nai_preset_name）与首页的 active_preset_name 分开，互不影响；选中预设即把地址/Key/模型应用到工作台并关闭「跟随首页」，两条通道从此各用各的连接。「跟随」开关文案改为通道语义。首页画师串污染修复：普通模式 generate() 不再拼接任何画师串（画师串只属于 NAI 工作台 c.artists）；移除首页幽灵链路——旧版遗留的 artistString 回填/防抖写回/StudioState 声明/仓储读写方法全部删除，旧存档键 artist_string 由 clearLegacyArtistString() 一次性清理；删除死文件 NaiPromptPanel.kt。SKY 渲染轨重做：删除九宫格糖果色方块（九种彩色方块并排，观感像调色盘而非加载动画），改为一条柔光沿 3dp 细轨缓缓往返——光带两端渐隐、头部呼吸光点、Reverse 往返零硬切；输出设置的分辨率徽章去 px 后缀、x 改 ×，避免长文本溢出。生成中提示词区收起：首页在生成中（loading）整个提示词编辑区（操作行＋输入框）收起，不再整段占屏；等待出图时页面只保留输出设置与生成状态，避免大段文字噪音。NAI 文本任务进度就地显示：润色/翻译进行态改为与首页同款——遮罩+转圈+文字就地盖在提示词框上（点完操作不用滚到页面底部找进度），底部进度块只保留出图任务；反推走独立弹窗、进度本就在弹窗内。未调用付费 NAI/LLM 服务，真实端点成图与角色效果待验证。
## 主题系统精简至罗德岛单主题（2024-09-12）

**主题删除与精简**：
- 删除波普/动漫/深夜/像素/包豪斯五套主题的全部痕迹：`ThemeMode` 枚举从 6 项精简为 2 项（`ARKNIGHTS_LIGHT` 罗德岛·日间 / `ARKNIGHTS_DARK` 罗德岛·战备），旧存档的主题 ID 一律映射为 `ARKNIGHTS_LIGHT`。
- `Theme.kt` 删除 `PaperColors`/`MidnightColors`/`PixelColors`/`BauhausColors`/`AnimeColors` 五套 ColorScheme 与对应的 Flavor/Typography，保留 `ArknightLightColors`（primary `#007A99`、background `#F2F4F5`、surface `#E8EAEC`）并新增 `ArknightDarkColors`（primary `#18D1FF`、background `#000000`、surface `#0D1012`）。
- `BackdropKind` 枚举整体删除（原有 `POP_DOTS`/`PIXEL_GRID`/`BAUHAUS_GEO`/`NIGHT`/`ARK_TERMINAL`/`SKY` 六个值），`FlavorSpec.backdrop` 字段移除，所有 `when(backdrop)` 分支全部删除。
- `UiKit.kt` 删除 6 个 Sky 专属组件（`SkyScreenHeader`/`SkyPanel`/`SkyBusyIcon`/`SkyRenderLoader`/`SkyLoadingLabel`/`SkyGenerationStatus`，共 -409 行），又删除 `popBackdrop` 里 5 个旧主题背景分支（-365 行）。
- `PanelMaterial.kt` 整个文件删除（原有 4 套旧主题材质绘制），`Panel`/`PopErrorDialog`/`PopWarningDialog`/`SettingsConsoleGroup` 里的 `PanelMaterial(...)` 调用全部移除。
- 跨文件清理：`PopMediaCard.kt` 简化硬朗直角卡片；`ImagineApp.kt` 删除主题判断变量，转场统一为罗德岛横向部署；`SettingsConsole.kt`/`DirectorScreen.kt`/`HistoryScreen.kt`/`IconTheme.kt` 中所有主题分支全部移除。

**背景纹明暗适配**：
- `popBackdrop()` 通过 `MaterialTheme.colorScheme.background` 亮度判断明暗模式（`luminance() < 0.5f` 为暗色版），动态切换背景纹配色。
- 暗色版：青色从 `#00A8CC` 改为更亮的 `#18D1FF`，网格线 alpha 从 .035 提到 .08，幽灵文字从深灰改为亮青，所有装饰元素透明度提升，强化夜间作战 HUD 感。
- 浅色版：保持原有冷白舱室 + 深青 HUD 装饰。

**官方图标嵌入**：
- 从 arknights.wiki.gg 下载 5 张官方图标至 `res/drawable/`：阿米娅（30K）、凯尔希（38K）、博士（17K）、德克萨斯（24K）、Savage（29K）。
- AppDock 顶部滑轨左侧嵌入阿米娅圆形头像（20dp，青色边框，浮于滑轨上方）作为品牌标识。
- 其余图标暂未接入，预留给未来的空态插画、加载动画等嵌入点。

**验证与状态**：
- `compileDebugKotlin` BUILD SUCCESSFUL（0 error，8 条 warning）。
- `assembleDebug` BUILD SUCCESSFUL，APK 23M 装机 **Success**。
- 未调用付费服务，真实端点效果待验证；明暗双版切换链路未实测。

## 对标官方 UI 深度风格化（2024-09-12 第二批）

依据官方四张截图（加载页/菜单页/设置页/主页）拆解设计语言后落地：
- **黄色灵魂**：secondary 改罗德岛黄（暗 `#FFD800` / 浅 `#C9A227`），popBackdrop 装饰线同步；背景 56.5% 高度加横贯黄线（致敬加载页灵魂线）。
- **平行四边形斜切**：新增 `ArkSlantShape(slant: Dp)`（Theme.kt），SectionMarker 主块、PopSwitch 外框、AppDock 按钮（slant 7dp）全部改用倾斜语言，替代温和的 45° 小切角。
- **分组彩条**：SettingsConsoleGroup 左缘 3dp 彩条（橙/青/黄/红/蓝，按标题 hash 稳定取色），对标官方菜单卡。
- **幽灵档案框**：popBackdrop 右上加 NO INFO/ 细线框+灰字。
- **卢恩文装饰**：页头 `ᚱᛖᛋ // NODE 01`、卷首 `ᛟᛖ ᛋ`。
- **斜挂助理相框**：新增 `ArkHangingFrame`（挂绳+木框+白衬+阿米娅，-6° 斜挂），挂设置页页头右侧，**点击切换明暗主题**（挂件即开关，非纯装饰）。
- **分段滑块开关**：PopSwitch 重做为官方「开启｜关闭」两段式，选中段填充 primary+细网格纹，未选中段深灰。
- **3D 线框加载动画**：ArkRenderLoader 重做为旋转正二十面体线框（12 顶点/30 边预计算，绕 Y 旋转+固定 X 倾角，6s 一圈）+18 颗星点，对标官方加载页灵魂动画。
- 验证：compileDebugKotlin / testDebugUnitTest / assembleDebug 全过，Shizuku 装机 Success。

## HUD 语法重构（2024-09-12 第三批·学官方手法）

拆解官方四张截图的 HUD 构成后重构（用户反馈「还是丑」「学一下他的 hud 怎么做的」）：
- **背景纹做减法**：元素从 10 种砍到 6 种，全部退边角、中间留白。删除满屏网格（像方格纸）、左下梳齿、右上青角括号、右下青绿斜条、左侧竖排幽灵字、左下切角块、呼吸点圆。
- **新背景纹六件套**：①星点（仅暗版，稀疏固定伪随机）②横贯基准线+左段亮黄粗段（官方加载页灵魂：一条细线贯穿，左段更亮更粗像进度）③右上三段黄断线（信号强度语言，递减透明）④NO INFO 幽灵框（退左下）⑤右下角落锚点黄字 `RHODES ISLAND™ // VER 1.0`（官方 ID/版本永远锚右下）⑥极淡扫描带（唯一动效）。
- **页头宽字距英文副标题**：中文大字 + 全大写 letterSpacing 0.22em 灰色小字（SETTINGS/STUDIO/NAI WORKSPACE/ARCHIVE...，title 映射，不改调用方），替代原卢恩文——官方 HUD 文字层灵魂。
- **分段开关改直角**：官方开关段是直角矩形，斜切只留给按钮/卡片。
- **暗版面板半透明加深**：ArkPanel alpha .93→.78（按 background 亮度判断），星点/黄线/扫描带从面板下透出来形成层次——官方「半透明深灰面板浮在背景上」手法。
- 验证：assembleDebug 成功，Shizuku 装机 Success。角色立绘/头像类素材一律不用（用户明确否决）。

**NAI 体验三连修（2026-09-13）**：
- **切页不再中断生图**：任务从页面 `rememberCoroutineScope` + `onDispose{cancelWaiting}` 的旧模式，提升为 `NaiWorkspaceState` 的**进程级作用域**（`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`）；`NaiNativeClient` 也移到状态对象内共用同一实例。离开 NAI 页不再掐连接，切到别的页面生成继续跑，回来时进度与结果都在。取消只有两条路径：用户点「取消」或请求自身超时（210s 出图超时 / 220s 看门狗兜底）。
- **进度区精简**：出图等待区从「LinearProgressIndicator + 一长串说明小字（已等待 xx s·超时规则…）」改为**动画 + 「正在生图」** 卡片：`RunnerDinoLoader`（旋转正二十面体线框）+ 右侧「取消」文字按钮。阶段/秒数细节不再铺在页面上。
- **首页与 NAI 操作行对齐**：首页新增「撤回」（独立 `StudioPromptHistory` 栈，与 NAI 各自独立；打字停顿 900ms / 翻译 / 润色应用前记快照）；两页按钮统一为 `[撤回] [翻译] [图像反推] [润色] (清空)`；「AI 润色」全 App 精简为「润色」（首页/NAI/修图/导演/设置页文案，含导演页「润色结果」标题）。
- **NAI 结果只显示最近一批**：新增 `clearShots()`，生成开始时清空上一批，与首页「results = emptyList()」行为一致；历史图仍可从作品库翻。
- **测试适配**：`NaiWorkspaceStateTest` 迁移到 `runTaskIn(scope, ...)` / `reconcile()` 新签名；新增可注入作用域的 internal 入口供测试验证 busy/error 语义。
- 编译 / 单测 / assembleDebug / 装机全部通过。

**官网设计语言深度落地（2026-09-13，对标 ak.hypergryph.com）**：
- 扒官网抓资产：`rhodes_island.png`（罗德岛白 logo）+ `stroke_text-rhodes_island.png`（描边字 RHODES ISLAND）存 res/drawable。
- **页头 logo 带**：底部装饰条右侧加官方描边字（11dp 高、alpha .65）+ `// TITLE //`，官网 logo 语言。
- **背景右下锚点**：`RHODES ISLAND™ // VER 1.0` → `VER 1.0 //`（去掉冗余英文，保留黄色锚点）。
- **加载动画呼吸**：ArkRenderLoader 加 pulse 脉冲（1.1s 往返 .45→1.0），线框和黄点透明度随脉冲起伏——官方「系统在线」心跳感。
- **分组标记改双斜杠**：卢恩文 `ᛟᛖ ᛋ` → `//`（黄色加粗），对标官网 READ MORE // 按钮语言。
- **空状态用真 logo**：`EmptyResults` 从「Surface + PopGallery 图标」改为官方罗德岛 logo（56dp，alpha .85）+ 描边字（13dp，alpha .55）——官网加载页灵魂。
- 编译 / 单测 / 装机全部通过。

**官网排版/字体/页码系统落地（2026-09-13，用户发4张官网截图二轮对标）**：
- **斜向菱形网格**：popBackdrop 新增极淡白斜线交叉铺满全屏（slope 1.15、间距 .16w，暗版 alpha .055），对标官网 WORLD/ABOUT TERRA 背景网格——此前完全没有。
- **右侧全高竖线**：x=.885w，内容区/边栏分隔，对标官网右缘竖线。
- **页码系统**：popBackdrop 加参（ghostText/pageIndex/pageTotal），ImagineApp 按 route 映射每页英文名（STUDIO/RETOUCH/DIRECTOR/ARCHIVE/SETTINGS/IMAGINE）；右缘竖排「页名 // 01 / 05」+ 右下 sans-serif-black 大数字（01-05，青色）。VER 1.0 // 锚点从 Dock 后面上移到 NO INFO 框正下方（此前一直被 Dock 盖住不可见）。
- **超大幽灵字**：每页英文名以 sans-serif-black 斜体压在页面底部（size .17w，暗版白 alpha .08），对标官网 WORLD/KAL'TSIT 幽灵字。
- **排版/字体**：页头标题 FontWeight.Bold→Black（官网极粗字重）；档案页脚日期改官网双斜杠格式「yyyy // MM // dd」（formatTime，仅 HistoryScreen 使用）。
- 编译 / 单测 / 装机全部通过。

**修正「没变化」——装饰从背景层搬进页头（2026-09-13）**：
- 病根：上一轮把幽灵字/页码画在 popBackdrop（背景层），而暗版面板 alpha .78、浅版 .93 基本不透明，底部 Dock 又完全遮住 h*.86 以下区域——等于白画。截图自查已确认不可见。
- 修复：删除背景层的竖排页码/大数字/幽灵字（连同 ghostPaint），改为在 ArkScreenHeader 内以布局流渲染：标题下方 30sp sans-serif-black 斜体幽灵页名（alpha .16）+ 底部条右侧 `STUDIO // 01 / 05` 页码。popBackdrop 恢复无参签名。
- 提亮仍属背景的元素：菱形网格 alpha .055→.105（浅 .04→.065）、右缘竖线 .16→.30；NO INFO 框与 VER 1.0 // 从 h*.80/.885 上移到 h*.70/.79 避开 Dock。
- 页名映射：创作 STUDIO/01、NAI IMAGINE/01、修图 RETOUCH/02、导演台 DIRECTOR/03、作品(库)/历史 ARCHIVE/04、设置 SETTINGS/05；未知页沿用原 `// TITLE //`。
- 截图验证：页头已渲染 `STUDIO // 01 / 05` 与大号 STUDIO。

**配色去浑浊·提高级感（2026-09-13）**：
- 病根诊断：①灰阶层级挤成一团（暗版 #000/#0D1012/#1A1D1F 只差十几级）；②面板半透明（.78/.93）让背景网格、扫描带透上来与面板灰混色；③大量 `primaryContainer.copy(.45~.58)`、`surfaceVariant.copy(.4~.72)` 的半透叠加，在面板上再洗一层灰；④青+黄两个强调色到处抢，白底叠黄=米色糊。
- 重建明度阶梯：暗版 纯黑 → #0C1014 → #161D24 → #212A33，文字改纯白 #FFFFFF（原 #E8EAEC 偏灰）、线条改冷灰 #33404A/#222C34；浅版 纯白 → #F4F6F8 → #E8ECF0 → #DCE2E8，文字近黑 #0A0E12，描边由 #585858 重墨线降到 #7B8792 中灰。
- 强调色收敛：主强调色只留青（暗 #3EDCFF / 浅 #00708C）；黄压成深金 #8A6D00（浅）/ #FFD400（暗），且只出现在横贯基准线与分组小方块；右上三段断线由黄改青、分组 `//` 由黄改中性灰。
- 面板与控件全部转实色：ArkPanel alpha → 1f；TinyBadge/ArkBusyIcon/LoadingLabel/PopSwitch 轨道/下拉展开态/导演与修图选中块/设置与反推的 surfaceVariant、primaryContainer 半透叠加全部改实色；底栏未选中由 surfaceVariant@.38 改 surfaceContainerLow。
- 背景纹改纯白细线（暗版网格 White .075、竖线 White .16），不再用灰线发闷；ThemeChooser 预览卡同步为真实色值。
- 量化验证：截图取样强调色占比 2.09%（此前青黄叠加时明显偏高），明度主峰 240/248 与文字峰 8 分离清晰；编译 / 单测 / 装机通过。

**强调色色号修正（2026-09-13）**：
- 用户原话是「那个蓝色的色号太丑了」，本喵上一轮误读成「不要蓝色」而整体换成琥珀金，结果更土——已全部回退。教训：色相问题 ≠ 色系问题。
- 病根在 #3EDCFF：明度 100、饱和仅 73%，在黑底上发飘、像默认超链接。换成官方同源的 **#18D1FF**（饱和 89%、同明度），干净锋利。
- 明暗两版配套：暗 primary #18D1FF / onPrimary #00222B / primaryContainer #003A52 / onPrimaryContainer #9BEFFF；浅 primary #00699C / primaryContainer #CDEEFF / onPrimaryContainer #00243A。inverse 互为对方深淡调。
- 辅助色收敛为青的同族，剔除偏紫的通用蓝：PopAccentsArk 由 (青, 荧光绿, #3387FB, 薄荷, 灰) 改为 (ArkCyan #18D1FF, ArkIce #7FE7FF, ArkCyanDeep #0E7A96, ArkMint #00E0C7, 中性灰)；tertiary 不再用蓝，暗版取冷白 #DCE4EA、浅版取近黑 #2C353D。
- 背景 hudAccent、分组标记衰减线、预览卡同步为青族；黄只保留 secondary（暗 #FFD400 / 浅 #6B5400）作小面积点睛。
- 量化验证：截图取样 hue 195–210 占 8.30%（青蓝主强调），hue 45–60 仅 0.17%（金基本退场）；编译 / 装机通过。

**官网动效实测与落地（2026-09-13）**：
- 扒官网 CSS（gzip 解包后）拿到真实动效参数：①页面转场黑幕 `opacity .6s + visibility .6s`，内容 `transform: scale(1.1)→scale(1) .6s`；②横线生长 `transition: width .6s` + `transition-delay .6s`（先定位再画线）；③闪烁光标 `@keyframes opacity 0↔1 2s infinite`；④上浮入场 `translateY + opacity 1.5s`；⑤交互反馈统一 `.3s`（hover/选中/按压）；⑥卡片悬停 `scale(1.05~1.1)`；⑦内容入场 `translateX(-3rem)→0 10s forwards`。
- 落地 4 项：**页头入场**（左滑 -14dp→0、520ms + 底横线亮段 0→22% 生长 600ms，官网 width 生长语法）；**状态光标**（SYSTEM ONLINE 方点 opacity 闪烁，2s 无限往返，官网 @keyframes 同款）；**页面转场**（内容 scale 1.04→1 + fade 360ms / exit 220ms，对齐官网 .6s 节奏）；**按压反馈**（PopIconButton 按下缩到 .93、140ms；Dock 按钮按下 .94、弹簧）。
- 缓动统一 FastOutSlowInEasing，时长分档 140/220/360/520/600ms（对齐官网 .3s/.6s 两档并细分）。
- 编译 / 单测 / 装机通过。
## 高级动效继续增强（2026-09-14）

- `popBackdrop()`：保留官网式 3.6s 横向扫描基础上，新增半周期错峰的第二道窄扫描光束；中间黄基准线增加极小幅度的 760ms 呼吸，不改变配色重心。
- `ArkScreenHeader`：页头左侧 22% 亮段完成首次生长后，增加 2.8s 的窄扫光，仅在亮段内循环，避免整条线持续闪烁。
- `AppDock`：顶部滑轨由静态亮段升级为 2.6s 慢速扫光，底轨保留低亮固定轨道。
- `PopMediaCard`：新增可选 `animationDelayMs`；首次进入时 420ms 淡入并从下方 10dp 回落。NAI 结果网格按索引每张错峰 70ms，形成结果接收级联；其他调用默认 0，不改变旧行为。
- `ArkGenerationStatus`：等待卡边框按 1.1s 轻微呼吸；已完成进度轨内部增加 1.8s 窄扫光，未恢复长串等待说明。
- 验证：`testDebugUnitTest`、`assembleDebug` 通过；已安装真机。连续截图 SHA-256 不同，确认动态绘制真实生效。仅剩历史 warning 已清理。

## 修图遮罩链路收尾（2026-09-14）

- 链路确认：`MaskDrawView` 将触摸笔迹写入透明预览 Bitmap，抬笔回调到 `EditState.refMasks[index]`；`EditScreen.runEdit()` 逐图调用 `ImageUtils.encodeMaskPng()`；`ImageRepository.editImage()` 在 `edits_multipart` 中发送 `mask.png` multipart，在 `generations_image` JSON 中发送 `mask` data URI。
- 确定性修复：multipart `/images/edits` 被拒后转 JSON 图生图时，降级请求现在继续携带 `mask`，不再静默变成整图重绘。
- 确定性修复：修图图片超过 4096px 被 `prepareForEdit()` 降采样时，遮罩改按实际发送图片的宽高编码，避免图片与 mask 尺寸不一致。
- 协议边界：`edits_multipart` 是 OpenAI `/images/edits` 的标准遮罩路径；默认 `generations_image` 的 JSON `mask` 属于中转扩展字段，是否执行局部重绘取决于服务端实现，客户端无法保证上游使用该字段。
- 可测规则：`ImageUtils.maskOutputAlpha()` 锁定 DST_OUT 语义——未涂区域 alpha=255 保留，完全涂抹区域 alpha=0 重绘，中间 alpha 取反；真实 PNG Canvas 编码仍属于 Android 运行时，不在当前纯 JVM 测试中伪造验证。
- 验证：`testDebugUnitTest` 与 `assembleDebug` 均 `BUILD SUCCESSFUL`；新增 `ImageUtilsMaskTest` 覆盖 DST_OUT alpha 规则；构建产物已复制并安装为最新 APK，`pm install` 返回 `Success`，Activity 前台截图确认 Imagine 正常启动。未调用用户真实 API 或付费生图服务。

## 参考视觉稿主题 overhaul（2026-09-14）

- 用户自带参考稿（罗德岛白舱终端风），要求全 App 照抄视觉语言并做风格一致性适配。
- 新增令牌 `theme/Theme.kt::ArkRef`：钢蓝 #2E8CB5 / 墨黑 #16191C / 冷白舱底 #E9EBEC / 生成渐变 #1D6E8C→#0C3A4C / Dock 激活斜块渐变；浅色 ColorScheme 同步改为冷白底 + 钢蓝 primary。
- 新增组件文件 `ui/ArkRefUi.kt`：`ArkTileButton`（墨黑双语磁贴）、`ArkBlockAction`（页头墨黑动作块）、`ArkGenerateBar`（渐变生成条）、`ArkFooterStrip`（页脚标语+四格色板）、`ArkDockActiveTile`（斜切渐变徽块）、`ArkInkPanel`（墨黑面板容器）、`ArkTranslateGlyph`（文/A 字形）。
- 页头 `ArkScreenHeader` 重写为参考稿五段结构：徽标行（罗德岛徽+FOR/A BETTER/TOMORROW 与 !INTEGRATED/STRATEGIC/SOLUTIONS 双微字栏+右侧 TERMINAL OPERATOR SYSTEM）→ 页签行 → 42sp 斜体黑标题+右侧动作块 → 平台微字+五段进度 → 全宽状态行（钢蓝闪烁方点 SYSTEM ONLINE | 页名//0X/05）；右上叠加 `ark_industry` 工业桁架淡纹。
- Dock 重写：冷灰舱带+顶部细线；未激活=线性图标+编号中文+英文微字；激活=斜切渐变徽块（内嵌罗德岛徽）+编号中文黑体；底部接页脚色板条。
- 首页：提示词面板改 `ArkInkPanel` 墨黑容器（标题+PROMPT INPUT 微字、深色芯片、五磁贴 撤回/翻译/图像/润色/清空、暗输入框+字数微字行）；页头动作改「中转 TRANSFER」墨黑块；生成按钮改 `ArkGenerateBar`。
- 新增 10 个线性矢量图标（res/drawable/ic_ark_*.xml，统一 1.7 描边语言）：undo/image/spark/trash/crop/clapper/folder/hex/transfer/play；徽标与工业背景复用已有 ark_rhodes_logo / ark_industry，未额外联网下载。
- 验证：`assembleDebug` BUILD SUCCESSFUL；装机冷启动 Success；截图确认五段页头、墨黑磁贴、渐变生成条、编号 Dock、页脚色板全部真实渲染。NAI/修图/导演/作品/设置页经共享页头与 Dock 自动获得一致性；NAI 页操作行仍为旧芯片样式，留待下轮统一。

## 参考稿比例二轮校准（2026-09-14）

- 用户反馈「差别很大」「顶部装饰区太大、输入框太大、最好不用翻页」→ 改用像素分带量化对比（参考稿 vs 实机截图，按屏高百分比对齐明度/深色占比），迭代 3 轮（v3→v5）。
- 关键发现：①文字实际渲染高度≈标称 sp 的 1.3–1.6 倍（44sp 标题实测占屏 8.6%），sp 估值不可靠，必须截图量测；②上次会话遗留的 921 字符提示词把 M3 TextField（24sp 行高+16dp 内衬）撑到 6 行，是输入区过大的主因。
- 页头压缩：标题 60→44→34sp；右侧终端块 4 行合并 2 行（TERMINAL // OPERATOR SYS 防折行）；徽标 30→22dp；页签 26→22dp；工业纹 150→100dp 且只占右 62% 宽；各 Spacer/状态条 padding 收紧。页头占屏 44%→31%（参考稿 26%）。
- 输入区重做：M3 TextField → BasicTextField（13sp/18sp 行高/内衬 10dp/钢蓝光标），minLines 3 maxLines 5；磁贴 64→56dp；ArkInkPanel padding 16→10dp。墨黑面板占屏 45%→36%（参考稿 38%）。
- 底部压缩：生成条 58→48dp、Dock 项 64→52dp、激活徽块 46→36dp、页脚 padding 减半。
- 输出设置面板：标题加钢蓝竖条 + OUTPUT SETTINGS 微字；画幅/画质标签补 ASPECT RATIO / QUALITY 英文微字。
- v5 实测（与参考稿同屏高百分比）：墨黑面板 31–67%（ref 26–64）、输出设置 68–80%（ref 64–75）、生成条 82–89%（ref 75–83）、Dock 90–100%（ref 83–100）。**首屏完整容纳 页头+提示词+输出设置+生成条，无需翻页**；唯一残差是页头比参考稿高 5%（真机状态栏+字体渲染膨胀所致，属物理下限）。
- 验证：三轮均 assembleDebug BUILD SUCCESSFUL + pm install Success + 冷启动截图复测；未调用任何真实生图 API。
- 方法论沉淀：视觉验收以「截图分带明度剖面」为准，不以代码 dp 估值为准。

## 页头密排重构 + NAI 页同款化（2026-09-14 第三轮）

- 用户反馈「顶部装饰栏还是太大、精细化不够、NAI 也要弄」。
- 页头从五行堆叠重构为四段密排：①徽标条压成单行（徽标 15dp + RHODES ISLAND 字标 + 细分隔线 + FOR A BETTER TOMORROW 微字 + 右侧 TERMINAL // OPERATOR SYS）；②页签行 22dp；③标题 32sp 斜体黑；④**平台行整行裁撤**，五段进度（14×2dp）并入全宽状态条（SYSTEM ONLINE ｜ 分段 ｜ 页名//0X/05）。工业桁架纹缩到右上 55% 宽 ×72dp、alpha .10，不再参与页头高度。
- v6 实测剖面 vs 参考稿：页头 0–25%（ref 0–26%）、标题带 15–18%（ref 15–18%）、墨黑面板 25–60%（ref 26–64%）、输出设置 62–80%、生成条 82–88%、Dock 90–100%——**分带明度剖面与参考稿基本重合**，比例校准收工。
- NAI 生图 tab 同款化：`Panel`→`ArkInkPanel` 墨黑容器；标题补 PROMPT INPUT 微字；操作行 StudioPromptAction×4+PopIconButton → 五枚 ArkTileButton（撤回 UNDO/翻译 TRANSLATE 文A/图像 IMAGE/润色 ENHANCE 钢蓝激活/清空 CLEAR，56dp）；PopTextField → 暗色 BasicTextField（13sp/18sp/钢蓝光标，minLines4 maxLines8）；文本任务忙碌遮罩改墨黑配色。token 徽章、画师串面板保留。
- 编译期自纠两处：when 块闭合括号被模糊匹配误删、大块替换尾部多出一个 `}`，均已修复；最终 assembleDebug BUILD SUCCESSFUL、pm install Success。
- 真机验证插曲：首次盲点 (350,310) 误触通知弹出 Clash 配置框（未动其内容，BACK 关闭）；改为「am start 确认 topResumedActivity → 像素扫描定位页签(创作激活块 x184-300/NAI x320-424, y≈228) → 定点 tap (372,228) → 前后双查焦点」流程后，创作页与 NAI 页截图均验证通过。
- 最终产物：`/sdcard/Download/imagine-ref-theme6.apk`（同步覆盖 `imagine-ark.apk`）。未调用任何真实生图 API。

## 页头四连收 + 切换模型块缩小（2026-09-14 第四轮）

- 用户反馈「切换模型做小点，上面还能再收收」。
- `ArkBlockAction`（中转 TRANSFER）整体缩一号：内衬 10×7→8×5dp、图标 16→13dp、中文 12→10sp、英文 7→6sp。
- 页头再收：徽标 15→13dp、字标 7→6.5sp、Column 顶部 padding 归零、徽标→页签 5→3dp、页签→标题 3→2dp、页签高 22→20dp、标题 32→28sp、标题→状态条 4→2dp。
- v7 实测：页头 0–24%（ref 0–26%）、标题带 14–16%、墨黑面板 24–58%、输出设置 60–80%、生成条 82–88%、Dock 90–100%；页头裁剪目检无折行无裁切。
- assembleDebug BUILD SUCCESSFUL、pm install Success、冷启动 topResumedActivity 确认；最终产物 `imagine-ref-theme7.apk`（同步覆盖 `imagine-ark.apk`）。未调用真实生图 API。
- 追加（第五轮）：用户反馈切换模型块「还是大了，做成长方形就行了」→ `ArkBlockAction` 由双行堆叠改为**单行横排**（图标 12dp + 中文 10sp + 英文 6sp 同行，内衬 9×4dp，高约 22dp 扁长方形）；v8 装机 OCR 确认单行渲染，剖面页头保持 0–24%。产物 `imagine-ref-theme8.apk`（同步覆盖 `imagine-ark.apk`）。

## 底栏页脚裁撤 + NAI 页签风格化 + 角色启用修复（2026-09-14 第六轮）

- 用户反馈四连：「底部装饰条太占位置，不要」「顶部不要出现罗德岛的英文」「NAI 模式的四个 tag 很丑，没有风格化适配」「角色的启用好像有点问题」。
- **底部装饰条裁撤**：`AppDock` 里的 `ArkFooterStrip()`（RHODES ISLAND 标语 + 四格色板）整条删除，Dock 现在以编号双语页签行收尾；`ArkRefUi.kt` 里的 `ArkFooterStrip` 组件本体同步移除（不再被引用）。
- **顶部品牌英文移除**：页头徽标条删去「RHODES ISLAND」字标、「FOR A BETTER TOMORROW」口号与中间分隔线，只保留左徽标 + 右侧 `TERMINAL // OPERATOR SYS` 终端微字，一行更轻。
- **NAI 四 tag 风格化**：旧 Material `FilterChip` 全部退役，换成新组件 `ArkTabRow`（`ArkRefUi.kt`）——四枚等宽切角块（ArkCutShape 5dp，36dp 高），编号 01–04 + 中文黑体 + 英文微字双层排布；选中为钢蓝底白字、未选中细描边；`NAI_TABS` 数据同步扩为 Triple（中文/英文/键）。
- **角色启用修复**（根因三条）：
  1. 下拉选中/回填标签不一致：空名角色在「选中显示」用「未命名角色」、在「选项列表」用「角色 N」，选完 `indexOfFirst` 匹配不上静默落回第 0 张卡——于是「启用该角色」切的是别人。现改为 `cardLabels` 单一来源（空名「角色 N」、重名追加 `(序号)`），选中/选项/回填三处共用。
  2. 编辑与开关用旧快照：`updateCard`、新增/删除/导入角色此前全部基于组合函数捕获的 `c` 快照，快速连续操作会用旧列表覆盖新列表。现全部改读 `NaiWorkspaceState.config` 实时值。
  3. 启用空提示词角色时请求里毫无痕迹：caption 为空时角色本就不写入请求，但界面没有任何说明。现启用态且提示词为空时，副标题提示「已启用，但提示词为空——补上特征/服装才会写入请求」。
- 验证：`assembleDebug` BUILD SUCCESSFUL（1m 8s，0 warning 级错误）；四个改动文件括号净深度全部为 0；产物 `imagine-ref-theme9.apk`（同步覆盖 `imagine-ark.apk`）。未调用真实生图 API。
- 装机与真机验证（通道恢复后补做）：`pm install -r` Success；冷启动 Success；UI dump 逐项确认——①页头无品牌英文（仅 `TERMINAL // OPERATOR SYS`）；②底栏无页脚（页签行直接收尾）；③NAI 页签为「01 生图 GENERATE / 02 预设 PRESET / 03 角色 CHARACTER / 04 更多 MORE」切角块；④角色页「启用该角色」开关与说明正常渲染，下拉/新增/导入/删除逻辑改读实时配置。
- 追加（第七轮·创作改标准）：用户反馈「创作改为标准」→ 三处落点统一改名——①`ImagineApp.kt` Dock 首项 `BottomItem` 中文「创作」→「**标准**」、英文「CREATE」→「STANDARD」；②`StudioScreen.kt` 页头 `ScreenHeader(title = "创作")` → `"标准"`；③`UiKit.kt` `ArkScreenHeader` 英文页名映射键 `"创作" -> "STUDIO"` → `"标准" -> "STUDIO"`（模式切换器 `ModeSwitch.kt` 左段本就是「标准」，无需改）。验证：assembleDebug SUCCESSFUL（17s）、pm install Success、冷启动 Success、UI dump 确认 `text="标准"` + `text="STANDARD"` 渲染。

## 全局美化猛攻（2026-09-14 第八轮）

- 用户反馈「继续，猛攻美化」+「创作改为标准」。
- **页头底横线入场动效补全**：`UiKit.kt::ArkScreenHeader` 的 `underlineGrow` 动画变量此前定义却未使用（死代码）——现把页头底部 1dp 分隔线改为「占位全宽 + 可视线随 `underlineGrow` 从 0 自左展开」（600ms FastOutSlowInEasing），落实官网 .6s 底横线自左生长语言。
- **Dock 激活/未激活同构化**：`ImagineApp.kt::AppDock` 此前激活项为横排（36dp 徽块 + 编号列）、未激活项为竖排居中，切页时整块布局跳变。现统一为竖排居中结构——激活项仅把 20dp 线性图标换成 24dp 斜切渐变徽块，并补 2dp 钢蓝顶标 + 钢蓝淡底（alpha .10）标识当前分区，切页不再跳动。
- **作品库每行数量芯片切角化**：`HistoryScreen.kt` 「每行数量」弹窗的 Material3 默认 `FilterChip`（圆角胶囊）退役，换成 40×32dp 主题切角 `Surface` 芯片（选中钢蓝底白字 / 未选中细描边，`themedShape(PopRadius.chip)`），消除与全局切角语言的风格断裂；同步删除无用 `FilterChip` 导入。
- 验证：assembleDebug BUILD SUCCESSFUL（8s）、testDebugUnitTest SUCCESSFUL、pm install Success（v12）；产物 `/sdcard/Download/imagine-ref-theme12.apk`（历次 theme9/10/11 留存）。未调用真实生图 API。

## 科技感动效加码（2026-09-14 第九轮）

- 用户反馈「感觉不够科技感，也没有粒子和光效还有动画」。
- **背景上升粒子流**：`UiKit.kt::popBackdrop` 新增第 7 层——8 颗伪随机横向散布的粒子，相位错开形成连续上升流；每颗带渐变尾迹（`Brush.verticalGradient`，尾部消隐）+ 顶点圆点，横向带正弦漂移，alpha 两端渐隐；5200ms 周期，暗版标志青 #18D1FF / 浅版深青 #00699C。
- **生成按钮流光**：`ArkRefUi.kt::ArkGenerateBar` 叠加 `drawBehind` 流光层——白色窄光束（22% 宽度，四段渐变透明→18%→32%→18%→透明）从左到右 2800ms 周期扫过按钮表面，仅启用态显示，能量充能语言。
- **面板角标呼吸**：`UiKit.kt::ArkPanel` 四角角标线 alpha 由固定 .82 改为 `cornerPulse`（.68→.92 往返，2400ms FastOutSlowInEasing），暗示模块处于待命状态。
- **结果卡扫描线**：`UiKit.kt::ImageResultCard` 新增 `drawBehind` 扫描层——1.5dp 钢蓝横线沿卡片高度 3600ms 周期缓慢下移（`scanY * size.height`），横向渐变消隐，数据写入语言。
- 验证：assembleDebug BUILD SUCCESSFUL（11s）、testDebugUnitTest SUCCESSFUL、pm install Success（v13）；连续 3 张截图 SHA-256 全不同，确认动态绘制真实生效；产物 `/sdcard/Download/imagine-ref-theme13.apk`。未调用真实生图 API。

## 生成按钮结构重做（2026-09-14 第十轮）

- 用户反馈「下面的主按钮有点丑」。
- **结构分层**：`ArkRefUi.kt::ArkGenerateBar` 由「居中堆叠（图标+文字挤在一起）」改为**左对齐分层**——左侧 32dp 钢蓝圆底播放图标（`ArkCutShape(6.dp)` 托底，不再是裸图标），右侧双语文字块，横向 16dp 内衬拉开呼吸空间。
- **底部能量指示线**：新增 2dp 钢蓝横线贴按钮底边，横向渐变消隐，alpha 随 `energyPulse`（.5→1.0 往返，1400ms）呼吸，系统就绪状态语言。
- 验证：assembleDebug BUILD SUCCESSFUL（9s）、testDebugUnitTest SUCCESSFUL、pm install Success（v14）；放大截图确认左图标圆底 + 右文字 + 底部能量线三层结构渲染正常；产物 `/sdcard/Download/imagine-ref-theme14.apk`。未调用真实生图 API。

## 修图/导演页风格统一（2026-09-14 第十一轮）

- 用户反馈「修图导演里面的风格一致性没做好」——两页残留 Material3 默认视觉（圆角 Button、CircleShape、圆角 RoundedCornerShape），与全站「罗德岛终端」切角语言断裂。
- **修图页 `EditScreen.kt`**（3 处）：
  1. 底部主按钮整块由 Material3 `Button`（圆角+主题色+`popCtaBorder`，高 58dp）替换为 `ArkGenerateBar`（切角渐变+流光+能量线，高 48dp，与创作页同款）——label「生成 N 张修改」/加载态「Ns · 结果数/总数 · 点此取消」，sub `WAITING|TAP TO CANCEL` / `EDIT GENERATE`，onClick 区分取消与 `runEdit()`；
  2. 结果卡「继续编辑」`PopIconButton` shape `CircleShape` → `themedShape(PopRadius.chip)`；
  3. 补 `import com.lo.imagine.ui.ArkGenerateBar`。
- **导演页 `DirectorScreen.kt`**（6 处）：访谈进度圆点 `stepCurrent/stepDone/未走` 的 `.clip(CircleShape)` 与 `.border(..., CircleShape)` → `themedShape(PopRadius.chip)`；AI ✦ 头像、「我」头像、思考态 ✦ 头像（各 2 处 clip/border）→ 切角；复制脚本按钮 `RoundedCornerShape(8.dp)` → `themedShape(PopRadius.chip)`。
- **约束保持**：访谈推进逻辑（`directorChatTurn`）、`ChatBubble` Gson 持久化、`finishInterview` 收口、素材开关三形态匹配均未触碰，纯视觉替换。
- 验证：assembleDebug BUILD SUCCESSFUL（14s，仅 1 条无害 Elvis warning）、testDebugUnitTest SUCCESSFUL、pm install Success（v15）；真机截图复验——修图页底部已渲染「生成1张修改 / EDIT GENERATE」生成条（与创作页同款），导演页步骤圆点 1-5 与聊天气泡切角头像渲染正常；产物 `/sdcard/Download/imagine-ref-theme15.apk`（历次 theme9~14 留存）。未调用真实生图 API。

## 页头二次收紧（2026-09-14 第十二轮）

- 用户反馈「顶部还可以再收」——针对 `UiKit.kt::ArkScreenHeader`（罗德岛专属页头，全局组件）做垂直密度再压缩，全站五页统一生效。
- **大标题**：英文页名（STUDIO/RETOUCH/DIRECTOR…）28sp → **22sp**（lineHeight 同步），入场斜体层级保留；
- **徽标条**：罗德岛徽标 13dp → **11dp**，与标题行间距 3dp → **2dp**；
- **页签行**（titleOverride，标准页「标准 | NAI」）：与标题行间距 2dp → **1dp**；
- **状态行**：SYSTEM ONLINE 分段进度条 vertical padding 3dp → **2dp**。
- 验证：assembleDebug BUILD SUCCESSFUL（12s）、testDebugUnitTest SUCCESSFUL、pm install Success（v16）；真机裁切复验修图页与标准页页头——标题缩小、徽标条/状态条紧凑，标准页页签行无挤压；产物 `/sdcard/Download/imagine-ref-theme16.apk`（历次 theme9~15 留存）。未调用真实生图 API。

## 页头结构重排（2026-09-14 第十三轮）

- 用户反馈「最顶上有个罗德岛的图标，把他放模式切换的左边，右上角的装饰下移，就可以往上移动空间了」。
- **徽标条整行并入主行**（`UiKit.kt::ArkScreenHeader`）：
  - 有模式切换器的页（标准/NAI，`titleOverride`）：第一行 = 罗德岛徽标(11dp) + 切换器 + 右贴 `TERMINAL // OPERATOR SYS` 微字；大标题行独立，action 只在大标题行出现一次；
  - 无切换器的页（修图/导演/作品/设置）：单行 = 徽标 + 大标题(22sp) + 右贴终端微字 + action——**整页页头从三行减为两行（状态行+单行），省一整行高度**；
  - 原「徽标条独立行」删除，action 不会在两行重复渲染。
- **右上角工业桁架纹理**：`align(TopEnd)` → **`align(BottomEnd)`**，顶部状态栏区域让空，视觉重心随内容下移。
- 验证：assembleDebug BUILD SUCCESSFUL（10s）、testDebugUnitTest SUCCESSFUL、pm install Success（v17）；真机裁切复验——标准页「logo+标准|NAI」与「STUDIO」两行正常，修图页「logo+RETOUCH+终端微字」单行+状态行正常；产物 `/sdcard/Download/imagine-ref-theme17.apk`（历次 theme9~16 留存）。未调用真实生图 API。

## 主行/切换器换位（2026-09-14 第十四轮）

- 用户反馈「把模式切换和下面的装饰文字换位置」——大页名（STUDIO/RETOUCH…）与模式切换器（标准|NAI）行序互换。
- **主行（全站）**：罗德岛徽标 + **大页名**（22sp weight 顶满）+ action（与页名同行）；
- **次行（仅标准/NAI 页）**：模式切换器 + 右贴 `TERMINAL // OPERATOR SYS` 微字；
- 无切换器页（修图/导演/作品/设置）结构不变：徽标+页名+微字+action 单行；切换器不再压在大标题上方，页头视觉层级「标题→模式→状态」。
- 验证：assembleDebug BUILD SUCCESSFUL（12s）、testDebugUnitTest SUCCESSFUL、pm install Success（v18）；真机裁切复验标准页——主行 logo+STUDIO+agnes TRANSFER、次行标准|NAI+终端微字；产物 `/sdcard/Download/imagine-ref-theme18.apk`（历次 theme9~17 留存）。未调用真实生图 API。

## NAI 作品提示词补全角色段（2026-09-14 第十五轮）

- 用户反馈「图片生成出来能看到角色起作用了，但打开图片看不到该角色的提示词，说明提示词没有完全展示」——根因：**角色卡走 NAI 原生独立字段**（`v4_prompt.char_captions` / `characterPrompts`），不并入 `input`；而预览/存档只取 `payload["input"]`，所以作品库里看不到角色段。
- **修复**（`NaiNativeClient.kt`）：新增 `naiDisplayPrompt(c, input)`——主体 + 启用角色 caption 按 NAI 多角色 `|` 语法拼接（`input + " | " + caption`），未启用/空 caption 角色不出现；注释说明与真实请求内容对齐。
- **接入**（`NaiWorkspaceScreen.kt`）：生成成功后 `resultPrompt` 由 `validation["input"]` 改为 `naiDisplayPrompt(c, ...)`——预览页 PromptDrawer、NaiShot、`ImageUtils.archiveResult` 落盘全部自动带上角色段（同一变量，无第二处改动）。
- **测试**（`NaiNativeTest.kt`）：新增 `displayPromptIncludesEnabledCharacters`——含角色时 display 含 caption 且以 input 开头；未启用/空 caption 时与 input 全等。
- 验证：assembleDebug BUILD SUCCESSFUL（16s，与单测同跑）、testDebugUnitTest SUCCESSFUL、pm install Success（v21）；产物 `/sdcard/Download/imagine-ref-theme21.apk`（历次 theme9~20 留存）。未调用真实生图 API（付费服务）。**注意：已生成的旧作品存档里没有角色段，修复只对之后新生成的图生效。**

## st-chatu8 加密导出解密对齐（2026-09-15 第十六轮）

- 用户下载 st-chatu8 插件源码（`refs/st-chatu8/index.js`，4.6MB 构建产物）研究后选择方案 4：**导入器解密按插件加密清单精确执行**。
- **根因**（对照插件 `encryptCharacterPreset`/`decryptCharacterPreset` 原文）：插件加密导出只对 **12 个固定字段**做 Base64 混淆——`nameCN, nameEN, facialFeatures, facialFeaturesBack, upperBodySFW, upperBodySFWBack, fullBodySFW, fullBodySFWBack, upperBodyNSFW, upperBodyNSFWBack, fullBodyNSFW, fullBodyNSFWBack`；`characterTraits / prompt / negative / outfit 描述`等**保持明文**。而本应用导入器 `decryptFields` 对所有字符串字段盲解——**形似 Base64 的明文（如 `solo`、`garden`）会被误伤解成乱码**，角色特征直接损坏。
- **修复**（`Chatu8CharacterImporter.kt`）：
  1. 新增 `encryptedCharacterFields` 清单（与插件 12 字段完全一致），`decryptFields` 仅解密清单内字段，其余原样保留；
  2. `decodeBase64Safe` 重写为纯 Kotlin 实现（`decodeBase64OrNull`，容忍缺省 padding/空白，UTF-8 还原失败返回 null 保留原文）——不依赖 `android.util.Base64`，单元测试可直接覆盖加密导入路径。
- **测试**（`NaiNativeTest.kt`）：新增 `encryptedExportDecryptsOnlyListedFields`——清单内字段（nameCN/facialFeatures/upperBodySFW）正确还原；清单外明文 `characterTraits="solo"`（合法 Base64 串，旧实现解成乱码）必须原样保留；加密 outfits 元素正常解码。
- 验证：assembleDebug + testDebugUnitTest BUILD SUCCESSFUL（13s）、NaiNativeTest 31 tests / 0 skipped / 0 failures（新测试在列）、pm install Success（v22）；产物 `/sdcard/Download/imagine-ref-theme22.apk`（历次 theme9~21 留存）。未调用真实生图 API。
- **未处理**（用户仅选了方案 4，以下为下一轮候选）：①`1girl→girl` 多角色字段转换；②`_nameMap` 服装匿名 ID 还原 + 顶层 outfits 对象解析；③坐标默认 0.5/0.5 永远在传的问题。

## 角色不像三根因全修（2026-09-15 第十七轮）

- 用户「继续」——上一轮挂起的三个候选全部落地（均对照 st-chatu8 生产逻辑）：
- **①人数词转换**（`NaiNativeClient.kt`）：新增 `naiCharacterFieldCaption()`——角色 caption 里 `1girl→girl`、`1boy→boy`（ignoreCase）。NAI 要求人数词只属于 base_caption（全局），char_caption / characterPrompts.prompt 里残留会与全局人数冲突、稀释角色特征。三处接入：v4_prompt char_captions、v4_negative_prompt 校验、characterPrompts.prompt；`naiDisplayPrompt` 同步使用（展示=请求，同一份转换）。
- **②加密服装还原**（`Chatu8CharacterImporter.kt`）：新增 `parseNameMap()`（解码 `_nameMap` Base64 → 匿名 ID→原名，覆盖 characters/outfits 两段）+ `parseOutfitDescriptions()`（顶层 outfits 字典 → 服装名/ID → upperBody+fullBody 描述串）+ `encryptedOutfitFields`（6 字段清单，与插件 encryptOutfitPreset 一致）。角色 outfits 解析链：匿名 ID → 描述字典拿真实标签 → 查不到退原名 → 都不行丢弃（绝不让 `OUTFIT_001` 垃圾词进 caption）。`parseJson` 四处调用点全部接上新参数。
- **③坐标清理**（`NaiNativeClient.kt`）：`use_coords=false`（默认）时 `centers` 传 `[{}]`、`characterPrompts.center` 传 `{}`（与 st-chatu8 对齐）——此前固定 0.5/0.5 会把所有角色钉在画面中心互相污染。
- **测试**（`NaiNativeTest.kt`）：新增 3 个——`characterFieldCaptionDropsCountWords`（角色字段无 1girl、base 保留）、`coordinatesEmptyWhenUseCoordsOff`（关闭时空、开启时 0.5/0.5）、`encryptedExportResolvesNameMapAndOutfits`（匿名 ID→描述字典→真实标签，ID 不残留）；修正 `displayPromptIncludesEnabledCharacters`（适配人数词转换新契约，只查角色段）。
- 验证：assembleDebug + testDebugUnitTest BUILD SUCCESSFUL、NaiNativeTest **34 tests / 0 skipped / 0 failures**、pm install Success（v23）；产物 `/sdcard/Download/imagine-ref-theme23.apk`（历次 theme9~22 留存）。未调用真实生图 API。

## LLM 角色场景融合（2026-09-15 第十八轮）

- 用户关键洞察：「角色卡参数是给语言模型看的，应该由 LLM 根据场景推理提示词，而不是直接用户提示词 + 角色详情强行注入」——此前机械拼接（caption 全量塞进 char_captions）导致场景不匹配、标签堆叠，画面糟糕。补上 st-chatu8 链路缺失的中间层：**角色数据 → LLM 上下文 → 按场景取景推理 → 场景适配提示词**。
- **提示词工程**（`NaiPolish.kt`）：`NaiTask.FUSE` + `naiFuseInstructions()`——取景判断（特写不写腿/上半身不写脚/背面只用背面资料/侧面只写可见部分）、状态判断（动作情绪选状态组、多套服装选一套）、标签化输出（保留权重写法）、人数词禁止出现（归全局管理）、不脑补不写质量词。
- **LLM 调用**（`NaiNativeClient.kt`）：`fuseCharacters(c, s, scene)`——角色完整矩阵（正/背面、上下身 SFW/NSFW、服装、补充）组织成「给 LLM 读的资料」+ 画面场景 → LLM 推理 → 输出按 `|` 分隔的角色段（`cleanNaiOutput` 清洗）。
- **数据层**：`NaiCharacterPrompt.fusedCaption` 新字段（默认空）；`naiCharacterFieldCaption` 优先使用融合版（非空时），请求组装与展示同步生效——「作品里看到的」=「实际发出的」。
- **UI**（`NaiWorkspaceScreen.kt`）：角色面板加「✦ 融合到场景」/「清除融合」按钮；角色卡显示融合版（含「✦ 已融合场景（生成时优先使用）」标记）；「融合中」纳入文本任务遮罩（就地盖在提示词框）；按 `|` 切段回填各启用角色。
- **测试**（`NaiNativeTest.kt`）：新增 `fusedCaptionOverridesMechanicalCaption`（融合版优先、请求组装生效）、`fuseInstructionsCarrySceneReasoningRules`（提示词工程含取景规则）。
- 验证：assembleDebug + testDebugUnitTest BUILD SUCCESSFUL、NaiNativeTest **36 tests / 0 skipped / 0 failures**、pm install Success（v24）；产物 `/sdcard/Download/imagine-ref-theme24.apk`（历次 theme9~23 留存）。未调用真实生图 API（LLM 融合也需用户配置的润色 LLM 才会实际调用）。

## 预设「新建」改为创建空白预设（2026-09-15 第十九轮）

- 用户反馈：「绘图预设」和「LLM 连接档案」点击「新建预设」后不会开一个空白预设，导致老预设总被覆盖。
- 根因（两层）：
  1. `PresetDropdown` 的「＋新建…」走的是**另存为**语义——`onSaveCurrent` 把**当前连接字段**（地址/Key/模型）原样复制成一条新预设，落盘用 `filterNot { it.name == name }.plus(p)`：输入名一旦与已有预设同名，就把老预设整条替换掉；且不产生空白槽位，用户想试新端点只能就地改当前表单、再点「保存修改到「老预设」」，老预设因此被覆盖。
  2. 改为「创建空白」后仍失败：新建要经过**第二步行内命名 + 点确认**，实测这一步没生效（键盘遮挡/焦点/误触），列表里什么都不会多。
- **最终设计（v26）**：新建 = **点一下直接创建**，砍掉命名确认步骤。
  - **组件**（`SettingsPresetSection.kt`）：`onCreateNew: (String) -> Boolean` → `onCreateBlank: () -> Unit`；`saveLabel` → `newLabel`。选中「＋新建…」时直接调 `onCreateBlank()`，不再展开命名行。命名行只保留给**重命名**；`resetInline()` 拆成对 `renaming` / `confirmDelete` 的直接赋值。
  - **调用点**（`SettingsScreen.kt` 两处：连接预设 / 连接档案）：`onCreateBlank` 内生成唯一名（新增 `nextBlankPresetName()`：基础名被占用就自动加序号）→ 建 `baseUrl/apiKey/model` 全空条目 → 置为当前 → **清空表单**（后续填写只落在新预设上）。`newLabel` =「＋新建空白预设…」/「＋新建空白档案…」。
- **实机验证（关键）**：v26 装机后走「设置 → 绘图 API → 下拉 → ＋新建空白预设…」，DataStore `custom_presets_json` 新增 `{"apiKey":"","baseUrl":"","editMode":"edits_multipart","fav":false,"model":"","name":"新预设"}`，`active_preset_name` 变为 `新预设`；原 6 条预设（agnes/apizzz/nai/打野2/摸鱼/鲨鱼）全部完好、零改动。LLM 侧共用同一组件与同一调用链。
- 验证：assembleDebug + testDebugUnitTest BUILD SUCCESSFUL（58 tests / 0 failures，含 NaiNativeTest 36 / NaiPromptTest 14 / NaiWorkspaceStateTest 4 / ImageUtilsMaskTest 4）、pm install Success（v25 → v26）；产物 `/sdcard/Download/imagine-ref-theme26.apk`（历次 theme9~25 留存）。未调用真实生图 API。



