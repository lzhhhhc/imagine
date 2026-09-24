package com.lo.imagine.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DirectorEngineeringTest {
    private fun turn(index: Int, complete: Boolean = true) = DirectorStepTurn(
        DIRECTOR_STAGES[index].key, "明确这个镜头的变化", "具体要点 $index", complete,
        if (complete) "" else "这个动作怎样结束？",
        if (index < 4) "${DIRECTOR_STAGES[index + 1].label}有什么要求？" else ""
    )
    private fun review(): DirectorInterviewState {
        var state = DirectorInterviewState()
        repeat(5) { state = state.receive(turn(it)).confirm() }
        return state
    }

    @Test fun `receiving an answer never advances until explicitly confirmed`() {
        val initial = DirectorInterviewState()
        val ready = initial.receive(turn(0))
        assertEquals(0, ready.stageIndex)
        assertEquals(0, ready.confirmedCount)
        assertTrue(ready.stageReady)
        assertFalse(ready.canGenerate)
        assertEquals(1, ready.confirm().stageIndex)
    }
    @Test fun `unanswered and incomplete stages cannot be confirmed`() {
        assertThrows(IllegalArgumentException::class.java) { DirectorInterviewState().confirm() }
        val incomplete = DirectorInterviewState().receive(turn(0, complete = false))
        assertFalse(incomplete.stageReady)
        assertThrows(IllegalArgumentException::class.java) { incomplete.confirm() }
    }
    @Test fun `cannot jump over unconfirmed stages`() {
        assertThrows(IllegalArgumentException::class.java) { DirectorInterviewState().revisit(2) }
        assertThrows(IllegalArgumentException::class.java) { DirectorInterviewState().receive(turn(4)) }
    }
    @Test fun `all five confirmations are required for generation`() {
        var state = DirectorInterviewState()
        repeat(5) {
            state = state.receive(turn(it))
            assertFalse(state.canGenerate)
            state = state.confirm()
        }
        assertTrue(state.isReview)
        assertTrue(state.canGenerate)
    }
    @Test fun `editing earlier answers preserves content but invalidates dependent confirmations`() {
        val complete = review()
        val edited = complete.revisit(1)
        assertEquals(1, edited.stageIndex)
        assertEquals(1, edited.confirmedCount)
        assertEquals(complete.summaries, edited.summaries)
        assertFalse(edited.canGenerate)
        assertFalse(edited.stageReady)
        assertEquals(2, edited.receive(turn(1)).confirm().stageIndex)
    }
    @Test fun `a malformed response cannot mark the interview complete`() {
        val state = DirectorInterviewState()
        val malformed = listOf("", "{}", "{\"covered\":[\"story\",\"subject\",\"scene\",\"camera\",\"delivery\"],\"ready\":true}",
            Gson().toJson(turn(0)).replace("\"complete\":true", "\"complete\":\"true\""),
            Gson().toJson(turn(0)).replace("\"summary\":\"具体要点 0\"", "\"summary\":\"\""))
        malformed.forEach { raw ->
            assertTrue(runCatching { state.receive(parseDirectorStepTurn(raw, "story")) }.isFailure)
        }
        assertEquals(DirectorInterviewState(), state)
    }
    @Test fun `wrong stage and empty followup are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { parseDirectorStepTurn(Gson().toJson(turn(1)), "story") }
        assertThrows(IllegalArgumentException::class.java) { parseDirectorStepTurn(Gson().toJson(turn(0).copy(nextAsk = "")), "story") }
        assertThrows(IllegalArgumentException::class.java) { parseDirectorStepTurn(Gson().toJson(turn(0, false).copy(ask = "")), "story") }
    }
    @Test fun `accepted JSON keeps multiline summaries and confirmed parameters`() {
        val value = turn(4).copy(summary = "总时长8秒\n竖屏，静音", durationSec = "8", videoAspect = "9:16")
        assertEquals(value, parseDirectorStepTurn("```json\n${Gson().toJson(value)}\n```", "delivery"))
        assertThrows(IllegalArgumentException::class.java) {
            parseDirectorStepTurn(Gson().toJson(value.copy(durationSec = "0")), "delivery")
        }
    }
    @Test fun `final stage cannot complete without explicit duration and aspect`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDirectorStepTurn(Gson().toJson(turn(4)), "delivery")
        }
    }
    @Test fun `engine prompts and first questions resolve independently`() {
        assertEquals(4, DirectorEngine.entries.map { directorStoryboardSystem(it) }.toSet().size)
        assertEquals(4, DirectorEngine.entries.map { directorInterviewSystem(it, 1) }.toSet().size)
        assertEquals(4, DirectorEngine.entries.map { it.question(1) }.toSet().size)
        DirectorEngine.entries.forEach { engine ->
            assertTrue(directorStoryboardSystem(engine).contains(engine.direction))
            assertTrue(directorStoryboardSystem(engine).contains(engine.copyFormat))
            repeat(5) { assertTrue(directorInterviewSystem(engine, it).contains("当前阶段：${it + 1}/5")) }
        }
        assertFalse(directorStoryboardSystem(DirectorEngine.GROK).contains("Seedance"))
        assertFalse(directorStoryboardSystem(DirectorEngine.KLING).contains("Seedance"))
    }
    @Test fun `saveable workspaces survive serialization without losing confirmations`() {
        val original = review().revisit(3)
        val restored = Gson().fromJson(Gson().toJson(original), DirectorInterviewState::class.java)
        assertEquals(original, restored)
        assertFalse(restored.canGenerate)
        assertEquals(3, restored.confirmedCount)
    }
    @Test fun `sse chunks expose only visible director text while JSON is incomplete`() {
        assertEquals("你说的画面已经很清楚", directorStreamingMessage(
            "{\"stage\":\"story\",\"reply\":\"你说的画面已经很清楚"
        ))
        assertEquals("收到。\n\n镜头要固定还是跟拍？", directorStreamingMessage(
            "{\"reply\":\"收到。\",\"summary\":\"猫奔跑\",\"complete\":false,\"ask\":\"镜头要固定还是跟拍？"
        ))
        assertEquals("{\"reply\":\"收到。\"}", directorSseDelta(
            "{\"choices\":[{\"delta\":{\"content\":\"{\\\"reply\\\":\\\"收到。\\\"}\"}}]}"
        ))
        assertEquals("下一段", directorSseDelta(
            "{\"choices\":[{\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"下一段\"}]}}]}"
        ))
    }
    @Test fun `a repeated followup appears only once in the conversation`() {
        val ask = "你想把这个画面放在哪里？"
        val reply = "主角的形象已经清楚了。\n\n$ask"
        val message = directorTurnMessage(turn(2, false).copy(reply = reply, ask = ask))
        assertEquals(reply, message)
        assertEquals("主角的形象已经清楚了。\n\n$ask",
            directorTurnMessage(turn(2, false).copy(reply = "主角的形象已经清楚了。", ask = ask)))
    }
    @Test fun `completed stages do not display another followup`() {
        val ready = turn(0).copy(reply = "这个画面可以，我把要点整理好了。", ask = "不该再次追问")
        assertEquals(ready.reply, directorTurnMessage(ready))
    }
    @Test fun `review state reads current stage safely instead of indexing past the end`() {
        val complete = review()
        assertEquals(DIRECTOR_STAGES.size, complete.stageIndex)
        assertTrue(complete.isReview)
        assertNull(complete.currentStage)
        assertEquals("", complete.currentSummary)
        assertNull(complete.nextStageLabel)
        // 崩溃回归：旧实现在复核态仍用 DIRECTOR_STAGES[stageIndex] 取阶段名，length=5 index=5 抛越界
        runCatching { complete.currentStage?.label }.getOrThrow()
        runCatching { complete.currentSummary }.getOrThrow()
        // 序列化往返后（rememberSaveable 恢复）同样不得越界
        val restored = Gson().fromJson(Gson().toJson(complete), DirectorInterviewState::class.java)
        assertNull(restored.currentStage)
        assertTrue(restored.canGenerate)
        // 回退修改后重新可用
        val edited = complete.revisit(3)
        assertEquals("镜头", edited.currentStage?.label)
        assertEquals("具体要点 3", edited.currentSummary)
        assertEquals("成片", edited.nextStageLabel)
        val last = DirectorInterviewState(stageIndex = 4, confirmedCount = 4,
            summaries = List(5) { "要点$it" })
        assertEquals("成片", last.currentStage?.label)
        assertNull(last.nextStageLabel)
        assertEquals("要点4", last.currentSummary)
    }
}