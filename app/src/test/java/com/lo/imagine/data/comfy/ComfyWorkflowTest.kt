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
        assertEquals(8, w.parameters.size)
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
        val parameters = ComfyWorkflowEngine.suggest(graph).map { if (it.kind == ParameterKind.CFG) it.copy(value = "6.5") else it } +
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