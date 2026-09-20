# 罗德岛主题重构方案

## 一、目标
**只留罗德岛、拆成明暗双版、深度风格化、嵌入明日方舟官方图标**

## 二、删除的主题（5 个）
- `PAPER`（暖纸画布）
- `MIDNIGHT`（深夜沉浸）
- `PIXEL`（像素街机）
- `BAUHAUS`（包豪斯）
- `ANIME`（动漫晴空）

## 三、保留并拆分：罗德岛 → 明暗双版

### 3.1 ARKNIGHTS_LIGHT（浅色 HUD）
**设计定位**：罗德岛舰桥日间作战态——冷白舱室 + 深灰墨线 + 标志青主行动。

**色彩系统**（对齐官网实测值）：
```kotlin
// 主题色：标志青（#00A8CC）→ 加深为 #007A99 保证浅底对比度
primary = Color(0xFF007A99)
onPrimary = Color.White
primaryContainer = Color(0xFFB3E5F0)

// 次要色：荧光黄绿（#B8D400）→ 加深为 #8FA800
secondary = Color(0xFF8FA800)
onSecondary = Color(0xFF1A2400)

// 三级色：电光蓝
tertiary = Color(0xFF3387FB)

// 背景：冷白舱室（#F2F4F5 / #E8EAEC / #DDE0E3 三级灰）
background = Color(0xFFF2F4F5)
surface = Color(0xFFE8EAEC)
surfaceVariant = Color(0xFFDDE0E3)

// 文字：深灰近黑（#1A1D1F）
onBackground = Color(0xFF1A1D1F)
onSurface = Color(0xFF1A1D1F)

// 描边：官网发丝线（#585858 实测）
outline = Color(0xFF585858)
```

**造型**：
- 45° 切角（左上+右下对角，`ArkCutShape(8.dp)`）
- 1px 极细描边（`panelBorder = 1.dp`）
- 零阴影（`panelElevation = 0.dp`）
- 底边细线分隔（`headerUnderline = true`）

**图标嵌入点**：
- ScreenHeader 左侧：阿米娅头像（24dp，灰度滤镜）
- AppDock 导航栏：博士/凯尔希/德克萨斯等图标
- 空态插画：阿米娅立绘
- 加载动画：罗德岛标志旋转

---

### 3.2 ARKNIGHTS_DARK（深色 HUD）
**设计定位**：罗德岛舰桥夜间战备态——纯黑底 + 标志青高光 + 减少全局亮度。

**色彩系统**：
```kotlin
// 主题色：标志青提亮（#18D1FF）在黑底上发光
primary = Color(0xFF18D1FF)
onPrimary = Color(0xFF003544)
primaryContainer = Color(0xFF004D5E)

// 次要色：荧光黄绿保持（#B8D400）
secondary = Color(0xFFB8D400)
onSecondary = Color(0xFF1A2400)

// 三级色：电光蓝提亮
tertiary = Color(0xFF61B6FB)

// 背景：纯黑舱室（#000000 / #0D1012 / #1A1D1F 三级深）
background = Color(0xFF000000)
surface = Color(0xFF0D1012)
surfaceVariant = Color(0xFF1A1D1F)

// 文字：冷白（#E8EAEC）
onBackground = Color(0xFFE8EAEC)
onSurface = Color(0xFFE8EAEC)

// 描边：深灰（#2A2D2F）
outline = Color(0xFF2A2D2F)
```

**造型**（与浅色版一致）：
- 45° 切角
- 1px 细描边（描边色改深灰）
- 零阴影
- 底边细线分隔

**图标嵌入点**（与浅色版一致，滤镜改为高光）：
- 同浅色版，但图标滤镜改为「标志青着色 + 60% 透明度」

---

## 四、实施步骤

### 4.1 数据层
**ThemeMode.kt**：
```kotlin
enum class ThemeMode(val id: String, val label: String, val description: String) {
    ARKNIGHTS_LIGHT("ark_light", "罗德岛 · 日间", "冷白舱室 HUD，标志青主行动"),
    ARKNIGHTS_DARK("ark_dark", "罗德岛 · 战备", "纯黑底高光 HUD，夜间作战态");
    
    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: ARKNIGHTS_LIGHT
    }
}
```

### 4.2 Theme.kt
- **删除** `PaperColors / MidnightColors / PixelColors / BauhausColors / AnimeColors`
- **保留** `ArknightColors`（重命名 `ArknightLightColors`）
- **新增** `ArknightDarkColors`
- **删除** `PaperFlavor / MidnightFlavor / PixelFlavor / BauhausFlavor / AnimeFlavor`
- **保留** `ArknightFlavor`（不分明暗，造型规格相同）
- **删除** `BackdropKind` 中除 `ARK_TERMINAL` 外的所有值
- **删除** `PopAccentsPaper / PopAccentsMidnight / PopAccentsPixel / PopAccentsBauhaus / PopAccentsAnime`
- **保留** `PopAccentsArk`（双版共用）

### 4.3 Type.kt
- **删除** `PixelTypography / BauhausTypography`
- **保留** `ArknightTypography`（双版共用）

### 4.4 UiKit.kt
- **删除** `SkyPanel / SkyScreenHeader / SkyBusyIcon / SkyRenderLoader / SkyGenerationStatus`（动漫）
- **删除** `popBackdrop` 里除 `ARK_TERMINAL` 外的所有分支
- **保留** `ArkPanel / ArkScreenHeader / ArkBusyIcon / ArkRenderLoader`（双版共用，根据 isDark 自动切色）
- **新增** 图标嵌入函数：`ArkOperatorIcon(resId: Int, size: Dp, tint: Color)`

### 4.5 PanelMaterial.kt
- **删除** 除 `ArkTerminalMaterial` 外的所有材质绘制函数
- **保留** `ArkTerminalMaterial`（双版共用）

### 4.6 ThemeChooser.kt
- **删除** `PixelPreview / BauhausPreview / AnimePreview / NightPreview / DefaultPreview`
- **保留** `ArkPreview`（新增明暗两卡）
- 卡片从 6 张缩至 2 张（日间 / 战备）

### 4.7 图标资源
已下载到 `app/src/main/res/drawable`：
- `ark_amiya.png`（阿米娅，30K）
- `ark_kaltsit.png`（凯尔希，38K）
- `ark_doctor.png`（博士，17K）
- `ark_texas.png`（德克萨斯，24K）

### 4.8 迁移策略
**旧存档处理**：
```kotlin
fun fromId(id: String): ThemeMode = when(id) {
    "ark_light" -> ARKNIGHTS_LIGHT
    "ark_dark" -> ARKNIGHTS_DARK
    "arknights" -> ARKNIGHTS_LIGHT  // 旧罗德岛 → 日间版
    else -> ARKNIGHTS_LIGHT  // 其他五个主题 → 日间版（默认）
}
```

---

## 五、UI 嵌入点（官方图标）

### 5.1 ScreenHeader（页头）
```kotlin
// 左侧：阿米娅圆形头像（24dp）
Row {
    Image(
        painter = painterResource(R.drawable.ark_amiya),
        contentDescription = null,
        modifier = Modifier.size(24.dp).clip(CircleShape),
        colorFilter = if (isDark) 
            ColorFilter.tint(Color(0xFF18D1FF).copy(alpha=0.6f)) 
            else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    )
    Spacer(4.dp)
    Text("RHODES ISLAND", style = MaterialTheme.typography.labelSmall)
}
```

### 5.2 AppDock（底栏导航）
```kotlin
// 三个导航项各用一个干员图标：
// 创作 → 博士（Doctor）
// NAI → 凯尔希（Kal'tsit）
// 设置 → 德克萨斯（Texas）
```

### 5.3 空态插画
```kotlin
// 作品库为空时：阿米娅立绘 + "暂无作品" 提示
```

### 5.4 加载动画
```kotlin
// ArkBusyIcon：罗德岛标志（六边形）旋转 + 标志青描边
```

---

## 六、验证清单
- [ ] compileDebugKotlin 0 error
- [ ] 47 项单元测试通过
- [ ] 旧存档（paper/midnight/pixel/bauhaus/anime/arknights）自动迁移到 `ark_light`
- [ ] 浅色版：冷白舱室、深灰墨线、标志青主色、45° 切角、1px 细线
- [ ] 深色版：纯黑底、标志青高光、深灰细线、切角与浅色版一致
- [ ] 图标正确显示：阿米娅/凯尔希/博士/德克萨斯
- [ ] 主题切换器只显示两张卡片（日间 / 战备）
- [ ] assembleDebug + 装机 Success
