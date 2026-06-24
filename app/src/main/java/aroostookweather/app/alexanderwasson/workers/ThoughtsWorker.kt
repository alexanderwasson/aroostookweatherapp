package aroostookweather.app.alexanderwasson.workers

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ThoughtsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ts = fetchThoughtsTimestamp()
            if (ts != null) {
                val prefs = applicationContext.getSharedPreferences("aroostook_prefs", Context.MODE_PRIVATE)
                val lastTs = prefs.getString("lastThoughtsTs", null)

                if (lastTs != null && lastTs != ts) {
                    prefs.edit().putString("lastThoughtsTs", ts).apply()
                    showThoughtsNotification()
                } else if (lastTs == null) {
                    prefs.edit().putString("lastThoughtsTs", ts).apply()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchThoughtsTimestamp(): String? {
        val url = URL("https://aroostookweather.xyz/thoughts.html")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        return try {
            val text = conn.inputStream.bufferedReader().readText()
            val regex = Regex("""data-thoughts-updated="([^"]+)"""")
            regex.find(text)?.groupValues?.getOrNull(1)
        } finally {
            conn.disconnect()
        }
    }

    private fun showThoughtsNotification() {
        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                putExtra("open_page", "thoughts.html")
            }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.THOUGHTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Storm Thoughts")
            .setContentText("A new write-up has been posted on Storm Thoughts.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(applicationContext).notify(
            System.currentTimeMillis().toInt(),
            notification.build()
        )
    }
}