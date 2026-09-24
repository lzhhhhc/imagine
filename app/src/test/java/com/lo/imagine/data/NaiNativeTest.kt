package com.lo.imagine.data
import org.junit.Assert.*
import org.junit.Test
class NaiNativeTest {
    private val base = NaiWorkspaceConfig(prompt = "1girl, garden")
    private fun params(c: NaiWorkspaceConfig = base) = naiNativePayload(c, 0)["parameters"] as Map<*, *>
    @Test fun v45Version() { assertEquals(3, params()["params_version"]) }
    @Test fun v5Version() { assertEquals(4, params(base.copy(model = "nai-diffusion-5-full"))["params_version"]) }
    @Test fun nativeFields() { val p = params(); assertEquals(832, p["width"]); assertEquals(28, p["steps"]); assertEquals(0L, p["seed"]); assertEquals(1, p["n_samples"]) }
    @Test fun noDoubleQuality() { assertEquals(false, params(base.copy(quality = "standard"))["qualityToggle"]) }
    @Test fun invalidDimensionsRejected() { assertTrue(runCatching { params(base.copy(width = "831")) }.isFailure) }
    @Test fun invalidNumberRejected() { assertTrue(runCatching { params(base.copy(scale = "NaN")) }.isFailure) }
    @Test fun weightedArtistsPreserved() { assertTrue(naiNativePayload(base.copy(artists = "1.2::artist:test::"), 42)["input"].toString().contains("1.2::artist:test::")) }
    @Test fun artistInjectedBeforeMainPrompt() {
        val c = base.copy(fixedPrefix = "blue sky", artists = "artist:test", prompt = "red flower, garden", fixedSuffix = "scenery")
        val input = naiNativePayload(c, 0)["input"].toString()
        val artistIdx = input.indexOf("artist:test")
        val promptIdx = input.indexOf("red flower, garden")
        assertTrue("画师串必须排在主体提示词前面", artistIdx in 0 until promptIdx)
    }
    @Test fun artistInjectedBeforeIdentityTag() {
        val c = base.copy(fixedPrefix = "blue sky", artists = "1.2::artist:test::", prompt = "1girl, solo, red flower", fixedSuffix = "scenery")
        val input = naiNativePayload(c, 0)["input"].toString()
        val artistIdx = input.indexOf("artist:test")
        val identityIdx = input.indexOf("1girl")
        assertTrue("画师串必须排在身份词之前", artistIdx in 0 until identityIdx)
        assertTrue(input.indexOf("blue sky") < identityIdx)
        assertTrue(identityIdx < input.indexOf("red flower"))
        assertTrue(1 == Regex("artist:test").findAll(input).count())
    }
    @Test fun furryDatasetKeepsArtistBeforeIdentity() {
        val c = base.copy(furryDataset = true, artists = "artist:test", prompt = "1girl, garden", fixedPrefix = "")
        val input = naiNativePayload(c, 0)["input"].toString()
        assertTrue(input.startsWith("fur dataset, artist:test"))
    }
    @Test fun separateCharacters() { val p = params(base.copy(characterCards = listOf(NaiCharacterPrompt(prompt="1girl, red hair"), NaiCharacterPrompt(prompt="1boy, blue hair")))); assertEquals(2, (p["characterPrompts"] as List<*>).size) }
    @Test fun excessiveCharactersRejected() { assertTrue(runCatching { params(base.copy(characterCards = List(7) { NaiCharacterPrompt(prompt="girl") })) }.isFailure) }
    @Test fun promptPipeRejected() { assertTrue(runCatching { params(base.copy(prompt = "girl | boy")) }.isFailure) }
    @Test fun noTokenInPayload() { assertFalse(com.google.gson.Gson().toJson(naiNativePayload(base.copy(token = "SECRET_TOKEN"), 1)).contains("SECRET_TOKEN")) }
    @Test fun configRoundTrip() { val gson = com.google.gson.Gson(); assertEquals(base, gson.fromJson(gson.toJson(base), NaiWorkspaceConfig::class.java)) }
    @Test fun replaceRulesApplied() {
        val c = base.copy(prompt = "猫娘, garden", promptReplace = "猫娘=cat girl, animal ears\n#注释行\n坏行")
        val input = naiNativePayload(c, 0)["input"].toString()
        assertTrue(input.contains("cat girl, animal ears"))
        assertFalse(input.contains("猫娘"))
    }
    @Test fun replaceRuleWithInsertSyntax() {
        assertEquals(listOf("触发词" to "插入词"), naiReplaceRules("触发词=前置前|插入词"))
    }
    @Test fun smeaAndVarietyFlags() {
        val off = params(base.copy(variety = false))
        assertEquals(false, off["sm"]); assertEquals(false, off["sm_dyn"]); assertEquals(null, off["skip_cfg_above_sigma"])
        val on = params(base.copy(smea = true, smeaDyn = true, variety = true, decrisp = true))
        assertEquals(true, on["sm"]); assertEquals(true, on["sm_dyn"]); assertEquals(true, on["dynamic_thresholding"]); assertEquals(19.0, on["skip_cfg_above_sigma"])
        val dynWithoutSmea = params(base.copy(smeaDyn = true))
        assertEquals(false, dynWithoutSmea["sm_dyn"])
    }
    @Test fun v5OnlyFields() {
        val v45 = params()
        assertFalse(v45.containsKey("straight_alpha"))
        val v5 = params(base.copy(model = "nai-diffusion-5-full", uc = "heavy", straightAlpha = true))
        assertEquals(true, v5["straight_alpha"]); assertEquals(1, v5["tag_hint_uc_preset"]); assertEquals(58.0, v5["skip_cfg_above_sigma"])
    }
    @Test fun furryDatasetPrepended() {
        assertTrue(naiNativePayload(base.copy(furryDataset = true), 0)["input"].toString().startsWith("fur dataset"))
    }
    @Test fun characterCaptionComposition() {
        val card = NaiCharacterPrompt(name = "A", traits = "1girl", face = "red eyes", outfit = "black coat", prompt = "solo")
        assertEquals("1girl, red eyes, black coat, solo", card.caption)
        val chars = params(base.copy(characterCards = listOf(card)))["characterPrompts"] as List<*>
        assertTrue(chars.first().toString().contains("red eyes"))
        assertTrue(chars.first().toString().contains("black coat"))
    }
    @Test fun displayPromptIncludesEnabledCharacters() {
        // 角色走独立字段不进 input：展示/存档必须由 naiDisplayPrompt 补齐
        val card = NaiCharacterPrompt(name = "A", traits = "1girl, red eyes")
        val c = base.copy(characterCards = listOf(card))
        val input = naiNativePayload(c, 0)["input"].toString()
        val display = naiDisplayPrompt(c, input)
        // 展示与请求同源：角色段人数词已转换（1girl→girl）；base 段（input）保留全局人数词
        val charSegment = display.substringAfter(" | ")
        assertTrue(charSegment.contains("girl, red eyes"))
        assertFalse("角色段不应残留 1girl（与请求组装同源）", charSegment.contains("1girl"))
        assertTrue(display.startsWith(input))
        // 未启用 / 空 caption 的角色不出现
        val off = c.copy(characterCards = listOf(card.copy(enabled = false)))
        assertEquals(input, naiDisplayPrompt(off, input))
        val empty = c.copy(characterCards = listOf(card.copy(traits = "", face = "")))
        assertEquals(input, naiDisplayPrompt(empty, input))
    }
    @Test fun characterFieldCaptionDropsCountWords() {
        // NAI 要求人数词只属于 base_caption：char_caption / characterPrompts 里必须去掉 1girl/1boy
        val card = NaiCharacterPrompt(name = "A", traits = "1girl, silver hair", face = "red eyes", prompt = "1boy pose")
        assertEquals("girl, silver hair, red eyes, boy pose", naiCharacterFieldCaption(card))
        val chars = params(base.copy(characterCards = listOf(card)))["characterPrompts"] as List<*>
        val first = chars.first() as Map<*, *>
        val promptField = first["prompt"].toString()
        assertFalse("角色字段不能残留 1girl", promptField.contains("1girl"))
        assertTrue(promptField.contains("girl, silver hair"))
        // base_caption 不受影响（人数词留在全局）
        assertTrue(params(base.copy(prompt = "1girl, garden"))["v4_prompt"].toString().contains("1girl"))
    }
    @Test fun coordinatesEmptyWhenUseCoordsOff() {
        val card = NaiCharacterPrompt(name = "A", traits = "girl", x = 0.5, y = 0.5)
        // 默认 useCoords=false：坐标传空对象，不把角色钉死在中心
        val offParams = params(base.copy(characterCards = listOf(card)))
        val offChars = offParams["characterPrompts"] as List<*>
        val offCenter = (offChars.first() as Map<*, *>)["center"] as Map<*, *>
        assertTrue("use_coords 关闭时 center 必须为空", offCenter.isEmpty())
        val v4 = offParams["v4_prompt"] as Map<*, *>
        val caption = v4["caption"] as Map<*, *>
        val charCaptions = caption["char_captions"] as List<*>
        val centers = (charCaptions.first() as Map<*, *>)["centers"] as List<*>
        assertTrue("use_coords 关闭时 centers 必须为空对象", (centers.first() as Map<*, *>).isEmpty())
        // 开启 useCoords：坐标正常传出
        val onParams = params(base.copy(useCoords = true, characterCards = listOf(card)))
        val onChars = onParams["characterPrompts"] as List<*>
        val onCenter = (onChars.first() as Map<*, *>)["center"] as Map<*, *>
        assertEquals(0.5, onCenter["x"]); assertEquals(0.5, onCenter["y"])
    }
    @Test fun encryptedExportResolvesNameMapAndOutfits() {
        // 完整加密导出：角色名匿名化（CHAR_001）、服装匿名化（OUTFIT_001）+ 顶层 outfits 描述字典
        fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        val nameMapJson = """{"characters":{"CHAR_001":"爱丽丝"},"outfits":{"OUTFIT_001":"校服"}}"""
        val json = """
        {
            "_encrypted": true,
            "_nameMap": "${b64(nameMapJson)}",
            "characters": {
                "CHAR_001": {
                    "nameCN": "${b64("爱丽丝")}",
                    "characterTraits": "1girl, blonde hair",
                    "outfits": ["${b64("OUTFIT_001")}"]
                }
            },
            "outfits": {
                "OUTFIT_001": {
                    "nameCN": "${b64("校服")}",
                    "upperBody": "${b64("white blouse, red ribbon")}",
                    "fullBody": "${b64("blue skirt, black socks")}"
                }
            }
        }
        """.trimIndent()
        val list = Chatu8CharacterImporter.parseJson(json)
        assertEquals(1, list.size)
        val char = list.first()
        assertEquals("爱丽丝", char.name)
        // 服装：匿名 ID → 描述字典 → 真实标签（上半身 + 下半身）
        assertTrue("服装描述必须来自顶层字典", char.outfit.contains("white blouse, red ribbon"))
        assertTrue(char.outfit.contains("blue skirt, black socks"))
        assertFalse("匿名 ID 不能残留", char.outfit.contains("OUTFIT_001"))
    }
    @Test fun fusedCaptionOverridesMechanicalCaption() {
        // LLM 场景整理产物非空时，请求组装优先使用整理版（按场景取景推理），
        // 而不是机械拼接的 caption——这是「角色数据 → LLM 推理 → 场景适配」链路的关键。
        val card = NaiCharacterPrompt(name = "A", traits = "1girl, silver hair", face = "red eyes", outfit = "black coat")
        val c = base.copy(characterCards = listOf(card))
        // 无整理：用机械拼接
        assertEquals("girl, silver hair, red eyes, black coat", naiCharacterFieldCaption(card))
        // 有整理：优先整理版
        val fused = card.copy(fusedCaption = "silver hair, red eyes, close-up")
        assertEquals("silver hair, red eyes, close-up", naiCharacterFieldCaption(fused))
        // 请求组装同样生效
        val chars = params(c.copy(characterCards = listOf(fused)))["characterPrompts"] as List<*>
        val promptField = (chars.first() as Map<*, *>)["prompt"].toString()
        assertEquals("silver hair, red eyes, close-up", promptField)
    }
    @Test fun fuseInstructionsCarrySceneReasoningRules() {
        // 融合提示词工程必须包含取景推理规则（特写/背面/服装取舍），而不是简单拼接指令
        val text = naiFuseInstructions(null)
        assertTrue(text.contains("取景"))
        assertTrue(text.contains("from behind"))
        assertTrue(text.contains("1girl"))
        assertTrue(text.contains("不要出现在输出"))
    }
    @Test fun characterMatrixFrontBackSfwNsfw() {
        val fullCard = NaiCharacterPrompt(
            name = "TestChar",
            traits = "1girl, silver hair",
            face = "blue eyes",
            faceBack = "ponytail from behind",
            upperSfw = "white blouse",
            upperSfwBack = "blouse from behind",
            lowerSfw = "blue skirt",
            lowerSfwBack = "skirt from behind",
            upperNsfw = "bare breasts",
            upperNsfwBack = "bare back",
            lowerNsfw = "pussy",
            lowerNsfwBack = "ass",
            viewAngle = "front",
            bodyMode = "sfw"
        )
        // 正面 SFW
        assertEquals("1girl, silver hair, blue eyes, white blouse, blue skirt", fullCard.caption)
        // 背面 SFW
        val backSfw = fullCard.copy(viewAngle = "back")
        assertEquals("1girl, silver hair, ponytail from behind, blouse from behind, skirt from behind", backSfw.caption)
        // 正面 NSFW
        val frontNsfw = fullCard.copy(bodyMode = "nsfw")
        assertEquals("1girl, silver hair, blue eyes, bare breasts, pussy", frontNsfw.caption)
        // 背面 NSFW
        val backNsfw = fullCard.copy(viewAngle = "back", bodyMode = "nsfw")
        assertEquals("1girl, silver hair, ponytail from behind, bare back, ass", backNsfw.caption)
    }
    @Test fun sfwModeStripsNsfwTagsFromArrangedCaption() {
        // 整理结果残留 NSFW 标签时，SFW 状态必须在请求与展示的同一入口把它剔除——开关必须真的关得掉
        val sfw = NaiCharacterPrompt(name = "A", traits = "silver hair", face = "red eyes",
            upperNsfw = "bare breasts, nipples", lowerNsfw = "pussy",
            bodyMode = "sfw", fusedCaption = "silver hair, red eyes, bare breasts, smiling, nipples, pussy")
        assertEquals("silver hair, red eyes, smiling", naiCharacterFieldCaption(sfw))
        // NSFW 状态保留原样
        val nsfw = sfw.copy(bodyMode = "nsfw")
        assertEquals("silver hair, red eyes, bare breasts, smiling, nipples, pussy", naiCharacterFieldCaption(nsfw))
        // custom（仅服装）同样按 SFW 处理
        val custom = sfw.copy(bodyMode = "custom")
        assertEquals("silver hair, red eyes, smiling", naiCharacterFieldCaption(custom))
    }
    @Test fun chatu8CharacterImportParsed() {
        val json = """
        {
            "characters": {
                "爱丽丝": {
                    "nameCN": "爱丽丝",
                    "nameEN": "Alice",
                    "characterTraits": "1girl, blonde hair, blue eyes",
                    "facialFeatures": "smile, blue eyes",
                    "facialFeaturesBack": "hair ribbons from behind",
                    "upperBodySFW": "blue apron dress",
                    "fullBodySFW": "white pantyhose, mary janes",
                    "outfits": ["maid dress", "casual shirt"]
                }
            }
        }
        """.trimIndent()
        val list = Chatu8CharacterImporter.parseJson(json)
        assertEquals(1, list.size)
        val char = list.first()
        assertEquals("爱丽丝", char.name)
        assertEquals("Alice", char.nameEn)
        assertEquals("1girl, blonde hair, blue eyes", char.traits)
        assertEquals("blue apron dress", char.upperSfw)
        assertEquals("white pantyhose, mary janes", char.lowerSfw)
        assertEquals("maid dress, casual shirt", char.outfit)
    }
    @Test fun encryptedExportDecryptsOnlyListedFields() {
        // st-chatu8 加密导出：仅 12 个字段被 Base64 混淆（与插件 encryptCharacterPreset 一致），
        // characterTraits / prompt / negative 等保持明文。盲解会把形似 Base64 的明文（如 "solo"）解成乱码——
        // 导入器必须按清单精确解密，其余字段原样保留。
        fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        val json = """
        {
            "_encrypted": true,
            "characters": {
                "CHAR_001": {
                    "nameCN": "${b64("爱丽丝")}",
                    "nameEN": "${b64("Alice")}",
                    "characterTraits": "solo",
                    "facialFeatures": "${b64("blue eyes")}",
                    "upperBodySFW": "${b64("white blouse")}",
                    "outfits": ["${b64("校服")}"]
                }
            }
        }
        """.trimIndent()
        val list = Chatu8CharacterImporter.parseJson(json)
        assertEquals(1, list.size)
        val char = list.first()
        // 清单内字段：正确还原
        assertEquals("爱丽丝", char.name)
        assertEquals("Alice", char.nameEn)
        assertEquals("blue eyes", char.face)
        assertEquals("white blouse", char.upperSfw)
        // 清单外明文：即便形似 Base64（"solo" 是合法 Base64 串，旧实现会解成乱码）也必须原样保留
        assertEquals("solo", char.traits)
        // 加密导出的 outfits 元素（Base64 混淆）正常解码
        assertEquals("校服", char.outfit)
    }
    @Test fun backendConnectionFollowedByDefault() {
        val s = AppSettings(baseUrl = "https://relay.example/api", apiKey = "sk-abc")
        val c = base.copy(token = "own-token", endpoint = "https://own.example/api")
        assertTrue(c.useBackendApi)
        assertEquals("https://relay.example/api", naiEffectiveEndpoint(c, s))
        assertEquals("sk-abc", naiEffectiveToken(c, s))
        val off = c.copy(useBackendApi = false)
        assertEquals("https://own.example/api", naiEffectiveEndpoint(off, s))
        assertEquals("own-token", naiEffectiveToken(off, s))
    }
    @Test fun rootOnlyEndpointGetsNativeGeneratePath() {
        // 实测中转常只填到站点根；直接打根路径会挂到超时，这里补成 NAI 原生路径
        assertEquals("https://relay.example/ai/generate-image", completeNaiEndpoint("https://relay.example"))
        assertEquals("https://relay.example/ai/generate-image", completeNaiEndpoint("https://relay.example/"))
        assertEquals("https://relay.example/ai/generate-image", completeNaiEndpoint("https://relay.example   "))
        // 已带路径的地址原样保留，不做二次拼接
        assertEquals("https://relay.example/v1/images/generations", completeNaiEndpoint("https://relay.example/v1/images/generations"))
        assertEquals("https://relay.example/api", completeNaiEndpoint("https://relay.example/api"))
        val s = AppSettings(baseUrl = "https://relay.example", apiKey = "sk-abc")
        assertEquals("https://relay.example/ai/generate-image", naiEffectiveEndpoint(base, s))
        assertEquals("https://own.example/ai/generate-image", naiEffectiveEndpoint(base.copy(useBackendApi = false, endpoint = "https://own.example"), s))
    }
    @Test fun upstreamFailuresExplained() {
        assertTrue(naiHttpHint(502).contains("上游"))
        assertTrue(naiHttpHint(503).contains("上游"))
        assertTrue(naiHttpHint(403).contains("Key"))
        assertTrue(naiHttpHint(429).contains("限流"))
        assertEquals("", naiHttpHint(400))
    }
    @Test fun chatContentShapes() {
        // 标准：content 是字符串
        assertEquals("hello", chatContentOf("""{"choices":[{"message":{"content":"hello"}}]}"""))
        // 分块数组：[{type:text,text:...}]
        assertEquals("a\nb", chatContentOf("""{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}"""))
        // 老式 completion：choices[0].text
        assertEquals("legacy", chatContentOf("""{"choices":[{"text":"legacy"}]}"""))
        // 非 JSON / 结构不符 → 空串（由调用方给提示）
        assertEquals("", chatContentOf("not json"))
        assertEquals("", chatContentOf("""{"choices":[]}"""))
    }
    @Test fun reasoningOnlyIsDiagnosed() {
        val onlyReasoning = """{"choices":[{"message":{"content":"","reasoning_content":"let me think..."}}]}"""
        assertEquals("", chatContentOf(onlyReasoning))
        val hint = requireNotNull(reasoningOnlyHintOf(onlyReasoning))
        assertTrue(hint.contains("思考"))
        // 有正文时不应误判
        assertNull(reasoningOnlyHintOf("""{"choices":[{"message":{"content":"ok","reasoning_content":"..."}}]}"""))
    }
    @Test fun http200WithErrorBodySurfacesMessage() {
        val e = runCatching { chatContentOf("""{"error":{"message":"invalid model"}}""") }.exceptionOrNull()
        assertTrue(requireNotNull(e).message!!.contains("invalid model"))
    }
}