package com.lo.imagine.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 并发任务调度器：根据设置的 maxParallel 控制同时跑的请求数。
 * - 1：稳，串行，最少出错，但慢
 * - 2：平衡
 * - 3 / 4：火力全开，但容易触发 API 限流
 */
object TaskScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 注意：kotlinx.coroutines.Semaphore 第二个参数是「初始已占用的许可数」，
    // 必须保持默认 0。此前误传 1 导致可用许可恒为 0，
    // 所有生成任务都会永远挂起在 withPermit（表现就是转圈不出图、无任何网络请求）。
    private var semaphore: Semaphore = Semaphore(1)
    private var permits: Int = 1

    /** 设置页切换时调用，立即生效。 */
    @Synchronized
    fun configure(maxParallel: Int) {
        val n = maxParallel.coerceIn(1, 4)
        if (n == permits) return
        // 重建为全新的 Semaphore（可用许可数 = 新的并发上限）
        semaphore = Semaphore(n)
        permits = n
    }

    /** 拿当前允许的并发数（UI 显示用） */
    fun permits(): Int = permits

    /**
     * 并发执行一组任务，每张完成就调用 onItem 立即回调。
     * 这样 UI 可以「一张张出来」实时刷新，而不是等全部完成。
     * isActive：取消开关——任务开始前检查，false 时该任务跳过（不发网络请求），
     * 结果以 CancellationException 包装返回，调用方可据此忽略。
     */
    suspend fun <T> parallelStream(
        tasks: List<suspend () -> T>,
        isActive: () -> Boolean = { true },
        onItem: suspend (index: Int, result: Result<T>) -> Unit
    ) {
        if (tasks.isEmpty()) return
        val deferreds = tasks.mapIndexed { idx, task ->
            scope.async {
                val r = if (!isActive()) {
                    Result.failure<T>(kotlinx.coroutines.CancellationException("已取消"))
                } else {
                    runCatching { semaphore.withPermit { task() } }
                }
                onItem(idx, r)
                r
            }
        }
        deferreds.awaitAll()
    }

    /** 全部完成后一次性返回结果列表 */
    suspend fun <T> parallel(tasks: List<suspend () -> T>): List<Result<T>> {
        if (tasks.isEmpty()) return emptyList()
        val out = arrayOfNulls<Result<T>>(tasks.size)
        parallelStream(tasks) { idx, r -> out[idx] = r }
        @Suppress("UNCHECKED_CAST")
        return out.toList() as List<Result<T>>
    }
}
