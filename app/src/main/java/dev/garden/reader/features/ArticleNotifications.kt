package dev.garden.reader.features

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import dev.garden.reader.MainActivity
import dev.garden.reader.R
import dev.garden.reader.data.WebContentParser
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object ArticleNotifications {
    private const val JOB = 14001
    private const val CHANNEL = "new_public_articles"
    fun enabled(context: Context) = context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun allowed(context: Context) = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE)
        // Reset baseline on every new opt-in, so old articles aren't announced as new.
        prefs.edit().putBoolean("enabled", enabled).remove("seen").apply()
        val jobs = context.getSystemService(JobScheduler::class.java)
        if (!enabled) { jobs.cancel(JOB); return }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "博客园新文章", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "定时检查博客园最新公开文章；受系统省电与网络条件影响，并非即时服务器推送。"
        })
        val result = jobs.schedule(JobInfo.Builder(JOB, ComponentName(context, ArticleNotificationJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(TimeUnit.MINUTES.toMillis(15)).build())
        if (result != JobScheduler.RESULT_SUCCESS) {
            prefs.edit().putBoolean("enabled", false).apply(); error("系统未接受后台任务，请稍后重试")
        }
    }
    internal fun check(context: Context) {
        if (!enabled(context) || !allowed(context)) return
        val prefs = context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE)
        val (status, html) = PublicHttp.get("https://www.cnblogs.com/", "www.cnblogs.com")
        check(status == 200) { "首页暂不可用" }
        val articles = WebContentParser.parse(html, "https://www.cnblogs.com/").articles
        check(articles.isNotEmpty())
        val previous = prefs.getStringSet("seen", null)?.toSet()
        if (!enabled(context)) return
        val all = (articles.map { it.url } + previous.orEmpty()).take(500).toSet()
        if (previous == null) { prefs.edit().putStringSet("seen", all).apply(); return }
        val fresh = articles.filter { it.url !in previous }
        if (fresh.isNotEmpty() && allowed(context)) {
            val article = fresh.first()
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("article_url", article.url); putExtra("article_title", article.title)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(context, JOB, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (fresh.size == 1) "博客园有新文章" else "博客园新增 ${fresh.size} 篇文章")
                .setContentText(article.title).setStyle(Notification.BigTextStyle().bigText(article.title))
                .setContentIntent(pending).setAutoCancel(true).build()
            context.getSystemService(NotificationManager::class.java).notify(JOB, notification)
        }
        prefs.edit().putStringSet("seen", all).apply()
    }
}

class ArticleNotificationJob : JobService() {
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var retry = false
            try { ArticleNotifications.check(this@ArticleNotificationJob) }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { retry = true }
            if (isActive) withContext(Dispatchers.Main) { jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); job = null; return ArticleNotifications.enabled(this) }
    override fun onDestroy() { job?.cancel(); super.onDestroy() }
}
