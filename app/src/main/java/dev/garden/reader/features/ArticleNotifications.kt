package dev.garden.reader.features

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.garden.reader.MainActivity
import dev.garden.reader.R
import dev.garden.reader.data.WebContentParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

object ArticleNotifications {
    private const val JOB = 14001
    private const val CHANNEL = "new_public_articles"
    private val checks = Mutex()
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialCheck: Job? = null

    fun enabled(context: Context) = NotificationHistory.get(context).state.value.enabled

    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permission = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Reconcile an enabled preference with system jobs after app restart or an app update. */
    fun restore(context: Context) {
        if (enabled(context)) setEnabled(context, true)
    }

    @Synchronized fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val store = NotificationHistory.get(app)
        val jobs = app.getSystemService(JobScheduler::class.java)
        if (!enabled) {
            store.enabled(false)
            initialCheck?.cancel(); initialCheck = null
            jobs.cancel(JOB)
            app.getSystemService(NotificationManager::class.java).cancel(JOB)
            return
        }
        createChannel(app)
        val result = jobs.schedule(JobInfo.Builder(JOB, ComponentName(app, ArticleNotificationJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(TimeUnit.MINUTES.toMillis(15)).build())
        check(result == JobScheduler.RESULT_SUCCESS) { "系统未接受后台任务，请稍后重试" }
        store.enabled(true)
        // Establish the baseline now, not at an unpredictable first periodic run hours later.
        if (initialCheck?.isActive != true) initialCheck = immediateScope.launch { runCheck(app, waitForExisting = true) }
    }

    /** Explicit manual check and scheduled checks share one mutex and one persistent baseline. */
    suspend fun checkNow(context: Context): NotificationCheckResult = runCheck(context, waitForExisting = false)

    private suspend fun runCheck(context: Context, waitForExisting: Boolean): NotificationCheckResult = withContext(Dispatchers.IO) {
        val store = NotificationHistory.get(context)
        if (waitForExisting) checks.lock()
        else if (!checks.tryLock()) return@withContext NotificationCheckResult(0, false, "正在检查，请稍候")
        var revision: Long? = null
        try {
            revision = store.beginCheck()
                ?: return@withContext NotificationCheckResult(0, false, "请先开启新文章检查")
            val (status, html) = PublicHttp.get("https://www.cnblogs.com/", "www.cnblogs.com")
            check(status == 200) { "博客园首页返回 HTTP $status，未更新通知记录" }
            val articles = WebContentParser.parse(html, "https://www.cnblogs.com/").articles
            check(articles.isNotEmpty()) { "未能解析首页文章，已保留原有记录，请稍后重试" }
            currentCoroutineContext().ensureActive()
            val delta = store.accept(revision, articles, System.currentTimeMillis())
                ?: return@withContext NotificationCheckResult(0, false, "检查已停止")
            if (enabled(context)) {
                try { deliverPending(context, store) }
                catch (_: SecurityException) {
                    store.failed(revision, "系统通知权限已关闭；新文章记录已保留在站内通知列表")
                }
            }
            NotificationCheckResult(delta.fresh.size, delta.baseline,
                if (delta.baseline) "已记录当前文章基线，之后发现的新文章会进入通知列表"
                else if (delta.fresh.isEmpty()) "检查完成，暂无新文章" else "发现 ${delta.fresh.size} 篇新文章")
        } catch (cancelled: CancellationException) {
            revision?.let(store::cancelled)
            throw cancelled
        } catch (error: Exception) {
            // Fixed client messages/status codes only; never persist a raw server response.
            val message = if (error is IllegalStateException) error.message.orEmpty().take(160)
                else "网络检查失败，请确认网络后重试（${error.javaClass.simpleName}）"
            revision?.let { store.failed(it, message) }
            NotificationCheckResult(0, false, message, failed = true)
        } finally { checks.unlock() }
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "博客园新文章", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "定时检查最新公开文章；不是即时服务器推送。站内记录不依赖通知权限。"
            })
    }

    @Synchronized private fun deliverPending(context: Context, store: NotificationHistory) {
        // Even if permission is revoked, new records stay unread in the app inbox.
        if (!enabled(context) || !allowed(context)) return
        val pending = store.state.value.records.filter { !it.read && !it.notified }
        if (pending.isEmpty()) return
        createChannel(context)
        val article = pending.first().article
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("article_url", article.url); putExtra("article_title", article.title)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, JOB, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (pending.size == 1) "博客园有新文章" else "博客园新增 ${pending.size} 篇文章")
            .setContentText(article.title).setStyle(Notification.BigTextStyle().bigText(
                pending.take(5).joinToString("\n") { it.article.title }))
            .setContentIntent(contentIntent).setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(JOB, notification)
        store.markNotified(pending.map { it.article.url }.toSet())
    }
}

data class NotificationCheckResult(val newCount: Int, val baseline: Boolean, val message: String, val failed: Boolean = false)

class ArticleNotificationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job?.cancel()
        job = scope.launch {
            val result = ArticleNotifications.checkNow(this@ArticleNotificationJob)
            if (isActive) withContext(Dispatchers.Main) { jobFinished(params, result.failed) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); job = null; return ArticleNotifications.enabled(this) }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
