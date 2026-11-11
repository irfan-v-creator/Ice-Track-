package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

object NotificationService {

  private const val CHANNEL_ID_ALERTS = "icetrack_business_alerts"
  private const val CHANNEL_NAME_ALERTS = "IceTrack Business Alerts"

  fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID_ALERTS,
        CHANNEL_NAME_ALERTS,
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Alerts for payment due dates, low stock levels, and expiring ice cream batches"
      }
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  fun showNotification(
    context: Context,
    notificationId: Int,
    title: String,
    message: String
  ) {
    createNotificationChannels(context)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)

    try {
      manager.notify(notificationId, builder.build())
    } catch (_: SecurityException) {
      // Gracefully handle if POST_NOTIFICATIONS runtime permission has not been granted
    }
  }
}
