package com.lo.imagine.data.comfy

import com.google.gson.*
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

object ComfyWorkflowEngine {
    const val MAX_BYTES = 5 * 1024 * 1024
    private val random = SecureRandom()

    fun parse(text: String): JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "工作流不能超过 5 MiB" }
        // Bound nesting before calling the recursive JSON parser.
        var depth = 0; var quoted = false; var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "JSON 嵌套过深" } }
                '}', ']' -> depth--
            }
        }
        val root = try { GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonElement::class.java) } catch (e: JsonParseException) {
            throw IllegalArgumentException("JSON 格式错误：${e.message?.take(160)}")
        }
        require(root != null && root.isJsonObject) { "请导入 API 格式的节点字典" }
        val graph = root.asJsonObject
        require(!(graph.get("nodes")?.isJsonArray == true && graph.has("links"))) {
            "这是画布工作流。请在 ComfyUI 中打开后，选择 File → Export Workflow (API) 重新导出"
        }
        require(graph.size() in 1..5000) { "工作流需要包含 1–5000 个节点" }
        graph.entrySet().forEach { (id, value) ->
            require(value.isJsonObject) { "节点 $id 无效，请导出 API 格式" }
            val node = value.asJsonObject
            require(node.get("class_type")?.isJsonPrimitive == true && node.get("class_type").asJsonPrimitive.isString && node.get("class_type").asString.isNotBlank()) { "节点 $id 缺少 class_type" }
            require(node.get("inputs")?.isJsonObject == true) { "节点 $id 缺少 inputs" }
            node.getAsJsonObject("inputs").entrySet().forEach { (key, input) ->
                if (input.isJsonArray && input.asJsonArray.size() == 2) {
                    val link = input.asJsonArray
                    if (link[0].isJsonPrimitive && link[0].asJsonPrimitive.isString && link[1].isJsonPrimitive && link[1].asJsonPrimitive.isNumber) {
                        require(graph.has(link[0].asString)) { "$id.$key 引用了不存在的节点 ${link[0].asString}" }
                    }
                }
            }
        }
        return graph
    }

    fun title(graph: JsonObject, id: String): String {
        val node = graph.getAsJsonObject(id)
        return node.get("_meta")?.takeIf { it.isJsonObject }?.asJsonObject?.get("title")
            ?.takeIf { it.isJsonPrimitive }?.asString ?: node.get("class_type").asString
    }

    fun scalars(graph: JsonObject): List<ScalarInput> = graph.entrySet().flatMap { (id, element) ->
        element.asJsonObject.getAsJsonObject("inputs").entrySet().mapNotNull { (key, value) ->
            if (!value.isJsonPrimitive) null else ScalarInput(InputTarget(id, key), "$id · ${title(graph, id)}", value.asString,
                when { value.asJsonPrimitive.isBoolean -> "布尔"; value.asJsonPrimitive.isNumber -> "数值"; else -> "文本" })
        }
    }

    /** Suggestions only: no traversal through unknown conditioning transforms, no first-node guessing. */
    fun suggest(graph: JsonObject): List<WorkflowParameter> {
        val params = mutableListOf<WorkflowParameter>()
        val samplers = graph.entrySet().filter { it.value.asJsonObject.get("class_type").asString in setOf("KSampler", "KSamplerAdvanced") }
        fun add(kind: ParameterKind, id: String, input: String) {
            val value = graph.getAsJsonObject(id).getAsJsonObject("inputs").get(input) ?: return
            if (!value.isJsonPrimitive || params.any { InputTarget(id, input) in it.targets }) return
            params += WorkflowParameter(label = kind.label, kind = kind, targets = listOf(InputTarget(id, input)), value = value.asString)
        }
        if (samplers.size == 1) {
            val (id, node) = samplers.single()
            val inputs = node.asJsonObject.getAsJsonObject("inputs")
            add(ParameterKind.SEED, id, if (inputs.has("seed")) "seed" else "noise_seed")
            add(ParameterKind.STEPS, id, "steps"); add(ParameterKind.CFG, id, "cfg")
            for ((linkName, kind) in listOf("positive" to ParameterKind.PROMPT, "negative" to ParameterKind.NEGATIVE)) {
                val link = inputs.get(linkName)
                if (link?.isJsonArray == true && link.asJsonArray.size() == 2) {
                    val target = link.asJsonArray[0].asString
                    if (graph.getAsJsonObject(target)?.get("class_type")?.asString == "CLIPTextEncode") add(kind, target, "text")
                }
            }
        }
        val latents = graph.entrySet().filter { it.value.asJsonObject.get("class_type").asString in setOf("EmptyLatentImage", "EmptySD3LatentImage", "EmptyFlux2LatentImage") }
        if (latents.size == 1) {
            val id = latents.single().key
            add(ParameterKind.WIDTH, id, "width"); add(ParameterKind.HEIGHT, id, "height"); add(ParameterKind.BATCH, id, "batch_size")
        }
        // Reference-image binding is intentionally manual: never guess which LoadImage node the user means.
        return params.sortedBy { it.kind.ordinal }
    }

    fun suggestedOutputs(graph: JsonObject): List<String> = graph.entrySet()
        .filter { it.value.asJsonObject.get("class_type").asString == "SaveImage" }.map { it.key }
        .let { if (it.size == 1) it else emptyList() }

    fun prepare(workflow: ComfyWorkflow, resolveRandom: Boolean = true): PreparedWorkflow {
        require(workflow.name.isNotBlank()) { "请填写工作流名称" }
        val graph = parse(workflow.graph.toString()).deepCopy()
        require(workflow.outputNodes.isNotEmpty() && workflow.outputNodes.all { graph.has(it) }) { "请选择有效的图片输出节点" }
        val used = mutableSetOf<InputTarget>()
        val values = linkedMapOf<String, String>()
        for (parameter in workflow.parameters) {
            require(parameter.label.isNotBlank() && parameter.targets.isNotEmpty()) { "参数需要名称和绑定目标" }
            require(parameter.kind == ParameterKind.SEED || !parameter.randomSeed) { "只有种子可启用随机值" }
            val raw = if (parameter.randomSeed && resolveRandom) BigInteger(48, random).toString() else if (parameter.randomSeed) "0" else parameter.value
            var targetType: String? = null
            parameter.targets.forEach { target ->
                require(used.add(target)) { "${target.key} 被多个参数重复绑定" }
                val inputs = graph.getAsJsonObject(target.nodeId)?.getAsJsonObject("inputs")
                    ?: throw IllegalArgumentException("绑定节点 ${target.nodeId} 已失效")
                val old = inputs.get(target.input)
                require(old?.isJsonPrimitive == true) { "${target.key} 不是可编辑的标量，不能覆盖节点连线" }
                val p = old!!.asJsonPrimitive
                val type = when { p.isNumber -> "number"; p.isBoolean -> "boolean"; else -> "string" }
                require(targetType == null || targetType == type) { "一个参数不能绑定不同类型的输入" }
                targetType = type
                if (parameter.kind in setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.CFG, ParameterKind.BATCH)) {
                    require(p.isNumber) { "${parameter.label} 必须绑定数值输入" }
                }
                if (parameter.kind == ParameterKind.IMAGE) {
                    require(graph.getAsJsonObject(target.nodeId).get("class_type").asString == "LoadImage" && target.input == "image") {
                        "参考图片只能绑定 LoadImage.image"
                    }
                    require(p.isString) { "参考图片必须绑定文本文件名输入" }
                    if (resolveRandom) require(raw.isNotBlank()) { "请选择并上传参考图片" }
                }
                val next = when {
                    p.isBoolean -> { require(raw in listOf("true", "false")) { "${parameter.label} 需要 true 或 false" }; JsonPrimitive(raw.toBoolean()) }
                    p.isNumber -> {
                        val numeric = raw.toBigDecimalOrNull() ?: throw IllegalArgumentException("${parameter.label} 需要有效数字")
                        if (parameter.kind in setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.BATCH)) {
                            require(numeric.stripTrailingZeros().scale() <= 0) { "${parameter.label} 必须为整数" }
                        } // JSON number spelling does not determine node INT/FLOAT; schema validates custom inputs.
                        when (parameter.kind) {
                            ParameterKind.SEED -> require(numeric >= BigDecimal.ZERO && numeric <= BigDecimal("18446744073709551615")) { "种子超出无符号 64 位范围" }
                            ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.STEPS, ParameterKind.BATCH -> require(numeric > BigDecimal.ZERO) { "${parameter.label} 必须大于 0" }
                            else -> Unit
                        }
                        JsonPrimitive(numeric)
                    }
                    else -> JsonPrimitive(raw)
                }
                inputs.add(target.input, next)
            }
            values["${parameter.label} [${parameter.targets.joinToString { it.key }}]"] = raw
        }
        return PreparedWorkflow(graph, values)
    }

    fun validateWithInfo(graph: JsonObject, info: Map<String, JsonObject>) {
        graph.entrySet().forEach { (id, element) ->
            val node = element.asJsonObject; val type = node.get("class_type").asString
            val schema = info[type] ?: throw IllegalArgumentException("服务器未安装节点：$type（$id）")
            val inputSchema = schema.getAsJsonObject("input") ?: return@forEach
            val definitions = JsonObject().apply {
                inputSchema.getAsJsonObject("required")?.entrySet()?.forEach { (k, v) -> add(k, v) }
                inputSchema.getAsJsonObject("optional")?.entrySet()?.forEach { (k, v) -> add(k, v) }
            }
            node.getAsJsonObject("inputs").entrySet().forEach field@{ (key, value) ->
                if (!value.isJsonPrimitive) return@field
                val definition = definitions.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return@field
                if (definition.size() == 0) return@field
                if (definition[0].isJsonArray) {
                    val options = definition[0].asJsonArray
                    require(options.any { it == value }) { "节点 $id 的 $key 不在服务器可选值中：${value.asString.take(100)}" }
                } else if (definition[0].isJsonPrimitive) {
                    when (definition[0].asString) {
                        "INT", "FLOAT" -> {
                            val n = value.asString.toBigDecimalOrNull() ?: throw IllegalArgumentException("$id.$key 需要数值")
                            if (definition[0].asString == "INT") require(n.stripTrailingZeros().scale() <= 0) { "$id.$key 必须为整数" }
                            val limits = definition.toList().getOrNull(1)?.takeIf { it.isJsonObject }?.asJsonObject
                            limits?.get("min")?.asString?.toBigDecimalOrNull()?.let { require(n >= it) { "$id.$key 小于最小值 $it" } }
                            limits?.get("max")?.asString?.toBigDecimalOrNull()?.let { require(n <= it) { "$id.$key 大于最大值 $it" } }
                        }
                    }
                }
            }
        }
    }
}
