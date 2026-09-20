package com.lo.imagine.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 智绘姬 (st-chatu8) / NovelAI 角色预设导入解析器。
 * 支持解密 Base64 混淆的智绘姬导出文件、标准导出包、以及直接角色 JSON。
 */
object Chatu8CharacterImporter {

    private val gson = Gson()

    private val b64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * 标准 Base64 解码（UTF-8 还原，容忍缺省 padding 与空白）；非法输入或还原出乱码时返回 null。
     * 纯 Kotlin 实现：不依赖 android.util，单元测试可直接覆盖加密导入路径。
     */
    private fun decodeBase64OrNull(input: String): String? {
        val cleaned = input.filterNot { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
        if (cleaned.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in cleaned) {
            if (ch == '=') break
            val v = b64Alphabet.indexOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        if (out.size() == 0) return null
        val text = String(out.toByteArray(), Charsets.UTF_8)
        // 解出替换符（U+FFFD）说明原串不是合法 UTF-8 的 Base64——判定失败，交回调用方保留原文
        return text.takeIf { '\uFFFD' !in it }
    }

    private fun decodeBase64Safe(input: String): String = decodeBase64OrNull(input) ?: input

    private fun optString(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val el = obj.get(k)
            if (el != null && !el.isJsonNull) {
                if (el.isJsonPrimitive) {
                    val str = el.asString.trim()
                    if (str.isNotBlank()) return str
                }
            }
        }
        return ""
    }

    /**
     * 解析顶层 _nameMap（Base64 包裹的 JSON）：加密导出把角色/服装原名替换成匿名 ID，
     * 原名映射表就存在这里。返回「匿名ID → 原名」的合并映射（characters + outfits）。
     */
    private fun parseNameMap(rootObj: JsonObject): Map<String, String> {
        val raw = rootObj.get("_nameMap")?.takeIf { it.isJsonPrimitive }?.asString ?: return emptyMap()
        val decoded = decodeBase64OrNull(raw) ?: return emptyMap()
        val mapObj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (section in listOf("characters", "outfits")) {
            val sec = mapObj.get(section)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            for ((id, name) in sec.entrySet()) {
                if (name.isJsonPrimitive && name.asString.isNotBlank()) result[id] = name.asString
            }
        }
        return result
    }

    /**
     * 解析顶层 outfits 对象（服装描述字典）：加密导出的服装真实内容在这里。
     * 返回「服装名 → 拼好的描述串」，供角色 outfit 字段引用时合并。
     */
    private fun parseOutfitDescriptions(rootObj: JsonObject, encrypted: Boolean): Map<String, String> {
        val outfitsObj = rootObj.get("outfits")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((outfitId, outfitVal) in outfitsObj.entrySet()) {
            if (!outfitVal.isJsonObject) continue
            val obj = decryptOutfitFields(outfitVal.asJsonObject, encrypted)
            val desc = listOf(
                optString(obj, "upperBody"), optString(obj, "upperBodyBack"),
                optString(obj, "fullBody"), optString(obj, "fullBodyBack")
            ).filter { it.isNotBlank() }.joinToString(", ")
            if (desc.isNotBlank()) result[outfitId] = desc
        }
        return result
    }

    /** 服装预设加密清单（与插件 encryptOutfitPreset 一致：6 字段） */
    private val encryptedOutfitFields = setOf("nameCN", "nameEN", "upperBody", "upperBodyBack", "fullBody", "fullBodyBack")

    private fun decryptOutfitFields(obj: JsonObject, encrypted: Boolean): JsonObject {
        if (!encrypted) return obj
        val res = JsonObject()
        for ((key, value) in obj.entrySet()) {
            if (key in encryptedOutfitFields && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                res.addProperty(key, decodeBase64Safe(value.asString))
            } else {
                res.add(key, value)
            }
        }
        return res
    }

    /**
     * st-chatu8 加密清单（与插件 encryptCharacterPreset 完全一致）：
     * 只有这 12 个字段会被 Base64 混淆；characterTraits / prompt / negative 等保持明文。
     * 盲解会把形似 Base64 的明文（如 "solo"）解成乱码——必须按清单精确解密。
     */
    private val encryptedCharacterFields = setOf(
        "nameCN", "nameEN",
        "facialFeatures", "facialFeaturesBack",
        "upperBodySFW", "upperBodySFWBack",
        "fullBodySFW", "fullBodySFWBack",
        "upperBodyNSFW", "upperBodyNSFWBack",
        "fullBodyNSFW", "fullBodyNSFWBack"
    )

    /** 解密智绘姬可能加密的对象字段：仅解密清单内的字段，其余原样保留 */
    private fun decryptFields(obj: JsonObject, encrypted: Boolean): JsonObject {
        if (!encrypted) return obj
        val res = JsonObject()
        for ((key, value) in obj.entrySet()) {
            if (key in encryptedCharacterFields && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                res.addProperty(key, decodeBase64Safe(value.asString))
            } else {
                res.add(key, value)
            }
        }
        return res
    }

    private fun parseSingleCharacter(
        nameKey: String,
        rawObj: JsonObject,
        encrypted: Boolean,
        nameMap: Map<String, String> = emptyMap(),
        outfitDescs: Map<String, String> = emptyMap()
    ): NaiCharacterPrompt {
        val obj = decryptFields(rawObj, encrypted)

        val nameCN = optString(obj, "nameCN", "name", "char_nameCN")
        val nameEN = optString(obj, "nameEN", "nameEn", "char_nameEN")
        val finalName = when {
            nameCN.isNotBlank() -> nameCN
            nameEN.isNotBlank() -> nameEN
            nameKey.isNotBlank() -> nameKey
            else -> "导入角色"
        }

        val traits = optString(obj, "characterTraits", "traits", "char_characterTraits")
        val face = optString(obj, "facialFeatures", "face", "char_facialFeatures")
        val faceBack = optString(obj, "facialFeaturesBack", "faceBack", "char_facialFeaturesBack")
        
        val upperSfw = optString(obj, "upperBodySFW", "upperSfw", "char_upperBodySFW")
        val upperSfwBack = optString(obj, "upperBodySFWBack", "upperSfwBack", "char_upperBodySFWBack")
        val lowerSfw = optString(obj, "fullBodySFW", "lowerSfw", "lowerBodySFW", "char_fullBodySFW")
        val lowerSfwBack = optString(obj, "fullBodySFWBack", "lowerSfwBack", "lowerBodySFWBack", "char_fullBodySFWBack")

        val upperNsfw = optString(obj, "upperBodyNSFW", "upperNsfw", "char_upperBodyNSFW")
        val upperNsfwBack = optString(obj, "upperBodyNSFWBack", "upperNsfwBack", "char_upperBodyNSFWBack")
        val lowerNsfw = optString(obj, "fullBodyNSFW", "lowerNsfw", "lowerBodyNSFW", "char_fullBodyNSFW")
        val lowerNsfwBack = optString(obj, "fullBodyNSFWBack", "lowerNsfwBack", "lowerBodyNSFWBack", "char_fullBodyNSFWBack")

        val negative = optString(obj, "negative", "negativePrompt", "char_negative", "uc")
        val prompt = optString(obj, "prompt", "extraPrompt", "photoPrompt", "char_photo_prompt")

        // 服装解析：可能是数组，也可能是字符串
        var outfit = optString(obj, "outfit", "outfitsText", "char_outfit")
        if (outfit.isBlank() && obj.has("outfits") && obj.get("outfits").isJsonArray) {
            val arr = obj.getAsJsonArray("outfits")
            val outfitList = mutableListOf<String>()
            for (item in arr) {
                if (item.isJsonPrimitive) {
                    val rawStr = item.asString
                    val decoded = if (encrypted) decodeBase64Safe(rawStr) else rawStr
                    if (decoded.isNotBlank()) outfitList += decoded
                }
            }
            if (outfitList.isNotEmpty()) {
                // 加密导出：outfits 数组存匿名 ID（OUTFIT_001），先按 ID 查顶层 outfits 字典拿真实描述
                // （上半身/下半身标签——这才是能写进 caption 的内容）；查不到再退名字；都没有则丢弃。
                val descs = outfitList.map { raw -> outfitDescs[raw] ?: nameMap[raw] ?: raw }
                    .filter { n -> n.isNotBlank() && !Regex("^OUTFIT_\\d+$").matches(n) }
                if (descs.isNotEmpty()) outfit = descs.joinToString(", ")
            }
        }

        return NaiCharacterPrompt(
            name = finalName,
            nameEn = nameEN,
            traits = traits,
            face = face,
            faceBack = faceBack,
            upperSfw = upperSfw,
            upperSfwBack = upperSfwBack,
            lowerSfw = lowerSfw,
            lowerSfwBack = lowerSfwBack,
            upperNsfw = upperNsfw,
            upperNsfwBack = upperNsfwBack,
            lowerNsfw = lowerNsfw,
            lowerNsfwBack = lowerNsfwBack,
            outfit = outfit,
            prompt = prompt,
            negative = negative,
            enabled = true,
            viewAngle = "front",
            bodyMode = "sfw"
        )
    }

    /**
     * 解析任意智绘姬格式或通用角色预设的 JSON 字符串。
     * 返回解析出来的角色列表。
     */
    fun parseJson(jsonStr: String): List<NaiCharacterPrompt> {
        val root = runCatching { JsonParser.parseString(jsonStr.trim()) }.getOrNull() ?: return emptyList()
        if (!root.isJsonObject && !root.isJsonArray) return emptyList()

        if (root.isJsonArray) {
            val list = mutableListOf<NaiCharacterPrompt>()
            for (item in root.asJsonArray) {
                if (item.isJsonObject) {
                    list += parseSingleCharacter("", item.asJsonObject, false)
                }
            }
            return list
        }

        val rootObj = root.asJsonObject
        val isEncrypted = rootObj.get("_encrypted")?.asBoolean == true
        // 加密导出：匿名 ID → 原名（角色/服装）+ 服装描述字典，供角色解析时还原引用
        val nameMap = if (isEncrypted) parseNameMap(rootObj) else emptyMap()
        // 服装描述字典：加密/非加密导出都可能有（非加密时键是服装名，加密时键是匿名 ID）
        val outfitDescs = parseOutfitDescriptions(rootObj, isEncrypted)

        // 智绘姬标准格式：{ "characters": { "角色名": { ... } } }
        if (rootObj.has("characters") && rootObj.get("characters").isJsonObject) {
            val charsObj = rootObj.getAsJsonObject("characters")
            val list = mutableListOf<NaiCharacterPrompt>()
            for ((charKey, charVal) in charsObj.entrySet()) {
                if (charVal.isJsonObject) {
                    list += parseSingleCharacter(nameMap[charKey] ?: charKey, charVal.asJsonObject, isEncrypted, nameMap, outfitDescs)
                }
            }
            if (list.isNotEmpty()) return list
        }

        // 智绘姬设置全局 dump：{ "characterPresets": { "角色名": { ... } } }
        if (rootObj.has("characterPresets") && rootObj.get("characterPresets").isJsonObject) {
            val charsObj = rootObj.getAsJsonObject("characterPresets")
            val list = mutableListOf<NaiCharacterPrompt>()
            for ((charKey, charVal) in charsObj.entrySet()) {
                if (charVal.isJsonObject) {
                    list += parseSingleCharacter(nameMap[charKey] ?: charKey, charVal.asJsonObject, isEncrypted, nameMap, outfitDescs)
                }
            }
            if (list.isNotEmpty()) return list
        }

        // 如果根对象本身就是一个角色（具有 nameCN, characterTraits, 或 traits, face 等）
        val hasDirectCharFields = listOf(
            "nameCN", "nameEN", "characterTraits", "facialFeatures",
            "upperBodySFW", "fullBodySFW", "traits", "face", "outfit"
        ).any { rootObj.has(it) }

        if (hasDirectCharFields) {
            return listOf(parseSingleCharacter("", rootObj, isEncrypted, nameMap, outfitDescs))
        }

        // 可能是字典格式：{ "角色名A": { ... }, "角色名B": { ... } }
        val list = mutableListOf<NaiCharacterPrompt>()
        for ((k, v) in rootObj.entrySet()) {
            if (v.isJsonObject) {
                val sub = v.asJsonObject
                if (listOf("nameCN", "nameEN", "characterTraits", "facialFeatures", "traits", "face").any { sub.has(it) }) {
                    list += parseSingleCharacter(nameMap[k] ?: k, sub, isEncrypted, nameMap, outfitDescs)
                }
            }
        }
        return list
    }
}
