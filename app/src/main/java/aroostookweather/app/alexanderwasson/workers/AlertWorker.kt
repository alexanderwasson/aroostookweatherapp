package aroostookweather.app.alexanderwasson.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val alerts = fetchAlerts()
            val prefs = applicationContext.getSharedPreferences("aroostook_prefs", Context.MODE_PRIVATE)
            val lastCount = prefs.getInt("lastAlertCount", 0)

            if (alerts.isNotEmpty() && alerts.size != lastCount) {
                prefs.edit().putInt("lastAlertCount", alerts.size).apply()
                showAlertNotification(alerts)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchAlerts(): List<Alert> {
        val url = URL("https://api.weather.gov/alerts/active?zone=MEZ001,MEZ002,MEZ003,MEZ004")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "(Alexander Wasson, admin@alexanderwasson.dev)")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        return try {
            val text = conn.inputStream.bufferedReader().readText()
            parseAlerts(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseAlerts(json: String): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val featuresKey = "\"features\":"
        val featuresIdx = json.indexOf(featuresKey)
        if (featuresIdx == -1) return alerts

        val arrStart = json.indexOf('[', featuresIdx)
        if (arrStart == -1) return alerts

        var depth = 0
        var i = arrStart
        val objects = mutableListOf<String>()

        while (i < json.length) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) {
                        val close = findMatchingBrace(json, i)
                        if (close != -1) {
                            objects.add(json.substring(i, close + 1))
                            i = close
                        }
                    }
                    depth++
                }
                '}' -> depth--
                ']' -> break
            }
            i++
        }

        for (obj in objects) {
            val propsStart = obj.indexOf("\"properties\":")
            if (propsStart == -1) continue
            val propsEnd = findMatchingBrace(obj, obj.indexOf('{', propsStart))
            if (propsEnd == -1) continue
            val props = obj.substring(obj.indexOf('{', propsStart), propsEnd + 1)

            val event = extractString(props, "\"event\"")
            val severity = extractString(props, "\"severity\"")
            val headline = extractString(props, "\"headline\"")
            alerts.add(Alert(event, severity, headline))
        }

        return alerts
    }

    private fun findMatchingBrace(s: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun extractString(json: String, key: String): String {
        val idx = json.indexOf(key)
        if (idx == -1) return ""
        val colon = json.indexOf(':', idx)
        if (colon == -1) return ""
        val start = json.indexOf('"', colon + 1)
        if (start == -1) return ""
        val end = json.indexOf('"', start + 1)
        if (end == -1) return ""
        return json.substring(start + 1, end)
    }

    private fun showAlertNotification(alerts: List<Alert>) {
        val severe = alerts.filter {
            it.severity.lowercase() == "extreme" || it.severity.lowercase() == "severe"
        }
        val count = alerts.size
        val sevCount = severe.size
        val title = if (sevCount > 0) {
            "SEVERE: $sevCount alert${if (sevCount > 1) "s" else ""} for Aroostook County"
        } else {
            "$count weather alert${if (count > 1) "s" else ""} for Aroostook County"
        }
        val body = alerts.take(3).joinToString(", ") { it.event.ifEmpty { "Weather Alert" } } +
                if (alerts.size > 3) " +${alerts.size - 3} more" else ""

        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                putExtra("open_page", "alerts.html")
            }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (sevCount > 0) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (sevCount > 0) {
            notification.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        NotificationManagerCompat.from(applicationContext).notify(
            System.currentTimeMillis().toInt(),
            notification.build()
        )
    }

    data class Alert(val event: String, val severity: String, val headline: String)
}