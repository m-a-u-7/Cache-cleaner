package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SysSweepApp : Application() {
  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
    }
    createNotificationChannel()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java) ?: return
      val channel =
        NotificationChannel(
          CLEANUP_CHANNEL_ID,
          getString(R.string.notif_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notif_channel_desc)
          setShowBadge(false)
        }
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CLEANUP_CHANNEL_ID = "syssweep_cleanup_channel"
  }
}
