package com.lo.imagine.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lo.imagine.data.AspectOption
import com.lo.imagine.data.ImageData
import com.lo.imagine.data.QUALITY_TIERS
import com.lo.imagine.data.editOutputPixels
import com.lo.imagine.data.matchAspect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全局状态容器。
 * 放在单例里而不是 remember，是为了让切换底部标签、进出预览页时
 * 不丢失创作上下文，正在进行的生成任务也不会被打断。
 */

object PreviewStore {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var prompt by mutableStateOf("")
    var model by mutableStateOf("")
    var workflowDetails by mutableStateOf<String?>(null)
    /** 生成耗时文本（如「用时 32s」），预览页在像素角标下方展示 */
    var elapsedText by mutableStateOf("")
    /** 上游模型实际输出的像素（如「1536x1024」）；与最终像素不同时预览页会显示 */
    var sizeNote by mutableStateOf<String?>(null)
    /** 作品库进入预览时携带的列表与当前位置；非空时预览页支持左右滑动切换 */
    var historyList by mutableStateOf<List<com.lo.imagine.util.HistoryEntry>?>(null)
    var historyIndex by mutableIntStateOf(0)
    /** 作品被删掉后递增，作品页据此刷新，不依赖重新进入页面 */
    var historyRevision by mutableIntStateOf(0)
}

object StudioState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var prompt by mutableStateOf("")
    var negative by mutableStateOf("")
    var styleId by mutableStateOf("none")
    var qualityId by mutableStateOf("high")
    var size by mutableStateOf("1024x1024")
    /** 创作页画幅选择的单一数据源：不从分辨率反推，避免 64 对齐后反推失败 */
    var aspectLabel by mutableStateOf("1:1")
    var count by mutableIntStateOf(1)
    var safeNegative by mutableStateOf(true)
    var advancedOpen by mutableStateOf(false)
    /** NAI 模式总开关：关闭时强制 profileId=off，走通用提示词路径 */
    var naiMode by mutableStateOf(false)
    var naiOptions by mutableStateOf(com.lo.imagine.data.NaiOptions())
    /**
     * 前置提示词：编辑即持久化（防抖），不依赖弹窗的保存按钮。
     * 放在全局状态而非弹窗内 remember，是因为弹窗关闭会取消其协程作用域，
     * 导致「退出弹窗后内容清空」。
     */
    /** 润色深度（轻/中/深） */
    var polishDepth by mutableStateOf(com.lo.imagine.data.PolishDepth.MEDIUM)
    /** 润色目标模型模板（自动=按当前生成模型匹配） */
    var polishTemplate by mutableStateOf(com.lo.imagine.data.PolishTemplate.AUTO)
    /** 创作参数是否已从磁盘恢复（进程生命周期内只恢复一次，避免覆盖正在调整的值） */
    var paramsRestored = false

    var loading by mutableStateOf(false)
    /** 画幅不符提醒只弹一次（App 生命周期内）：上游模型无视 size 请求属于模型端行为，不反复打扰 */
    var aspectWarnShown = false
    /** 生成任务激活标志：false 时等待中的请求不再发起（用于「取消生成」），在途请求正常跑完 */
    var genActive by mutableStateOf(false)
    var elapsed by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<ImageData>>(emptyList())
    var resultPrompt by mutableStateOf("")
    var resultModel by mutableStateOf("")

    /** 精确计时的起点时间戳（非 Compose 状态，仅用于计算 elapsed） */
    var startedAt = 0L
}

object EditState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** 参考图列表（全局存活：切页/查看结果后回来不丢，无需重新选图） */
    var refBitmaps by mutableStateOf<List<Bitmap>>(emptyList())
    /** 每张参考图各自的遮罩（索引对应 refBitmaps） */
    val refMasks = androidx.compose.runtime.mutableStateMapOf<Int, Bitmap>()

    var sourceBitmap by mutableStateOf<Bitmap?>(null)
    var sourceBytes by mutableStateOf<ByteArray?>(null)
    var sourceMime by mutableStateOf("image/png")
    var prompt by mutableStateOf("")
    var size by mutableStateOf(editOutputPixels("1:1", "high"))
    /** 修图画幅选择的单一数据源：不从输出分辨率反推，避免 64 对齐后反推失败、显示不更新 */
    var aspectLabel by mutableStateOf("1:1")
    /** 修图画质档（沿用创作页 QUALITY_TIERS，与长边对应） */
    var qualityId by mutableStateOf("high")
    /** 修图一次出几张（1/2/4） */
    var count by mutableIntStateOf(1)
    var round by mutableIntStateOf(0)

    /** 请求和界面共用这一处，避免画质显示是 1.5K、实际却发出旧的 1024。 */
    fun outputPixels(): String = editOutputPixels(aspectLabel, qualityId)

    /** 原图画幅（选图后自动识别） */
    var sourceAspect by mutableStateOf<String?>(null)
    var sourceSize by mutableStateOf<String?>(null)

    /** 手绘遮罩展示位图（≤ 1500px 预览尺寸，拖动中实时绘制）。
     *  同一实例可变更新（避免分配），发送时再升采样到原图尺寸编码。
     *  位图本身不是 Compose 状态（拖动中更新位图不应触发昂贵重组），
     *  用 [maskVersion] 作为递增信号驱动显示层重绘，抬笔后再写回状态。 */
    var maskBitmap by mutableStateOf<Bitmap?>(null)
    var maskVersion by mutableIntStateOf(0)

    var loading by mutableStateOf(false)
    var elapsed by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<ImageData>>(emptyList())

    /** 精确计时的起点时间戳（非 Compose 状态，仅用于计算 elapsed） */
    var startedAt = 0L

    /** 按当前画质档计算该画幅的输出尺寸（档位缺失时按「高画质」档，与修图页显示口径一致） */
    fun sizeFor(aspect: AspectOption): String =
        aspect.sizeFor((QUALITY_TIERS.firstOrNull { it.id == qualityId } ?: QUALITY_TIERS[1]).longEdge)

    /**
     * 注入修图源图。
     * [asSingleReference]=true 表示「这张源图就是唯一修图对象」：同步 refBitmaps 与遮罩状态。
     * 必须成对——修图页缩略图渲染与 runEdit 实际发送都以 refBitmaps 为准，
     * 只设 source* 会让外部入口（预览页「拿去修图」）进来后缩略图空白、点生成被判空静默 return。
     */
    fun setSource(bitmap: Bitmap, bytes: ByteArray, mime: String, asSingleReference: Boolean = false) {
        sourceBitmap = bitmap
        sourceBytes = bytes
        sourceMime = mime
        val matched = matchAspect(bitmap.width, bitmap.height)
        sourceAspect = matched.label
        sourceSize = matched.size
        maskBitmap = null
        results = emptyList()
        error = null
        if (asSingleReference) {
            refBitmaps = listOf(bitmap)
            refMasks.clear()
            maskVersion += 1
        }
    }

    fun reset() {
        sourceBitmap = null
        refBitmaps = emptyList()
        refMasks.clear()
        sourceBytes = null
        sourceAspect = null
        sourceSize = null
        maskBitmap = null
        results = emptyList()
        error = null
        round = 0
    }
}

/** 模型列表缓存，避免每次进设置都重新拉 */
object ModelCache {
    var models by mutableStateOf<List<String>>(emptyList())
    var fetchedFor by mutableStateOf<String?>(null)

    fun validFor(baseUrl: String): Boolean =
        models.isNotEmpty() && fetchedFor == baseUrl
}

/** 跨页面动作总线 */
object AppBus {
    /** 请求跳转到某个 tab */
    var requestRoute by mutableStateOf<String?>(null)

    fun goTo(route: String) {
        requestRoute = route
    }

    fun consumeRoute(): String? {
        val r = requestRoute
        requestRoute = null
        return r
    }
}

/**
 * 创作模式记忆：初次从 SettingsRepository 恢复，导航变化写入同一个键。
 */
object StudioModeState {
    var current by mutableStateOf(StudioMode.NORMAL)
    var ready by mutableStateOf(false)
}
