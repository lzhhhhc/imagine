package com.lo.imagine.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lo.imagine.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val CHANNEL_TASK = "imagine_task_progress"
private const val CHANNEL_DONE = "imagine_task_result"
private const val ONGOING_ID = 100

/** 创建通知渠道（幂等） */
fun ensureNotifChannels(app: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = app.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_TASK, "生成任务进度", NotificationManager.IMPORTANCE_LOW).apply {
            description = "后台生成期间的常驻提示"
            setShowBadge(false)
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_DONE, "生成结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "任务完成或失败提醒"
            setShowBadge(true)
        }
    )
}

/**
 * 前台服务：只负责「保活 + 常驻通知」，让网络生成任务在用户离开 App 后继续运行。
 * 具体的生成 / 修图业务仍在仓库层执行，结果落库（作品库）不依赖界面存在。
 */
class GenerationService : Service() {

    companion object {
        /** 任务协程作用域：进程存活期间持续有效 */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun ensureAlive(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, GenerationService::class.java)
                )
            } catch (e: Exception) {
                // 极少数厂商限制下退化为纯后台协程，功能不受影响
                Log.w("GenerationTasks", "startForegroundService failed: ${e.message}")
            }
        }

        fun shutdownIfIdle(context: Context) {
            context.stopService(Intent(context, GenerationService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotifChannels(applicationContext)
        promoteToForeground(buildOngoing("正在后台生成，可随时离开…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildOngoing(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_TASK)
            .setSmallIcon(R.drawable.ic_notif_spark)
            .setContentTitle("Imagine 智绘")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ONGOING_ID, notification)
        }
    }
}

/** 系统通知工具：完成后投递一次性结果通知 */
fun notifyResult(app: Context, title: String, text: String, notifId: Int = Random.nextInt(5000, 9999), failed: Boolean = false) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    try {
        val launchIntent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return
        val pending = PendingIntent.getActivity(
            app, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_DONE)
            .setSmallIcon(com.lo.imagine.R.drawable.ic_notif_spark)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_PROGRESS)
            .setColor(if (failed) 0xFFD02020.toInt() else 0xFFFFB020.toInt())
            .build()
        NotificationManagerCompat.from(app).notify(notifId, notification)
    } catch (e: Exception) {
        Log.w("GenerationTasks", "notifyResult failed: ${e.message}")
    }
}

/** 任务结果契约：显式成败，通知标题不再靠文案关键词猜测 */
data class TaskOutcome(
    val summary: String,
    val success: Boolean
)

/**
 * 把一段耗时的生成 / 修图逻辑放入 [GenerationService.scope] 执行：
 * 1) 先确保前台服务拉起保活；
 * 2) 工作函数返回 [TaskOutcome]（显式声明成功/失败）；
 * 3) 无论成败，最终都会发出结果通知；
 * 4) 所有排队任务结束后自动停掉前台服务。
 */
object GenerationTasks {

    @Volatile
    private var activeCount = 0

    /** 结果通知固定 ID：同 ID 覆盖重发，避免多条完成/失败通知在通知栏堆叠混淆 */
    private const val DONE_NOTIF_ID = 4321

    fun launch(
        context: Context,
        doneTitle: String,
        failTitle: String = "$doneTitle（未完成）",
        work: suspend () -> TaskOutcome
    ) {
        val app = context.applicationContext
        ensureNotifChannels(app)
        GenerationService.ensureAlive(app)

        synchronized(this) { activeCount += 1 }

        GenerationService.scope.launch {
            var outcome = TaskOutcome("任务已中止", success = false)
            var crashed: Exception? = null
            try {
                outcome = work()
            } catch (e: Exception) {
                crashed = e
                Log.e("GenerationTasks", "task crashed", e)
                outcome = TaskOutcome("失败：${e.message ?: "未知错误"}", success = false)
            } finally {
                val remains = synchronized(this@GenerationTasks) {
                    activeCount -= 1
                    activeCount
                }
                // 成败由 work 显式声明；崩溃或未明确声明成功的一律按失败处理
                val failed = crashed != null || !outcome.success
                notifyResult(app, if (failed) failTitle else doneTitle, outcome.summary, DONE_NOTIF_ID, failed)
                if (remains <= 0) {
                    GenerationService.shutdownIfIdle(app)
                }
            }
        }
    }
}