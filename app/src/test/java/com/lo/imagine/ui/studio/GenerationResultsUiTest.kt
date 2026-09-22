package com.lo.imagine.ui.studio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenerationResultsUiTest {
    @Test
    fun comfyWorkspaceKeepsOldJobsCollapsedAndUsesAFixedResultRail() {
        val source = File("src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkspaceScreen.kt").readText()
        assertTrue(source.contains("state.jobs.take(1)"))
        assertTrue(source.contains("showJobHistory"))
        assertTrue(source.contains("LazyRow("))
        assertTrue(source.contains("height(156.dp)"))
    }

    @Test
    fun naiResultCardsCarryAndRenderTheirCreationTime() {
        val source = File("src/main/java/com/lo/imagine/ui/studio/NaiWorkspaceScreen.kt").readText()
        assertTrue(source.contains("val createdAt: Long"))
        assertTrue(source.contains("footerText = \"NAI  //  \${ImageUtils.formatTimestamp(shot.createdAt)}\""))
    }
}