package com.lo.imagine.data

import org.junit.Assert.*
import org.junit.Test

class NaiPromptTest {
    private val plain = StylePreset("none", "", "", "")
    /** 测试默认开启 NAI 模式；关闭路径单独用 naiModeGatesProfiles 覆盖 */
    private val on = NaiOptions(modeEnabled = true)
    @Test fun profiles() {
        assertEquals(NaiProfile.V45_FULL, resolveNaiProfile("nai-diffusion-4-5-full", on))
        assertEquals(NaiProfile.V45_CURATED, resolveNaiProfile("novelai-v4.5-curated", on))
        assertEquals(NaiProfile.V5_FULL, resolveNaiProfile("nai-diffusion-5-full", on))
        assertEquals(NaiProfile.V5_CURATED, resolveNaiProfile("novelai-v5-curated", on))
        assertNull(resolveNaiProfile("nai-diffusion-3", on))
        assertNull(resolveNaiProfile("gpt-image-5", on))
        assertNull(resolveNaiProfile("nai-diffusion-5-full", on.copy(profileId = "off")))
    }
    @Test fun naiModeGatesProfiles() {
        // 模式未开启时，即使模型是 NAI 且 profileId=auto 也必须走通用路径
        assertNull(resolveNaiProfile("nai-diffusion-5-full", NaiOptions(modeEnabled = false)))
        assertEquals(NaiProfile.V45_FULL, resolveNaiProfile("nai-diffusion-4-5-full", NaiOptions(modeEnabled = true)))
    }
    @Test fun qualityIsVersioned() {
        assertEquals("location, very aesthetic, masterpiece, no text", naiQualityTags(NaiProfile.V45_FULL, "standard"))
        assertTrue(naiQualityTags(NaiProfile.V45_CURATED, "standard").contains("-0.8::feet::"))
        assertEquals("very aesthetic, amazing quality, no text", naiQualityTags(NaiProfile.V5_FULL, "light"))
        assertEquals("", naiQualityTags(NaiProfile.V5_FULL, "off"))
        assertTrue(naiNegativeTags(NaiProfile.V5_FULL, "light").contains("0::ai-generated::"))
    }
    @Test fun weightedGroupsStayWhole() {
        assertEquals(listOf("1.2::artist:a, artist:b::", "artist:c (alias)"), naiSegments("1.2::artist:a, artist:b::, artist:c (alias)"))
        assertEquals(listOf("{{a, b}}", "[c]"), naiSegments("{{a, b}}, [c]"))
    }
    @Test fun syntaxValidation() {
        assertTrue(naiSyntaxErrors("1.2::artist:a::, -1::hat::, {{rain::").isEmpty())
        assertTrue(naiSyntaxErrors("1.2::artist:a").isNotEmpty())
        assertTrue(naiSyntaxErrors("(artist:a:1.2)").isNotEmpty())
        assertTrue(naiSyntaxErrors("a | b", true).isNotEmpty())
        assertTrue(naiSyntaxErrors("{a]").isNotEmpty())
    }
    @Test fun cleanPreservesNumericWeights() {
        assertEquals("1.2::artist:a::\n-1::hat::\n_name_", cleanNaiOutput("<正文>\n1.2::artist:a::\n-1::hat::\n_name_\n</正文>"))
    }
    @Test fun datasetAndCharactersStayInScope() {
        val result = assembleNaiPrompt("fur dataset, 2girls, garden | girl, red hair | girl, blue hair", "1.1::artist:a::", "", plain, NaiProfile.V5_FULL, on.copy(quality = "standard"))
        assertTrue(result.positive.startsWith("fur dataset, 1.1::artist:a::, 2girls"))
        assertEquals(1, Regex("artist:a").findAll(result.positive).count())
        assertTrue(result.positive.contains("masterpiece, no text |"))
        assertTrue(result.errors.isEmpty())
    }
    @Test fun artistStaysAheadOfIdentityAndBody() {
        val r = assembleNaiPrompt("blue sky, 1girl, solo, red flower", "artist:test", "", plain, NaiProfile.V5_FULL, on.copy(quality = "off"))
        val artist = r.positive.indexOf("artist:test")
        val identity = r.positive.indexOf("1girl")
        val body = r.positive.indexOf("red flower")
        assertTrue("画师串必须排在身份词之前", artist in 0 until identity)
        assertTrue("画师串必须排在主体提示词之前", artist < body)
        assertTrue("身份词仍应排在主体词之前", identity < body)
    }
    @Test fun noDuplicateInjectionAndOffMeansOff() {
        val result = assembleNaiPrompt("1girl, artist:a, masterpiece", "artist:a", "", plain, NaiProfile.V5_FULL, on.copy(quality = "standard"))
        assertEquals(1, Regex("artist:a").findAll(result.positive).count())
        assertEquals(1, Regex("masterpiece").findAll(result.positive).count())
        val off = assembleNaiPrompt("1girl", "artist:a", "", plain, NaiProfile.V5_FULL, on.copy(artistsEnabled = false))
        assertEquals("1girl", off.positive)
        assertNull(off.negative)
    }
    @Test fun conflictsRemainVisibleNotSilentlyRemoved() {
        val r = assembleNaiPrompt("1girl, film grain", "", "film grain", plain, NaiProfile.V5_FULL, on)
        assertTrue(r.warnings.any { it.contains("正负向重复") })
        assertEquals("film grain", r.negative)
    }
    @Test fun disabledArtistsAreNotInjected() {
        val options = on.copy(disabledArtists = listOf("1.1::artist:a::"))
        assertEquals("artist:b", activeNaiArtists("1.1::artist:a::, artist:b", options))
        val restored = com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(StudioPersist(naiOptions = options)), StudioPersist::class.java)
        assertEquals(options, restored.naiOptions)
        assertNull(com.google.gson.Gson().fromJson("{}", StudioPersist::class.java).naiOptions)
    }
    @Test fun instructionsDoNotDemandTagCount() {
        val s = naiPolishInstructions(NaiProfile.V5_FULL, PolishDepth.DEEP, "artist:a", "watercolor")
        assertTrue(s.contains("不要强制标签数量"))
        assertTrue(s.contains("artist:a"))
        assertTrue(s.contains("V5"))
    }
    @Test fun taskEngineeringIsTaskSpecific() {
        val caption = naiTaskInstructions(NaiTask.CAPTION, NaiProfile.V5_FULL)
        assertTrue(caption.contains("只看图"))
        assertTrue(caption.contains("不写质量词"))
        assertTrue(caption.contains("不输出任何负面词"))
        val translate = naiTaskInstructions(NaiTask.TRANSLATE, NaiProfile.V45_FULL, direction = "译成英文")
        assertTrue(translate.contains("译成英文"))
        assertTrue(translate.contains("1.2::artist:name::"))
        val polish = naiTaskInstructions(NaiTask.POLISH, NaiProfile.V5_FULL, PolishDepth.MEDIUM, "artist:a", "")
        assertTrue(polish.contains("artist:a"))
        assertTrue(polish != caption && polish != translate)
    }
    @Test fun promptTemplatesOverrideAndDefaults() {
        // 留空 = 用内置默认；填了 = 整段覆盖
        val blank = AppSettings()
        assertEquals(PromptTemplates.defaultReversePromptText("nai-diffusion-4-5-full"), PromptTemplates.reverseSystem(blank, "nai-diffusion-4-5-full"))
        assertTrue(PromptTemplates.reverseSystem(blank, "nai-diffusion-4-5-full").contains("只看图"))
        assertEquals(PromptTemplates.GENERIC_REVERSE, PromptTemplates.reverseSystem(blank, "gpt-image-2"))
        assertEquals(PromptTemplates.GENERIC_POLISH, PromptTemplates.polishSystem(blank, "gpt-image-2"))
        val custom = blank.copy(reversePromptTemplate = "我的反推规则", polishPromptTemplate = "我的润色规则")
        assertEquals("我的反推规则", PromptTemplates.reverseSystem(custom, "nai-diffusion-4-5-full"))
        assertEquals("我的润色规则", PromptTemplates.polishSystem(custom, "gpt-image-2"))
        // 只有空白字符也算没填
        val spaces = blank.copy(reversePromptTemplate = "   ")
        assertEquals(PromptTemplates.defaultReversePromptText("gpt-image-2"), PromptTemplates.reverseSystem(spaces, "gpt-image-2"))
    }

    @Test fun standardReverseTemplateCarriesTheFourRequiredModules() {
        val t = PromptTemplates.GENERIC_REVERSE
        // 四个核心模块与实例段落名必须在模板里，否则模型不知道要输出什么结构
        listOf("画面风格", "核心元素", "具体内容", "构图方式").forEach { assertTrue("缺模块 $it", t.contains(it)) }
        listOf("最高指令", "核心规则", "约束条件", "提示词生成", "核心要素", "细节特征", "格式校验", "输出规范", "实例").forEach {
            assertTrue("缺章节 $it", t.contains(it))
        }
        // 关键约束未丢：正向表达、禁微观粒子、1000 字上限、无 Markdown
        assertTrue(t.contains("禁用负面提示词"))
        assertTrue(t.contains("禁止使用微观悬浮粒子类描述"))
        assertTrue(t.contains("1000字以内"))
        assertTrue(t.contains("严禁输出 Markdown 符号"))
        // 旧内置规则不应残留（长度/英文标签流与新模板互斥）
        assertFalse(t.contains("140 词"))
        assertFalse(t.contains("每个短语不超过 8 个词"))
        // NAI 专属反推不受影响
        val nai = PromptTemplates.defaultReversePromptText("nai-diffusion-4-5-full")
        assertNotSame(t, nai)
        assertTrue(nai.contains("只看图"))
    }

    @Test fun sanitizedOutputKeepsTheFourSectionLabels() {
        // 模板要求「画面风格：…」四段式；清洗链不得把段名吃掉，否则结果退回无结构长句
        val raw = """
            画面风格：自然写实摄影风格，光影过渡柔和细腻，色彩还原真实。
            核心元素：年轻的亚洲女性角色，手持双束粉色玫瑰花，背景为开阔玫瑰花田。
            具体内容：深棕色长发自然垂落肩头，眼神柔和专注，嘴唇微启。
            构图方式：三分法构图，人物位于右侧三分之一处，前景花束为视觉焦点。
        """.trimIndent()
        val out = ImageRepository.sanitizeReversePrompt(raw)
        listOf("画面风格：", "核心元素：", "具体内容：", "构图方式：").forEach {
            assertTrue("清洗后丢了段名 $it", out.contains(it))
        }
        assertTrue(out.contains("自然写实摄影风格"))
        assertTrue(out.contains("三分法构图"))
        // 中文标题仍照旧剥离，未被放宽
        assertEquals("海边日落", ImageRepository.sanitizeReversePrompt("场景：海边日落"))
        // 负面段依然会被裁掉（新模板要求正向，属双保险）
        assertFalse(ImageRepository.sanitizeReversePrompt("画面风格：明亮。\n负面提示词：模糊").contains("模糊"))
        // 代码块围栏与 Markdown 星号被清除
        val fenced = ImageRepository.sanitizeReversePrompt("```\n**画面风格**：胶片质感。\n```")
        assertTrue(fenced.contains("画面风格"))
        assertFalse(fenced.contains("```"))
        assertFalse(fenced.contains("**"))
    }
}