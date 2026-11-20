package com.example.syssweep.engine

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.example.syssweep.data.DeviceTelemetry
import com.example.syssweep.data.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInventoryManager(private val context: Context) {

  private val essentialSystemPackages =
    setOf(
      "android",
      "com.android.systemui",
      "com.android.settings",
      "com.android.phone",
      "com.android.providers.telephony",
      "com.android.server.telecom",
      "com.android.shell",
      "com.android.deskclock",
      "com.google.android.deskclock",
      "moe.shizuku.privileged.api",
      "rikka.shizuku.server",
      "com.topjohnwu.magisk",
      "me.weishu.kernelsu",
      "me.bmax.apatch",
    )

  suspend fun loadDeviceTelemetry(): DeviceTelemetry =
    withContext(Dispatchers.IO) {
      val (totalStorage, freeStorage) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          runCatching {
              val ssm = context.getSystemService(StorageStatsManager::class.java)
              if (ssm != null) {
                ssm.getTotalBytes(StorageManager.UUID_DEFAULT) to
                  ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
              } else {
                statFsStorage()
              }
            }
            .getOrElse { statFsStorage() }
        } else {
          statFsStorage()
        }

      val usedStorage = (totalStorage - freeStorage).coerceAtLeast(0L)
      val storagePct =
        if (totalStorage > 0L) ((usedStorage * 100L) / totalStorage).toInt().coerceIn(0, 100)
        else 0

      val am = context.getSystemService(ActivityManager::class.java)
      val memInfo = ActivityManager.MemoryInfo()
      am?.getMemoryInfo(memInfo)
      val totalRam = memInfo.totalMem.coerceAtLeast(0L)
      val freeRam = memInfo.availMem.coerceAtLeast(0L)
      val usedRam = (totalRam - freeRam).coerceAtLeast(0L)
      val ramPct =
        if (totalRam > 0L) ((usedRam * 100L) / totalRam).toInt().coerceIn(0, 100) else 0

      DeviceTelemetry(
        totalStorageBytes = totalStorage,
        usedStorageBytes = usedStorage,
        freeStorageBytes = freeStorage.coerceAtLeast(0L),
        storageUsedPercent = storagePct,
        totalRamBytes = totalRam,
        usedRamBytes = usedRam,
        freeRamBytes = freeRam,
        ramUsedPercent = ramPct,
      )
    }

  private fun statFsStorage(): Pair<Long, Long> {
    val stat = StatFs(Environment.getDataDirectory().path)
    return stat.totalBytes to stat.availableBytes
  }

  suspend fun loadInstalledApps(
    blacklistedPackages: Set<String>,
    hasUsageStatsPermission: Boolean,
  ): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val protectedDynamic = resolveDynamicProtectedPackages(pm)

      val packages =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
          )
        } else {
          @Suppress("DEPRECATION")
          pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS)
        }

      val storageStatsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasUsageStatsPermission) {
          context.getSystemService(StorageStatsManager::class.java)
        } else {
          null
        }

      packages
        .asSequence()
        .filter { it.packageName != context.packageName }
        .map { appInfo ->
          val pkg = appInfo.packageName
          val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
          val isPersistent = (appInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
          val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
          val label =
            runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)

          val protectionReason =
            when {
              pkg == "moe.shizuku.privileged.api" || pkg == "rikka.shizuku.server" ->
                "Shizuku Service"
              pkg in protectedDynamic -> protectedDynamic[pkg]
              pkg in essentialSystemPackages -> "Core System"
              isPersistent -> "Persistent Process"
              else -> null
            }

          val cacheBytes =
            if (storageStatsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              queryAppCacheBytes(storageStatsManager, appInfo)
            } else {
              0L
            }

          InstalledAppInfo(
            packageName = pkg,
            label = label,
            cacheBytes = cacheBytes,
            isSystemApp = isSystem,
            isEnabled = appInfo.enabled,
            isStopped = isStopped,
            isProtectedByDefault = protectionReason != null,
            protectionReason = protectionReason,
            isBlacklisted = pkg in blacklistedPackages,
          )
        }
        .sortedWith(
          compareByDescending<InstalledAppInfo> { it.cacheBytes }
            .thenBy { it.isStopped }
            .thenBy { it.label.lowercase() }
        )
        .toList()
    }

  private fun queryAppCacheBytes(
    storageStatsManager: StorageStatsManager,
    appInfo: ApplicationInfo,
  ): Long {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
    return runCatching {
        val stats =
          storageStatsManager.queryStatsForPackage(
            appInfo.storageUuid ?: StorageManager.UUID_DEFAULT,
            appInfo.packageName,
            Process.myUserHandle(),
          )
        stats.cacheBytes
      }
      .getOrDefault(0L)
  }

  private fun resolveDynamicProtectedPackages(pm: PackageManager): Map<String, String> {
    val result = mutableMapOf<String, String>()

    val homeIntent =
      Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    val homeResolve =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION") pm.resolveActivity(homeIntent, 0)
      }
    homeResolve?.activityInfo?.packageName?.let { result[it] = "Default Launcher" }

    val imm = context.getSystemService(InputMethodManager::class.java)
    imm?.enabledInputMethodList?.forEach { info ->
      result.putIfAbsent(info.packageName, "Keyboard (IME)")
    }

    val am = context.getSystemService(AccessibilityManager::class.java)
    am?.getEnabledAccessibilityServiceList(
      android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
      ?.forEach { info ->
        info.resolveInfo?.serviceInfo?.packageName?.let {
          result.putIfAbsent(it, "Accessibility")
        }
      }

    return result
  }
}
