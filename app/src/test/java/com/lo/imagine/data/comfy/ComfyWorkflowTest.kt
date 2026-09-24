package com.lo.imagine.data.comfy

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

internal fun sampleGraph(): JsonObject = ComfyWorkflowEngine.parse("""{
  "1":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"model.safetensors"}},
  "2":{"class_type":"CLIPTextEncode","inputs":{"text":"a paper bird on a wooden desk","clip":["1",1]}},
  "3":{"class_type":"CLIPTextEncode","inputs":{"text":"blurry","clip":["1",1]}},
  "4":{"class_type":"EmptyLatentImage","inputs":{"width":512,"height":512,"batch_size":1}},
  "5":{"class_type":"KSampler","inputs":{"model":["1",0],"positive":["2",0],"negative":["3",0],"latent_image":["4",0],"seed":9007199254740993,"steps":20,"cfg":7.5,"sampler_name":"euler","scheduler":"normal","denoise":1.0}},
  "6":{"class_type":"VAEDecode","inputs":{"samples":["5",0],"vae":["1",2]}},
  "7":{"class_type":"SaveImage","inputs":{"images":["6",0],"filename_prefix":"Imagine_Test"}}
}""")
internal fun sampleWorkflow(): ComfyWorkflow = sampleGraph().let {
    ComfyWorkflow(name = "Paper bird", graph = it, parameters = ComfyWorkflowEngine.suggest(it), outputNodes = listOf("7"))
}

class ComfyWorkflowTest {
    @Test fun `standard workflow suggests direct prompt links and preserves original graph`() {
        val w = sampleWorkflow()
        assertEquals(listOf(ParameterKind.PROMPT, ParameterKind.NEGATIVE, ParameterKind.SEED, ParameterKind.WIDTH, ParameterKind.HEIGHT), w.parameters.map { it.kind })
        assertEquals(InputTarget("2", "text"), w.parameters.first { it.kind == ParameterKind.PROMPT }.targets.single())
        val copy = w.copy(parameters = w.parameters.map { if (it.kind == ParameterKind.PROMPT) it.copy(value = "a blue vase\n\"on a table\"") else it })
        val prepared = ComfyWorkflowEngine.prepare(copy)
        assertEquals("a paper bird on a wooden desk", w.graph.getAsJsonObject("2").getAsJsonObject("inputs").get("text").asString)
        assertEquals("a blue vase\n\"on a table\"", prepared.graph.getAsJsonObject("2").getAsJsonObject("inputs").get("text").asString)
        assertEquals(w.graph.getAsJsonObject("5").getAsJsonObject("inputs").get("model"), prepared.graph.getAsJsonObject("5").getAsJsonObject("inputs").get("model"))
    }
    @Test fun `uint64 seeds survive binding and gson persistence without double conversion`() {
        for (seed in listOf("9007199254740993", "18446744073709551615")) {
            val w = sampleWorkflow().let { it.copy(parameters = it.parameters.map { p -> if (p.kind == ParameterKind.SEED) p.copy(value = seed) else p }) }
            val restored = Gson().fromJson(Gson().toJson(w), ComfyWorkflow::class.java)
            assertEquals(seed, ComfyWorkflowEngine.prepare(restored).graph.getAsJsonObject("5").getAsJsonObject("inputs").get("seed").asString)
        }
    }
    @Test fun `ambiguous samplers do not pick the first prompt or sampler`() {
        val graph = sampleGraph().apply { add("8", get("5").deepCopy()) }
        assertTrue(ComfyWorkflowEngine.suggest(graph).none { it.kind in setOf(ParameterKind.PROMPT, ParameterKind.NEGATIVE, ParameterKind.SEED, ParameterKind.STEPS) })
    }
    @Test fun `multiple output nodes require explicit choice`() {
        val graph = sampleGraph().apply { add("8", get("7").deepCopy()) }
        assertTrue(ComfyWorkflowEngine.suggestedOutputs(graph).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.prepare(sampleWorkflow().copy(outputNodes = emptyList())) }
    }
    @Test fun `custom scalar binding and multiple targets preserve numeric types`() {
        val graph = sampleGraph().apply { add("8", get("2").deepCopy()) }
        val p = WorkflowParameter(label = "Shared text", targets = listOf(InputTarget("2", "text"), InputTarget("8", "text")), value = "still life")
        val out = ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = listOf(p), outputNodes = listOf("7"))).graph
        assertEquals("still life", out.getAsJsonObject("8").getAsJsonObject("inputs").get("text").asString)
        assertTrue(out.getAsJsonObject("5").getAsJsonObject("inputs").get("seed").asJsonPrimitive.isNumber)
    }
    @Test fun `links duplicate bindings and incompatible field types are rejected`() {
        val w = sampleWorkflow()
        for (p in listOf(
            WorkflowParameter(label = "link", targets = listOf(InputTarget("5", "model")), value = "bad"),
            WorkflowParameter(label = "mixed", targets = listOf(InputTarget("2", "text"), InputTarget("5", "steps")), value = "5"),
            WorkflowParameter(label = "seed", kind = ParameterKind.SEED, targets = listOf(InputTarget("2", "text")), value = "5")
        )) assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.prepare(w.copy(parameters = listOf(p))) }
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.prepare(w.copy(parameters = w.parameters + w.parameters.first())) }
    }
    @Test fun `invalid and frontend JSON fail with useful errors`() {
        val message = assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.parse("""{"nodes":[],"links":[]}""") }.message!!
        assertTrue(message.contains("Export Workflow (API)"))
        for (raw in listOf("[]", "null", "{}", "{bad}", """{"1":{"class_type":"X","inputs":{},},}""", """{"1":{"class_type":"X","inputs":{"link":["99",0]}}}""")) {
            assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.parse(raw) }
        }
    }
    @Test fun `size and nesting limits are enforced before parsing`() {
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.parse(" ".repeat(ComfyWorkflowEngine.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.parse("[".repeat(65) + "]".repeat(65)) }
    }
    @Test fun `random seed is resolved once and recorded exactly`() {
        val w = sampleWorkflow().let { it.copy(parameters = it.parameters.map { p -> if (p.kind == ParameterKind.SEED) p.copy(randomSeed = true, value = "") else p }) }
        ComfyWorkflowEngine.prepare(w, resolveRandom = false)
        val prepared = ComfyWorkflowEngine.prepare(w)
        val seed = prepared.graph.getAsJsonObject("5").getAsJsonObject("inputs").get("seed").asString
        assertTrue(BigInteger(seed) >= BigInteger.ZERO)
        assertEquals(seed, prepared.values.entries.first { it.key.startsWith("种子") }.value)
    }
    @Test fun `negative overflow and fractional seeds are rejected`() {
        for (seed in listOf("-1", "18446744073709551616", "1.5", "NaN")) {
            val w = sampleWorkflow().let { it.copy(parameters = it.parameters.map { p -> if (p.kind == ParameterKind.SEED) p.copy(value = seed) else p }) }
            assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.prepare(w) }
        }
    }
    @Test fun `integer spelled defaults still allow fractional float inputs`() {
        val graph = sampleGraph().apply {
            getAsJsonObject("5").getAsJsonObject("inputs").addProperty("cfg", 7)
            getAsJsonObject("5").getAsJsonObject("inputs").addProperty("denoise", 1)
        }
        val parameters = ComfyWorkflowEngine.suggest(graph) +
            WorkflowParameter(label = "CFG", kind = ParameterKind.CFG, targets = listOf(InputTarget("5", "cfg")), value = "6.5") +
            WorkflowParameter(label = "Denoise", targets = listOf(InputTarget("5", "denoise")), value = "0.75")
        val inputs = ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = parameters, outputNodes = listOf("7"))).graph.getAsJsonObject("5").getAsJsonObject("inputs")
        assertEquals("6.5", inputs.get("cfg").asString)
        assertEquals("0.75", inputs.get("denoise").asString)
    }
    @Test fun `custom integer input is constrained by server schema`() {
        val graph = ComfyWorkflowEngine.parse("""{"1":{"class_type":"Custom","inputs":{"count":1}}}""")
        val parameter = WorkflowParameter(label = "Count", targets = listOf(InputTarget("1", "count")), value = "1.5")
        val prepared = ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = listOf(parameter), outputNodes = listOf("1")))
        val schema = JsonParser.parseString("""{"input":{"required":{"count":["INT",{"min":1,"max":10}]}}}""").asJsonObject
        val error = assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.validateWithInfo(prepared.graph, mapOf("Custom" to schema)) }
        assertTrue(error.message!!.contains("1.count"))
    }
    @Test fun `image bindings accept only LoadImage image inputs and require an uploaded filename`() {
        val graph = ComfyWorkflowEngine.parse("""{"1":{"class_type":"LoadImage","inputs":{"image":"example.png","upload":"image"}},"2":{"class_type":"CLIPTextEncode","inputs":{"text":"a","clip":["3",1]}},"3":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"model.safetensors"}}}""")
        val loader = WorkflowParameter(label = "参考图片", kind = ParameterKind.IMAGE, targets = listOf(InputTarget("1", "image")), value = "example.png")
        val prepared = ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = listOf(loader), outputNodes = listOf("1")))
        assertEquals("example.png", prepared.graph.getAsJsonObject("1").getAsJsonObject("inputs").get("image").asString)
        // 绑定错误目标（文本节点）必须被拒绝
        assertThrows(IllegalArgumentException::class.java) {
            ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = listOf(WorkflowParameter(label = "参考图片", kind = ParameterKind.IMAGE, targets = listOf(InputTarget("2", "text")), value = "example.png")), outputNodes = listOf("1")))
        }
        // 空文件名在提交前被拒绝
        assertThrows(IllegalArgumentException::class.java) {
            ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = listOf(loader.copy(value = "")), outputNodes = listOf("1")))
        }
    }
    @Test fun `prompt behind conditioning nodes is bound once and a side prompt stays unbound`() {
        val graph = ComfyWorkflowEngine.parse("""{
          "1":{"class_type":"CLIPTextEncode","inputs":{"text":"edit the shirt","clip":["2",0]}},
          "2":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"m.safetensors"}},
          "3":{"class_type":"ReferenceLatent","inputs":{"conditioning":["1",0],"latent":["4",0]}},
          "4":{"class_type":"VAEEncode","inputs":{"pixels":["6",0],"vae":["2",2]}},
          "5":{"class_type":"ConditioningZeroOut","inputs":{"conditioning":["1",0]}},
          "6":{"class_type":"LoadImage","inputs":{"image":"ref.png"}},
          "7":{"class_type":"ReferenceLatent","inputs":{"conditioning":["5",0],"latent":["4",0]}},
          "8":{"class_type":"KSampler","inputs":{"model":["2",0],"positive":["3",0],"negative":["7",0],"latent_image":["4",0],"seed":1,"steps":6,"cfg":1,"sampler_name":"euler","scheduler":"simple","denoise":1}},
          "9":{"class_type":"CLIPTextEncode","inputs":{"text":"clothes","clip":["2",1]}},
          "10":{"class_type":"SaveImage","inputs":{"images":["6",0],"filename_prefix":"x"}}
        }""")
        val suggested = ComfyWorkflowEngine.suggest(graph)
        val prompt = suggested.single { it.kind == ParameterKind.PROMPT }
        assertEquals(InputTarget("1", "text"), prompt.targets.single())
        assertEquals("edit the shirt", prompt.value)
        assertTrue(suggested.none { it.kind == ParameterKind.NEGATIVE || it.targets.any { target -> target.nodeId == "9" } })
        assertEquals(InputTarget("6", "image"), suggested.single { it.kind == ParameterKind.IMAGE }.targets.single())
        assertTrue(suggested.none { it.kind in setOf(ParameterKind.STEPS, ParameterKind.CFG) })
    }
    @Test fun `two texts on one conditioning chain are not guessed`() {
        val graph = ComfyWorkflowEngine.parse("""{
          "1":{"class_type":"CLIPTextEncode","inputs":{"text":"a","clip":["4",0]}},
          "2":{"class_type":"CLIPTextEncode","inputs":{"text":"b","clip":["4",0]}},
          "3":{"class_type":"ConditioningCombine","inputs":{"conditioning_1":["1",0],"conditioning_2":["2",0]}},
          "4":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"m.safetensors"}},
          "5":{"class_type":"KSampler","inputs":{"model":["4",0],"positive":["3",0],"negative":["2",0],"latent_image":["4",0],"seed":1,"steps":1,"cfg":1,"sampler_name":"euler","scheduler":"simple","denoise":1}}
        }""")
        val suggested = ComfyWorkflowEngine.suggest(graph)
        assertTrue(suggested.none { it.kind == ParameterKind.PROMPT })
        assertEquals(InputTarget("2", "text"), suggested.single { it.kind == ParameterKind.NEGATIVE }.targets.single())
    }
    @Test fun `a single load image is suggested and several are left for the user`() {
        val one = sampleGraph().apply { add("8", JsonParser.parseString("""{"class_type":"LoadImage","inputs":{"image":"ref.png"}}""")) }
        val suggested = ComfyWorkflowEngine.suggest(one)
        assertEquals(InputTarget("8", "image"), suggested.single { it.kind == ParameterKind.IMAGE }.targets.single())
        assertEquals("ref.png", suggested.single { it.kind == ParameterKind.IMAGE }.value)
        val two = one.deepCopy().apply { add("9", get("8").deepCopy()) }
        assertTrue(ComfyWorkflowEngine.suggest(two).none { it.kind == ParameterKind.IMAGE })
        assertEquals(listOf(InputTarget("4", "width")), suggested.single { it.kind == ParameterKind.WIDTH }.targets)
        assertEquals("512", suggested.single { it.kind == ParameterKind.WIDTH }.value)
        assertEquals(listOf(InputTarget("4", "height")), suggested.single { it.kind == ParameterKind.HEIGHT }.targets)
        val resized = sampleGraph().apply {
            add("9", JsonParser.parseString("""{"class_type":"CustomLatentResize","inputs":{"samples":["4",0],"width":768,"height":1024}}"""))
            getAsJsonObject("5").getAsJsonObject("inputs").add("latent_image", JsonParser.parseString("""["9",0]"""))
        }
        val resizedSuggested = ComfyWorkflowEngine.suggest(resized)
        assertEquals(InputTarget("9", "width"), resizedSuggested.single { it.kind == ParameterKind.WIDTH }.targets.single())
        assertEquals("1024", resizedSuggested.single { it.kind == ParameterKind.HEIGHT }.value)
        assertTrue(suggested.none { it.kind in setOf(ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.BATCH) })
    }
    @Test fun `photo formats are recognized and unsafe names become uploadable`() {
        assertEquals("png", comfyImageKind(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("jpg", comfyImageKind(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        val webp = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        assertEquals("webp", comfyImageKind(webp))
        assertNull(comfyImageKind("ftypheic".toByteArray()))
        assertEquals("cat 空格.png", comfyUploadFilename("cat 空格.png", "png"))
        assertEquals("photo.jpg", comfyUploadFilename("photo.HEIC", "jpg"))
        assertEquals("photo.jpeg", comfyUploadFilename("photo.jpeg", "jpg"))
        assertEquals("reference.png", comfyUploadFilename("\"bad\nname\".heic", "png"))
    }
    @Test fun `subfolder reference image values are preserved for later uploads`() {
        assertEquals("sub/图片 空格.png", UploadedImage("图片 空格.png", "sub").inputValue)
        assertEquals("a.png", UploadedImage("a.png", "").inputValue)
        assertThrows(IllegalArgumentException::class.java) { UploadedImage("../evil.png") }
        assertThrows(IllegalArgumentException::class.java) { UploadedImage("a.png", type = "temp") }
    }
    @Test fun `combo options fall back instead of crashing on exotic server entries`() {
        assertEquals(listOf("euler", "dpmpp_2m"), ComfyWorkflowEngine.comboOptions(JsonParser.parseString("""["euler","dpmpp_2m"]""")))
        assertNull(ComfyWorkflowEngine.comboOptions(JsonParser.parseString("""["euler",{"name":"x"}]""")))
        assertNull(ComfyWorkflowEngine.comboOptions(JsonParser.parseString("""[{"name":"x"}]""")))
        assertNull(ComfyWorkflowEngine.comboOptions(JsonParser.parseString("[]")))
        assertNull(ComfyWorkflowEngine.comboOptions(JsonParser.parseString(""""INT"""")))
        assertNull(ComfyWorkflowEngine.comboOptions(JsonParser.parseString("""[["nested"]]""")))
        assertNull(ComfyWorkflowEngine.comboOptions(null))
    }
    @Test fun `exotic combo lists do not block validation while plain lists stay strict`() {
        val graph = ComfyWorkflowEngine.parse("""{"1":{"class_type":"Custom","inputs":{"model":"weird"}}}""")
        val exotic = JsonParser.parseString("""{"input":{"required":{"model":[[{"name":"weird"}]]}}}""").asJsonObject
        ComfyWorkflowEngine.validateWithInfo(graph, mapOf("Custom" to exotic))
        val plain = JsonParser.parseString("""{"input":{"required":{"model":[["available"]]}}}""").asJsonObject
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.validateWithInfo(graph, mapOf("Custom" to plain)) }
    }
    @Test fun `model analysis keeps the real size and drops invented bindings`() {
        val graph = sampleGraph().apply {
            add("9", JsonParser.parseString("""{"class_type":"CustomLatentResize","inputs":{"samples":["4",0],"width":768,"height":1024}}"""))
            getAsJsonObject("5").getAsJsonObject("inputs").add("latent_image", JsonParser.parseString("""["9",0]"""))
        }
        val digest = ComfyWorkflowEngine.digest(graph)
        assertTrue(digest.startsWith("5 KSampler"))
        assertTrue(digest.indexOf("9 CustomLatentResize") < digest.indexOf("4 EmptyLatentImage"))
        val reply = """
            分析如下：
            {"bindings":[
              {"kind":"PROMPT","label":"画面提示词","reason":"正向文本","targets":[{"node":"2","input":"text"}]},
              {"kind":"WIDTH","targets":[{"node":4,"input":"width"}]},
              {"kind":"HEIGHT","targets":[{"node":9,"input":"height"}]},
              {"kind":"CFG","targets":[{"node":"5","input":"cfg"}]},
              {"kind":"STEPS","targets":[{"node":"5","input":"steps"}]},
              {"kind":"SAMPLER","targets":[{"node":"5","input":"sampler_name"}]},
              {"kind":"PROMPT","targets":[{"node":"3","input":"text"}]},
              {"kind":"IMAGE","targets":[{"node":"7","input":"images"}]},
              {"kind":"WIDTH","targets":[{"node":"5","input":"positive"}]}
            ]}
        """.trimIndent()
        val bindings = ComfyWorkflowEngine.bindingsFromAnalysis(graph, reply)
        assertEquals(listOf(ParameterKind.PROMPT, ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.SAMPLER), bindings.map { it.kind })
        assertEquals(InputTarget("2", "text"), bindings.single { it.kind == ParameterKind.PROMPT }.targets.single())
        assertEquals(InputTarget("4", "width"), bindings.single { it.kind == ParameterKind.WIDTH }.targets.single())
        assertEquals(InputTarget("9", "height"), bindings.single { it.kind == ParameterKind.HEIGHT }.targets.single())
        assertEquals("euler", bindings.single { it.kind == ParameterKind.SAMPLER }.value)
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.bindingsFromAnalysis(graph, "没有 JSON") }
        assertTrue(ComfyWorkflowEngine.bindingsFromAnalysis(graph, """{"bindings":[]}""").isEmpty())
        val nodes = ComfyWorkflowEngine.nodesFromAnalysis(graph, """{"nodes":[5,"9",99,"6"]}""")
        assertEquals(listOf("5", "9"), nodes)
        val panel = ComfyWorkflowEngine.panelParameters(graph, "5")
        assertEquals(listOf("seed", "steps", "cfg", "sampler_name", "scheduler", "denoise"), panel.map { it.targets.single().input })
        assertEquals(setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.SAMPLER, ParameterKind.CUSTOM), panel.map { it.kind }.toSet())
        assertEquals(setOf("2", "3", "4", "5", "9"), ComfyWorkflowEngine.nodesFromAnalysis(graph, reply).toSet())
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.nodesFromAnalysis(graph, "没有 JSON") }
    }
    @Test fun `one card can bind several matching fields`() {
        val graph = ComfyWorkflowEngine.parse("""{
          "1":{"class_type":"KSamplerAdvanced","inputs":{"noise_seed":1,"steps":8,"cfg":1}},
          "2":{"class_type":"KSamplerAdvanced","inputs":{"noise_seed":2,"steps":8,"cfg":1.1}},
          "3":{"class_type":"SaveImage","inputs":{"filename_prefix":"ComfyUI","images":["1",0]}}
        }""")
        val seeds = graph.entrySet().mapNotNull { (id, node) ->
            node.asJsonObject.getAsJsonObject("inputs").get("noise_seed")?.takeIf { it.isJsonPrimitive }?.let { InputTarget(id, "noise_seed") }
        }
        val card = WorkflowParameter(label = "两段种子", kind = ParameterKind.SEED, targets = seeds, value = "42")
        val prepared = ComfyWorkflowEngine.prepare(ComfyWorkflow(name = "多绑定", graph = graph, parameters = listOf(card), outputNodes = listOf("3")))
        assertEquals(2, seeds.size)
        assertEquals("42", prepared.graph.getAsJsonObject("1").getAsJsonObject("inputs").get("noise_seed").asString)
        assertEquals("42", prepared.graph.getAsJsonObject("2").getAsJsonObject("inputs").get("noise_seed").asString)
        assertEquals("8", prepared.graph.getAsJsonObject("1").getAsJsonObject("inputs").get("steps").asString)
    }
    @Test fun `bypass nodes keep their mode and disabled outputs are blocked until re-enabled`() {
        val graph = sampleGraph()
        assertEquals(7, ComfyWorkflowEngine.enabledNodeCount(graph))
        assertTrue(ComfyWorkflowEngine.nodeEnabled(graph, "5"))
        graph.getAsJsonObject("5").addProperty("mode", 2)
        assertEquals(2, ComfyWorkflowEngine.nodeMode(graph, "5"))
        assertFalse(ComfyWorkflowEngine.nodeEnabled(graph, "5"))
        assertEquals(6, ComfyWorkflowEngine.enabledNodeCount(graph))
        // 停用节点上的参数面板仍然完整；普通节点停用不阻止 prepare（服务器端跳过执行）
        val panel = ComfyWorkflowEngine.panelParameters(graph, "5")
        assertEquals(6, panel.size)
        ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = panel, outputNodes = listOf("7")))
        // 输出节点被停用必须先启用
        graph.getAsJsonObject("7").addProperty("mode", 4)
        assertEquals(4, ComfyWorkflowEngine.nodeMode(graph, "7"))
        assertEquals(5, ComfyWorkflowEngine.enabledNodeCount(graph))
        assertThrows(IllegalArgumentException::class.java) {
            ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = graph, parameters = emptyList(), outputNodes = listOf("7")))
        }
        val fixed = ComfyWorkflowEngine.setNodeMode(graph, "7", ComfyWorkflowEngine.MODE_ENABLED)
        assertEquals(0, ComfyWorkflowEngine.nodeMode(fixed, "7"))
        assertTrue(ComfyWorkflowEngine.nodeEnabled(fixed, "7"))
        ComfyWorkflowEngine.prepare(ComfyWorkflow(graph = fixed, parameters = emptyList(), outputNodes = listOf("7")))
        // setNodeMode 是副本操作，原图保持不变
        assertEquals(4, ComfyWorkflowEngine.nodeMode(graph, "7"))
    }
    @Test fun `disabled nodes do not require server schemas`() {
        val graph = ComfyWorkflowEngine.parse("""{"1":{"class_type":"MissingCustomNode","inputs":{"value":"x"}}}""")
        graph.getAsJsonObject("1").addProperty("mode", ComfyWorkflowEngine.MODE_BYPASS)
        assertEquals(emptyList<String>(), ComfyWorkflowEngine.enabledClassTypes(graph))
        ComfyWorkflowEngine.validateWithInfo(graph, emptyMap())
    }

    @Test fun `server choices and numeric bounds are respected`() {
        val graph = ComfyWorkflowEngine.parse("""{"1":{"class_type":"Custom","inputs":{"model":"missing","steps":2}}}""")
        val schema = JsonParser.parseString("""{"input":{"required":{"model":[["available"]],"steps":["INT",{"min":1,"max":10}]}}}""").asJsonObject
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.validateWithInfo(graph, mapOf("Custom" to schema)) }
        graph.getAsJsonObject("1").getAsJsonObject("inputs").addProperty("model", "available")
        ComfyWorkflowEngine.validateWithInfo(graph, mapOf("Custom" to schema))
        graph.getAsJsonObject("1").getAsJsonObject("inputs").addProperty("steps", 11)
        assertThrows(IllegalArgumentException::class.java) { ComfyWorkflowEngine.validateWithInfo(graph, mapOf("Custom" to schema)) }
    }
}