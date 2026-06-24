package aroostookweather.app.alexanderwasson.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val ALERT_CHANNEL_ID = "weather_alerts"
    const val THOUGHTS_CHANNEL_ID = "storm_thoughts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Weather Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "National Weather Service alerts for Aroostook County"
            }
            manager.createNotificationChannel(alertChannel)

            val thoughtsChannel = NotificationChannel(
                THOUGHTS_CHANNEL_ID,
                "Storm Thoughts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New storm thoughts write-ups"
            }
            manager.createNotificationChannel(thoughtsChannel)
        }
    }
}