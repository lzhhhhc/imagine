package com.lo.imagine.data.comfy

import com.google.gson.*
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

object ComfyWorkflowEngine {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val ANALYSIS_DIGEST_LIMIT = 24_000
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

    /** ComfyUI bypass/mute node state; disabled nodes are skipped by the server's executor. */
    const val MODE_ENABLED = 0
    const val MODE_BYPASS = 2
    const val MODE_MUTE = 4
    fun nodeMode(graph: JsonObject, id: String): Int =
        graph.getAsJsonObject(id)?.get("mode")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asInt ?: MODE_ENABLED
    fun nodeEnabled(graph: JsonObject, id: String): Boolean = nodeMode(graph, id) == MODE_ENABLED
    fun setNodeMode(graph: JsonObject, id: String, mode: Int): JsonObject = graph.deepCopy().apply {
        getAsJsonObject(id).addProperty("mode", mode)
    }
    fun enabledNodeCount(graph: JsonObject): Int = graph.entrySet().count { (_, node) ->
        val mode = node.asJsonObject.get("mode")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asInt ?: MODE_ENABLED
        mode == MODE_ENABLED
    }

    /** Only enabled node types participate in server schema checks and object_info requests. */
    fun enabledClassTypes(graph: JsonObject): List<String> = graph.entrySet()
        .filter { (id, _) -> nodeEnabled(graph, id) }
        .map { (_, element) -> element.asJsonObject.get("class_type").asString }
        .distinct()

    fun scalars(graph: JsonObject): List<ScalarInput> = nodePanels(graph).flatMap { it.fields }

    /** Nodes that still have literal inputs. Links are omitted: selecting a node means its whole panel. */
    fun nodePanels(graph: JsonObject): List<NodePanel> = graph.entrySet().mapNotNull { (id, element) ->
        val node = element.asJsonObject
        val fields = node.getAsJsonObject("inputs").entrySet().mapNotNull { (key, value) ->
            if (!value.isJsonPrimitive) null else ScalarInput(
                InputTarget(id, key), "$id · ${title(graph, id)}", value.asString,
                when { value.asJsonPrimitive.isBoolean -> "布尔"; value.asJsonPrimitive.isNumber -> "数值"; else -> "文本" }
            )
        }
        if (fields.isEmpty()) null else NodePanel(id, title(graph, id), node.get("class_type").asString, fields)
    }.sortedWith(compareBy({ it.nodeId.toIntOrNull() ?: Int.MAX_VALUE }, { it.nodeId }))

    /** Every literal input on one node, each kept as its own control so values are not forced together. */
    fun panelParameters(graph: JsonObject, nodeId: String): List<WorkflowParameter> {
        val panel = nodePanels(graph).find { it.nodeId == nodeId } ?: return emptyList()
        return panel.fields.map { field ->
            val kind = fieldKind(panel.classType, field.target.input)
            WorkflowParameter(
                label = if (kind == ParameterKind.CUSTOM) field.target.input else kind.label,
                kind = kind, targets = listOf(field.target), value = field.value
            )
        }
    }

    private fun fieldKind(classType: String, input: String): ParameterKind = when {
        classType == "LoadImage" && input == "image" -> ParameterKind.IMAGE
        input in setOf("seed", "noise_seed") -> ParameterKind.SEED
        input == "steps" -> ParameterKind.STEPS
        input == "cfg" -> ParameterKind.CFG
        input == "width" -> ParameterKind.WIDTH
        input == "height" -> ParameterKind.HEIGHT
        input in setOf("batch_size", "batch") -> ParameterKind.BATCH
        input == "sampler_name" -> ParameterKind.SAMPLER
        else -> ParameterKind.CUSTOM
    }

    /** Combo values for the parameter form. Returns null unless the definition is a plain primitive
     *  list, so exotic server entries (objects, nested values, null) fall back to a free-text field. */
    fun comboOptions(definition: JsonElement?): List<String>? {
        val array = definition?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        if (array.size() == 0 || array.any { !it.isJsonPrimitive }) return null
        return array.map { it.asString }
    }

    private fun linkNodeId(value: JsonElement?): String? {
        if (value?.isJsonArray != true) return null
        val link = value.asJsonArray
        if (link.size() != 2) return null
        val id = link[0]
        return id.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    /** The one CLIPTextEncode reached by conditioning links, or null when there isn't exactly one.
     *  Image, mask and latent branches are not followed, so a side prompt such as a detector stays unbound. */
    private fun conditioningText(graph: JsonObject, start: JsonElement?): String? {
        val found = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        linkNodeId(start)?.let(pending::add)
        var steps = 0
        while (pending.isNotEmpty()) {
            if (++steps > 64) return null
            val id = pending.removeFirst()
            if (!seen.add(id)) continue
            val node = graph.get(id)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val type = node.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
            val inputs = node.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            if (type == "CLIPTextEncode") {
                if (inputs.get("text")?.isJsonPrimitive == true) found += id
                continue
            }
            inputs.entrySet().forEach { (key, value) ->
                val name = key.lowercase()
                if (name == "positive" || name == "negative" || "conditioning" in name) linkNodeId(value)?.let(pending::add)
            }
        }
        return found.singleOrNull()
    }

    /** Walk latent_image backward and keep the size written closest to the sampler. */
    private fun sizeChoice(graph: JsonObject, start: JsonElement?): Pair<List<Pair<String, String>>, List<Pair<String, String>>>? {
        val pending = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        linkNodeId(start)?.let(pending::add)
        var steps = 0
        while (pending.isNotEmpty()) {
            if (++steps > 64) return null
            val id = pending.removeFirst()
            if (!seen.add(id)) continue
            val inputs = graph.get(id)?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            fun numeric(name: String) = inputs.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString
            val width = numeric("width")
            val height = numeric("height")
            if (width != null || height != null) {
                return listOfNotNull(width?.let { id to it }) to listOfNotNull(height?.let { id to it })
            }
            inputs.entrySet().mapNotNull { linkNodeId(it.value) }.forEach(pending::add)
        }
        return null
    }

    private val samplerTypes = setOf("KSampler", "KSamplerAdvanced", "SamplerCustom", "SamplerCustomAdvanced")

    /** Compact graph for the polish model. Sampler ancestors come first; long text and the tail are cut. */
    fun digest(graph: JsonObject): String {
        val order = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        graph.entrySet()
            .filter { it.value.asJsonObject.get("class_type").asString in samplerTypes }
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { queue.add(it.key) }
        val seen = mutableSetOf<String>()
        var steps = 0
        while (queue.isNotEmpty() && steps < 256) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            steps++
            order += id
            val inputs = graph.get(id)?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            inputs.entrySet().mapNotNull { linkNodeId(it.value) }.forEach(queue::add)
        }
        val preferred = setOf("CLIPTextEncode", "EmptyLatentImage", "EmptySD3LatentImage", "LoadImage")
        graph.entrySet()
            .filter { it.value.asJsonObject.get("class_type").asString in preferred }
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { order += it.key }
        graph.entrySet()
            .sortedWith(compareBy<Map.Entry<String, JsonElement>> { it.key.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.key })
            .forEach { order += it.key }
        val lines = mutableListOf<String>()
        var length = 0
        for (id in order) {
            val line = digestLine(graph, id)
            if (length + line.length + 1 > ANALYSIS_DIGEST_LIMIT) {
                lines += "…(其余节点已省略)"
                break
            }
            lines += line
            length += line.length + 1
        }
        return lines.joinToString("\n")
    }

    private fun digestLine(graph: JsonObject, id: String): String {
        val node = graph.getAsJsonObject(id)
        val type = node.get("class_type").asString
        val name = title(graph, id)
        val inputs = node.getAsJsonObject("inputs").entrySet().joinToString(" ") { (key, value) ->
            val linked = linkNodeId(value)
            when {
                linked != null -> {
                    val slot = value.asJsonArray[1].takeIf { it.isJsonPrimitive }?.asString ?: "?"
                    "$key→$linked:$slot"
                }
                value.isJsonPrimitive -> {
                    val text = value.asString.replace(Regex("\\s+"), " ")
                    val shown = if (text.length > 80) text.take(80) + "…" else text
                    "$key=$shown"
                }
                else -> "$key=…"
            }
        }
        val head = if (name != type) "$id $type「$name」" else "$id $type"
        return "$head $inputs".trim()
    }

    /** Node ids the model wants expanded. A node id means its whole literal panel, not one input.
     *  Older replies that only name bindings are accepted by taking the node of each valid target. */
    fun nodesFromAnalysis(graph: JsonObject, raw: String): List<String> {
        val body = extractJsonObject(raw) ?: throw IllegalArgumentException("模型没有返回可解析的 JSON")
        val root = try {
            GsonBuilder().setStrictness(Strictness.LENIENT).create().fromJson(body, JsonElement::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("模型返回的 JSON 无法解析")
        }
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("模型返回的不是 JSON 对象")
        if (!obj.has("nodes") && !obj.has("bindings")) throw IllegalArgumentException("模型返回里没有 nodes")
        val panels = nodePanels(graph).map { it.nodeId }.toSet()
        val found = linkedSetOf<String>()
        fun accept(id: String?) { if (id != null && id in panels && found.size < 24) found += id }
        obj.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            when {
                element.isJsonPrimitive -> accept(jsonId(element))
                element.isJsonObject -> {
                    val item = element.asJsonObject
                    accept(jsonId(item.get("id")) ?: jsonId(item.get("node")))
                }
            }
        }
        obj.get("bindings")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val targets = item.get("targets")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
            val valid = targets.mapNotNull { readTarget(it) }.filter { (node, input) ->
                graph.get(node)?.takeIf { it.isJsonObject }?.asJsonObject?.get("inputs")
                    ?.takeIf { it.isJsonObject }?.asJsonObject?.get(input)?.isJsonPrimitive == true
            }
            if (valid.size == targets.size()) valid.forEach { accept(it.first) }
        }
        return found.toList()
    }

    /** Turn the model reply into checkbox rows. Invalid rows are dropped; an unreadable reply throws. */
    fun bindingsFromAnalysis(graph: JsonObject, raw: String): List<BindingProposal> {
        val body = extractJsonObject(raw) ?: throw IllegalArgumentException("模型没有返回可解析的 JSON")
        val root = try {
            GsonBuilder().setStrictness(Strictness.LENIENT).create().fromJson(body, JsonElement::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("模型返回的 JSON 无法解析")
        }
        val bindings = root?.takeIf { it.isJsonObject }?.asJsonObject?.get("bindings")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException("模型返回里没有 bindings")
        val numeric = setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.BATCH)
        val proposals = mutableListOf<BindingProposal>()
        val kindsUsed = mutableSetOf<ParameterKind>()
        val used = mutableSetOf<InputTarget>()
        for (element in bindings) {
            if (proposals.size >= 24) break
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val kindName = item.get("kind")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: continue
            val kind = ParameterKind.entries.firstOrNull {
                it.name.equals(kindName.trim(), ignoreCase = true) || it.label == kindName.trim()
            } ?: continue
            if (kind != ParameterKind.CUSTOM && !kindsUsed.add(kind)) continue
            fun reject() {
                if (kind != ParameterKind.CUSTOM) kindsUsed.remove(kind)
            }
            val targetElements = item.get("targets")?.takeIf { it.isJsonArray }?.asJsonArray ?: run { reject(); continue }
            val targets = mutableListOf<InputTarget>()
            var value: String? = null
            var valueType: String? = null
            var ok = true
            for (targetElement in targetElements) {
                val pair = readTarget(targetElement)
                if (pair == null) { ok = false; break }
                val target = InputTarget(pair.first, pair.second)
                if (target in targets || target in used) { ok = false; break }
                val inputs = graph.get(target.nodeId)?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject
                val current = inputs?.get(target.input)
                if (current?.isJsonPrimitive != true) { ok = false; break }
                val primitive = current.asJsonPrimitive
                val type = when {
                    primitive.isNumber -> "number"
                    primitive.isBoolean -> "boolean"
                    else -> "string"
                }
                if (valueType != null && valueType != type) { ok = false; break }
                if (kind in numeric && type != "number") { ok = false; break }
                if (kind == ParameterKind.SAMPLER && type != "string") { ok = false; break }
                if (kind == ParameterKind.IMAGE) {
                    val nodeType = graph.getAsJsonObject(target.nodeId).get("class_type")?.asString
                    if (nodeType != "LoadImage" || target.input != "image" || type != "string") { ok = false; break }
                }
                valueType = type
                value = primitive.asString
                targets += target
            }
            if (!ok || targets.isEmpty() || value == null) { reject(); continue }
            used += targets
            val label = item.get("label")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lineSequence()?.firstOrNull()?.take(40)
                ?.takeIf { it.isNotBlank() } ?: kind.label
            val reason = item.get("reason")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lineSequence()?.firstOrNull()?.take(80).orEmpty()
            proposals += BindingProposal(label = label, kind = kind, targets = targets, value = value, reason = reason)
        }
        return proposals.sortedBy { it.kind.ordinal }
    }

    private fun readTarget(element: JsonElement): Pair<String, String>? {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val node = jsonId(obj.get("node")) ?: return null
            val input = obj.get("input")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
            return node to input
        }
        if (element.isJsonArray && element.asJsonArray.size() == 2) {
            val pair = element.asJsonArray
            val node = jsonId(pair[0]) ?: return null
            val input = pair[1].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
            return node to input
        }
        return null
    }

    private fun jsonId(element: JsonElement?): String? {
        val primitive = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        return when {
            primitive.isString -> primitive.asString
            primitive.isNumber && primitive.asString.none { it == '.' || it == 'e' || it == 'E' } -> primitive.asString
            else -> null
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    /** Rule suggestions kept for tests. Import does not call this, and analysis failure must not fall back to it. */
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
            for ((linkName, kind) in listOf("positive" to ParameterKind.PROMPT, "negative" to ParameterKind.NEGATIVE)) {
                conditioningText(graph, inputs.get(linkName))?.let { add(kind, it, "text") }
            }
        }
        // 多个采样器时不猜种子和负面词，但正面提示词只要整张图里唯一，就直接抓。
        if (params.none { it.kind == ParameterKind.PROMPT }) {
            val prompts = graph.entrySet().filter { (_, element) ->
                val node = element.asJsonObject
                node.get("class_type").asString == "CLIPTextEncode" &&
                    node.getAsJsonObject("inputs").get("text")?.isJsonPrimitive == true
            }
            if (prompts.size == 1) add(ParameterKind.PROMPT, prompts.single().key, "text")
        }
        // 尺寸不认死节点类型：从采样器的 latent_image 往回走，取真正决定画面的最后一组宽高。
        if (samplers.size == 1) {
            val latent = samplers.single().value.asJsonObject.getAsJsonObject("inputs").get("latent_image")
            sizeChoice(graph, latent)?.let { choice ->
                fun bind(kind: ParameterKind, input: String, targets: List<Pair<String, String>>) {
                    if (targets.isEmpty()) return
                    add(kind, targets.first().first, input)
                    if (targets.size > 1 && params.lastOrNull()?.kind == kind) {
                        params[params.lastIndex] = params.last().copy(targets = targets.map { InputTarget(it.first, input) })
                    }
                }
                bind(ParameterKind.WIDTH, "width", choice.first)
                bind(ParameterKind.HEIGHT, "height", choice.second)
            }
        }
        val loaders = graph.entrySet().filter { (id, element) ->
            element.asJsonObject.get("class_type").asString == "LoadImage" &&
                graph.getAsJsonObject(id).getAsJsonObject("inputs").get("image")?.isJsonPrimitive == true
        }
        if (loaders.size == 1) add(ParameterKind.IMAGE, loaders.single().key, "image")
        return params.sortedBy { it.kind.ordinal }
    }

    fun suggestedOutputs(graph: JsonObject): List<String> = graph.entrySet()
        .filter { it.value.asJsonObject.get("class_type").asString == "SaveImage" }.map { it.key }
        .let { if (it.size == 1) it else emptyList() }

    fun prepare(workflow: ComfyWorkflow, resolveRandom: Boolean = true): PreparedWorkflow {
        require(workflow.name.isNotBlank()) { "请填写工作流名称" }
        val graph = parse(workflow.graph.toString()).deepCopy()
        require(workflow.outputNodes.isNotEmpty() && workflow.outputNodes.all { graph.has(it) }) { "请选择有效的图片输出节点" }
        require(workflow.outputNodes.all { nodeEnabled(graph, it) }) { "图片输出节点处于停用（Bypass/Mute）状态，请先在编辑页启用它" }
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
            if (!nodeEnabled(graph, id)) return@forEach
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
                    // Membership is enforced only for plain primitive lists; exotic entries cannot be
                    // compared reliably (the form already falls back to free text), so the server decides.
                    if (options.all { it.isJsonPrimitive }) {
                        require(options.any { it == value }) { "节点 $id 的 $key 不在服务器可选值中：${value.asString.take(100)}" }
                    }
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
