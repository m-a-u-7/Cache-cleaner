package com.example.syssweep.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.SysSweepApp
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.data.SweepPreferencesRepository
import com.example.syssweep.engine.AppInventoryManager
import com.example.syssweep.engine.PrivilegeBridge
import com.example.syssweep.widget.updateAllSysSweepWidgets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CleanupForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val actionName = intent?.getStringExtra(EXTRA_ACTION_TYPE)
    val actionType =
      actionName?.let { runCatching { SweepActionType.valueOf(it) }.getOrNull() }
        ?: SweepActionType.BOOST_BOTH

    val notification = buildProgressNotification()
    val fgsType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      } else {
        0
      }
    ServiceCompat.startForeground(this, PROGRESS_NOTIF_ID, notification, fgsType)

    if (!isExecuting.compareAndSet(false, true)) {
      stopSelf(startId)
      return START_NOT_STICKY
    }

    serviceScope.launch {
      val repo = SweepPreferencesRepository(applicationContext)
      val bridge = PrivilegeBridge(applicationContext)
      val inventory = AppInventoryManager(applicationContext)

      try {
        repo.setSweepRunning(true)
        updateAllSysSweepWidgets(applicationContext)

        val prefs = repo.getSnapshot()
        val diagnostics = bridge.inspectPrivileges(prefs.preferredBackend)
        val apps =
          inventory.loadInstalledApps(
            blacklistedPackages = prefs.blacklistedPackages,
            hasUsageStatsPermission = diagnostics.hasUsageStatsPerm,
          )

        val result =
          bridge.executeSweep(
            actionType = actionType,
            apps = apps,
            includeSystemApps = prefs.includeSystemApps,
            autoProtectEssential = prefs.autoProtectEssential,
            preferredBackend = prefs.preferredBackend,
          )

        repo.saveSweepResult(result)
        updateAllSysSweepWidgets(applicationContext)
        postCompletionNotification(result.summaryMessage)
      } finally {
        repo.setSweepRunning(false)
        isExecuting.set(false)
        updateAllSysSweepWidgets(applicationContext)
        ServiceCompat.stopForeground(this@CleanupForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
      }
    }

    return START_NOT_STICKY
  }

  private fun buildProgressNotification(): Notification {
    val launchIntent = Intent(this, MainActivity::class.java)
    val pendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, SysSweepApp.CLEANUP_CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(getString(R.string.notif_sweeping_title))
      .setContentText(getString(R.string.notif_sweeping_text))
      .setProgress(0, 0, true)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun postCompletionNotification(summary: String) {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val launchIntent = Intent(this, MainActivity::class.java)
    val pendingIntent =
      PendingIntent.getActivity(
        this,
        1,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notif =
      NotificationCompat.Builder(this, SysSweepApp.CLEANUP_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(summary)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    runCatching { manager.notify(RESULT_NOTIF_ID, notif) }
  }

  override fun onDestroy() {
    isExecuting.set(false)
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val EXTRA_ACTION_TYPE = "extra_action_type"
    private const val PROGRESS_NOTIF_ID = 4101
    private const val RESULT_NOTIF_ID = 4102
    private val isExecuting = AtomicBoolean(false)

    fun startSweep(context: Context, actionType: SweepActionType) {
      val intent =
        Intent(context, CleanupForegroundService::class.java).apply {
          putExtra(EXTRA_ACTION_TYPE, actionType.name)
        }
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
