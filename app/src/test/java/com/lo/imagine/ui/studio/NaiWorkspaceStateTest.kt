package com.lo.imagine.ui.studio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiWorkspaceStateTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun taskSetsAndClearsBusyItself() = runBlocking {
        val s = scope()
        NaiWorkspaceState.runTaskIn(s) {
            assertTrue("任务体内应看到 busy", NaiWorkspaceState.busy)
            NaiWorkspaceState.stage = "请求出图"
        }
        assertFalse(NaiWorkspaceState.busy)
        assertEquals("", NaiWorkspaceState.stage)
        s.cancel()
    }

    @Test fun failedTaskReportsErrorInsteadOfStuckSpinner() = runBlocking {
        val s = scope()
        NaiWorkspaceState.runTaskIn(s) { throw IllegalStateException("连接超时") }
        assertFalse(NaiWorkspaceState.busy)
        assertEquals("连接超时", NaiWorkspaceState.error)
        s.cancel()
    }

    @Test fun cancelledScopeNeverLeavesStuckBusy() = runBlocking {
        val s = scope()
        s.cancel()
        NaiWorkspaceState.runTaskIn(s) { error("不该被执行") }
        assertFalse("作用域已取消时不能把 busy 卡在 true", NaiWorkspaceState.busy)
    }

    @Test fun reconcileClearsStaleWaiting() = runBlocking {
        NaiWorkspaceState.busy = true
        NaiWorkspaceState.stage = "请求出图"
        NaiWorkspaceState.reconcile()
        assertFalse(NaiWorkspaceState.busy)
        assertEquals("", NaiWorkspaceState.stage)
    }
}