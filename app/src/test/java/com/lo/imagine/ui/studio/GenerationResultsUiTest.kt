package com.lo.imagine.ui.studio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenerationResultsUiTest {
    @Test
    fun comfyWorkspaceCollapsesNodePanelsAndKeepsOnlyLatestPreview() {
        val source = File("src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkspaceScreen.kt").readText()
        // 节点面板默认收起，点标题展开/收起
        assertTrue(source.contains("expandedPanelIds"))
        assertTrue(source.contains("PopChevronDown"))
        assertTrue(source.contains("PopChevronUp"))
        assertTrue(source.contains("if (expanded) {"))
        // 不再渲染历史任务卡，只保留最新结果预览
        assertTrue(!source.contains("showJobHistory"))
        assertTrue(source.contains("state.jobs.firstOrNull()?.saved"))
        assertTrue(source.contains("LazyRow("))
        assertTrue(source.contains("WORKFLOW PREVIEW"))
    }

    @Test
    fun workflowListIsCardTappedToSelectWithIconActions() {
        val source = File("src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkflowEditor.kt").readText()
        // 点卡片即选择工作流，选择按钮已删除
        assertTrue(source.contains("runtime.repository.select(workflow.id)\n                    onDismiss()"))
        assertTrue(source.contains("PopIconButton(PopEdit, \"编辑工作流\""))
        assertTrue(source.contains("PopIconButton(PopDownload, \"导出工作流\""))
        assertTrue(source.contains("PopIconButton(PopTrash, \"删除工作流\""))
        assertTrue(!source.contains("\"已选择\" else \"选择\""))
    }

    @Test
    fun disabledNodesAreVisibleAndToggleableEverywhere() {
        val editor = File("src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkflowEditor.kt").readText()
        val workspace = File("src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkspaceScreen.kt").readText()
        assertTrue(editor.contains("ComfyWorkflowEngine.setNodeMode(workflow.graph, panel.nodeId, mode)"))
        assertTrue(editor.contains("MODE_BYPASS"))
        assertTrue(workspace.contains("ComfyWorkflowEngine.nodeEnabled(workflow.graph, nodeId)"))
        assertTrue(workspace.contains("该节点已停用"))
    }

    @Test
    fun naiResultCardsCarryAndRenderTheirCreationTime() {
        val source = File("src/main/java/com/lo/imagine/ui/studio/NaiWorkspaceScreen.kt").readText()
        assertTrue(source.contains("val createdAt: Long"))
        assertTrue(source.contains("footerText = \"NAI  //  \${ImageUtils.formatTimestamp(shot.createdAt)}\""))
    }
}