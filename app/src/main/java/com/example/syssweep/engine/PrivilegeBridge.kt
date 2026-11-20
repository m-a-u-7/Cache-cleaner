package com.example.syssweep.engine

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.example.BuildConfig
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.PrivilegeDiagnostics
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.data.SweepResult
import com.example.syssweep.shizuku.ISysSweepUserService
import com.example.syssweep.shizuku.SysSweepUserService
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

class PrivilegeBridge(private val context: Context) {

  private val packageRegex = Regex("^[a-zA-Z0-9._]+$")

  suspend fun inspectPrivileges(preferredBackend: PreferredBackend): PrivilegeDiagnostics =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val appFlags = context.applicationInfo.flags
      val isSystemApp =
        (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
          (appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

      val hasClearCache =
        pm.checkPermission("android.permission.CLEAR_APP_CACHE", context.packageName) ==
          PackageManager.PERMISSION_GRANTED
      val hasDeleteCache =
        pm.checkPermission("android.permission.DELETE_CACHE_FILES", context.packageName) ==
          PackageManager.PERMISSION_GRANTED
      val hasForceStop =
        pm.checkPermission("android.permission.FORCE_STOP_PACKAGES", context.packageName) ==
          PackageManager.PERMISSION_GRANTED

      val hasUsageStats = hasUsageStatsAccess()
      val isRoot = checkRootAvailable()
      val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
      val shizukuGranted =
        if (shizukuRunning) {
          runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)
        } else {
          false
        }

      val systemReady = isSystemApp && (hasClearCache || hasForceStop)
      val shizukuReady = shizukuRunning && shizukuGranted

      val activeMode =
        when (preferredBackend) {
          PreferredBackend.SYSTEM_PRIV_APP ->
            if (systemReady) PrivilegeMode.SYSTEM_PRIV_APP else PrivilegeMode.LIMITED_NONE
          PreferredBackend.ROOT_SU ->
            if (isRoot) PrivilegeMode.ROOT_SU else PrivilegeMode.LIMITED_NONE
          PreferredBackend.SHIZUKU ->
            if (shizukuReady) PrivilegeMode.SHIZUKU else PrivilegeMode.LIMITED_NONE
          PreferredBackend.AUTO ->
            when {
              systemReady -> PrivilegeMode.SYSTEM_PRIV_APP
              isRoot -> PrivilegeMode.ROOT_SU
              shizukuReady -> PrivilegeMode.SHIZUKU
              else -> PrivilegeMode.LIMITED_NONE
            }
        }

      PrivilegeDiagnostics(
        activeMode = activeMode,
        isSystemPartitionApp = isSystemApp,
        hasClearCachePerm = hasClearCache,
        hasDeleteCachePerm = hasDeleteCache,
        hasForceStopPerm = hasForceStop,
        hasUsageStatsPerm = hasUsageStats,
        isRootAvailable = isRoot,
        isShizukuRunning = shizukuRunning,
        isShizukuPermissionGranted = shizukuGranted,
      )
    }

  fun hasUsageStatsAccess(): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  private fun checkRootAvailable(): Boolean {
    return runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
        if (!finished) {
          process.destroyForcibly()
          false
        } else {
          val output = process.inputStream.bufferedReader().use { it.readText() }
          process.exitValue() == 0 && output.contains("uid=0")
        }
      }
      .getOrDefault(false)
  }

  suspend fun executeSweep(
    actionType: SweepActionType,
    apps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    autoProtectEssential: Boolean,
    preferredBackend: PreferredBackend,
  ): SweepResult =
    withContext(Dispatchers.IO) {
      val diagnostics = inspectPrivileges(preferredBackend)
      val mode = diagnostics.activeMode

      val targetApps =
        apps.filter { app ->
          val systemAllowed = includeSystemApps || !app.isSystemApp
          val essentialAllowed = !autoProtectEssential || !app.isProtectedByDefault
          systemAllowed && essentialAllowed && !app.isWhitelisted
        }
      val skippedCount = apps.size - targetApps.size
      val estimatedCacheBytes =
        apps
          .filter { includeSystemApps || !it.isSystemApp }
          .sumOf { it.cacheBytes }

      if (mode == PrivilegeMode.LIMITED_NONE) {
        return@withContext SweepResult(
          actionType = actionType,
          timestampMillis = System.currentTimeMillis(),
          modeUsed = mode,
          cacheTrimmedSuccess = false,
          freedBytesEstimate = 0L,
          stoppedCount = 0,
          failedCount = 0,
          skippedCount = skippedCount,
          summaryMessage =
            "No elevated privilege active. Flash as priv-app, grant Root, or start Shizuku.",
        )
      }

      val packagesToStop =
        if (actionType == SweepActionType.FORCE_STOP || actionType == SweepActionType.BOOST_BOTH) {
          targetApps.filter { !it.isStopped }.map { it.packageName }
        } else {
          emptyList()
        }

      val shouldTrimCache =
        actionType == SweepActionType.CLEAR_CACHE || actionType == SweepActionType.BOOST_BOTH

      when (mode) {
        PrivilegeMode.SYSTEM_PRIV_APP ->
          executeViaSystemPrivApp(
            actionType = actionType,
            shouldTrimCache = shouldTrimCache,
            packagesToStop = packagesToStop,
            estimatedCacheBytes = estimatedCacheBytes,
            skippedCount = skippedCount,
            diagnostics = diagnostics,
          )
        PrivilegeMode.ROOT_SU ->
          executeViaRoot(
            actionType = actionType,
            shouldTrimCache = shouldTrimCache,
            packagesToStop = packagesToStop,
            estimatedCacheBytes = estimatedCacheBytes,
            skippedCount = skippedCount,
          )
        PrivilegeMode.SHIZUKU ->
          executeViaShizuku(
            actionType = actionType,
            shouldTrimCache = shouldTrimCache,
            packagesToStop = packagesToStop,
            estimatedCacheBytes = estimatedCacheBytes,
            skippedCount = skippedCount,
          )
        PrivilegeMode.LIMITED_NONE ->
          SweepResult(
            actionType = actionType,
            timestampMillis = System.currentTimeMillis(),
            modeUsed = mode,
            cacheTrimmedSuccess = false,
            freedBytesEstimate = 0L,
            stoppedCount = 0,
            failedCount = 0,
            skippedCount = skippedCount,
            summaryMessage = "No privilege available.",
          )
      }
    }

  suspend fun forceStopSinglePackage(
    packageName: String,
    preferredBackend: PreferredBackend,
  ): Boolean =
    withContext(Dispatchers.IO) {
      if (!packageRegex.matches(packageName)) return@withContext false
      val diagnostics = inspectPrivileges(preferredBackend)
      when (diagnostics.activeMode) {
        PrivilegeMode.SYSTEM_PRIV_APP -> forceStopPackageSystem(packageName)
        PrivilegeMode.ROOT_SU -> runRootCommands(listOf("am force-stop --user 0 $packageName"))
        PrivilegeMode.SHIZUKU ->
          withShizukuService { service ->
            service.forceStopPackage(packageName, currentUserId())
          } ?: false
        PrivilegeMode.LIMITED_NONE -> false
      }
    }

  suspend fun clearSinglePackageCache(
    packageName: String,
    preferredBackend: PreferredBackend,
  ): Boolean =
    withContext(Dispatchers.IO) {
      if (!packageRegex.matches(packageName)) return@withContext false
      val diagnostics = inspectPrivileges(preferredBackend)
      when (diagnostics.activeMode) {
        PrivilegeMode.SYSTEM_PRIV_APP -> deleteSingleAppCacheSystem(packageName)
        PrivilegeMode.ROOT_SU ->
          runRootCommands(
            listOf(
              "rm -rf /data/user/0/$packageName/cache/* /data/user/0/$packageName/code_cache/* /sdcard/Android/data/$packageName/cache/*"
            )
          )
        PrivilegeMode.SHIZUKU ->
          // Non-root Shizuku only supports global cache trim (`pm trim-caches`), not per-package delete
          withShizukuService { service -> service.trimAllCaches() } ?: false
        PrivilegeMode.LIMITED_NONE -> false
      }
    }

  private fun executeViaSystemPrivApp(
    actionType: SweepActionType,
    shouldTrimCache: Boolean,
    packagesToStop: List<String>,
    estimatedCacheBytes: Long,
    skippedCount: Int,
    diagnostics: PrivilegeDiagnostics,
  ): SweepResult {
    val cacheOk =
      if (shouldTrimCache && (diagnostics.hasClearCachePerm || diagnostics.hasDeleteCachePerm)) {
        trimCachesSystem()
      } else {
        false
      }

    var stopped = 0
    var failed = 0
    if (packagesToStop.isNotEmpty()) {
      if (diagnostics.hasForceStopPerm) {
        for (pkg in packagesToStop) {
          if (forceStopPackageSystem(pkg)) stopped++ else failed++
        }
      } else {
        failed = packagesToStop.size
      }
    }

    val summary =
      buildSummaryMessage(
        actionType = actionType,
        cacheOk = cacheOk,
        stopped = stopped,
        failed = failed,
        modeLabel = "Priv-App",
      )
    return SweepResult(
      actionType = actionType,
      timestampMillis = System.currentTimeMillis(),
      modeUsed = PrivilegeMode.SYSTEM_PRIV_APP,
      cacheTrimmedSuccess = cacheOk,
      freedBytesEstimate = if (cacheOk) estimatedCacheBytes else 0L,
      stoppedCount = stopped,
      failedCount = failed,
      skippedCount = skippedCount,
      summaryMessage = summary,
    )
  }

  private fun executeViaRoot(
    actionType: SweepActionType,
    shouldTrimCache: Boolean,
    packagesToStop: List<String>,
    estimatedCacheBytes: Long,
    skippedCount: Int,
  ): SweepResult {
    val userId = currentUserId()
    val cmds = mutableListOf<String>()
    if (shouldTrimCache) {
      cmds.add("pm trim-caches 999999999999999")
    }
    packagesToStop.filter { packageRegex.matches(it) }.forEach { pkg ->
      cmds.add("am force-stop --user $userId $pkg")
    }

    val ok = if (cmds.isEmpty()) true else runRootCommands(cmds)
    val stopped = if (ok) packagesToStop.size else 0
    val failed = if (ok) 0 else packagesToStop.size
    val cacheOk = shouldTrimCache && ok

    return SweepResult(
      actionType = actionType,
      timestampMillis = System.currentTimeMillis(),
      modeUsed = PrivilegeMode.ROOT_SU,
      cacheTrimmedSuccess = cacheOk,
      freedBytesEstimate = if (cacheOk) estimatedCacheBytes else 0L,
      stoppedCount = stopped,
      failedCount = failed,
      skippedCount = skippedCount,
      summaryMessage =
        buildSummaryMessage(
          actionType = actionType,
          cacheOk = cacheOk,
          stopped = stopped,
          failed = failed,
          modeLabel = "Root",
        ),
    )
  }

  private suspend fun executeViaShizuku(
    actionType: SweepActionType,
    shouldTrimCache: Boolean,
    packagesToStop: List<String>,
    estimatedCacheBytes: Long,
    skippedCount: Int,
  ): SweepResult {
    val userId = currentUserId()
    var cacheOk = false
    var stopped = 0
    var failed = 0

    val executed =
      withShizukuService { service ->
        if (shouldTrimCache) {
          cacheOk = service.trimAllCaches()
        }
        if (packagesToStop.isNotEmpty()) {
          stopped = service.forceStopPackages(packagesToStop, userId)
          failed = (packagesToStop.size - stopped).coerceAtLeast(0)
        }
        true
      } ?: false

    if (!executed) {
      failed = packagesToStop.size
    }

    val summary =
      if (!executed) {
        "Shizuku service timed out or disconnected. Check Shizuku app."
      } else {
        buildSummaryMessage(
          actionType = actionType,
          cacheOk = cacheOk,
          stopped = stopped,
          failed = failed,
          modeLabel = "Shizuku",
        )
      }

    return SweepResult(
      actionType = actionType,
      timestampMillis = System.currentTimeMillis(),
      modeUsed = PrivilegeMode.SHIZUKU,
      cacheTrimmedSuccess = cacheOk,
      freedBytesEstimate = if (cacheOk) estimatedCacheBytes else 0L,
      stoppedCount = stopped,
      failedCount = failed,
      skippedCount = skippedCount,
      summaryMessage = summary,
    )
  }

  private fun forceStopPackageSystem(packageName: String): Boolean {
    val am = context.getSystemService(ActivityManager::class.java) ?: return false
    return runCatching {
        HiddenApiBypass.invoke(ActivityManager::class.java, am, "forceStopPackage", packageName)
        true
      }
      .getOrElse {
        runCatching {
            val method =
              HiddenApiBypass.getDeclaredMethods(ActivityManager::class.java)
                .filterIsInstance<java.lang.reflect.Method>()
                .firstOrNull { it.name == "forceStopPackageAsUser" && it.parameterCount == 2 }
                ?: return@runCatching false
            method.isAccessible = true
            method.invoke(am, packageName, currentUserId())
            true
          }
          .getOrDefault(false)
      }
  }

  private fun trimCachesSystem(): Boolean {
    val pm = context.packageManager
    val targetBytes = Long.MAX_VALUE / 2
    return runCatching {
        val methods =
          HiddenApiBypass.getDeclaredMethods(pm.javaClass)
            .filterIsInstance<java.lang.reflect.Method>()
            .filter { it.name == "freeStorageAndNotify" }
        for (method in methods.sortedByDescending { it.parameterCount }) {
          val params = method.parameterTypes
          method.isAccessible = true
          when (params.size) {
            4 -> {
              // (String volumeUuid, long idealStorageSize, int storageFlags, IPackageDataObserver)
              if (params[0] == String::class.java && params[1] == Long::class.javaPrimitiveType) {
                method.invoke(pm, null, targetBytes, 0, null)
                return@runCatching true
              }
            }
            3 -> {
              // (String volumeUuid, long idealStorageSize, IPackageDataObserver)
              if (params[0] == String::class.java && params[1] == Long::class.javaPrimitiveType) {
                method.invoke(pm, null, targetBytes, null)
                return@runCatching true
              }
            }
            2 -> {
              // (long idealStorageSize, IPackageDataObserver)
              if (params[0] == Long::class.javaPrimitiveType) {
                method.invoke(pm, targetBytes, null)
                return@runCatching true
              }
            }
          }
        }
        false
      }
      .getOrDefault(false)
  }

  private fun deleteSingleAppCacheSystem(packageName: String): Boolean {
    val pm = context.packageManager
    return runCatching {
        val method =
          HiddenApiBypass.getDeclaredMethods(pm.javaClass)
            .filterIsInstance<java.lang.reflect.Method>()
            .firstOrNull { it.name == "deleteApplicationCacheFiles" && it.parameterCount == 2 }
            ?: return@runCatching false
        method.isAccessible = true
        method.invoke(pm, packageName, null)
        true
      }
      .getOrDefault(false)
  }

  private fun runRootCommands(commands: List<String>): Boolean {
    if (commands.isEmpty()) return true
    return runCatching {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
          for (cmd in commands) {
            writer.write(cmd)
            writer.write("\n")
          }
          writer.write("exit 0\n")
          writer.flush()
        }
        process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(20, TimeUnit.SECONDS)
        if (!finished) {
          process.destroyForcibly()
          false
        } else {
          process.exitValue() == 0
        }
      }
      .getOrDefault(false)
  }

  private suspend fun <T> withShizukuService(block: (ISysSweepUserService) -> T): T? {
    val args =
      Shizuku.UserServiceArgs(
          ComponentName(BuildConfig.APPLICATION_ID, SysSweepUserService::class.java.name)
        )
        .daemon(false)
        .processNameSuffix("syssweep_shizuku")
        .tag("SysSweepUserService")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    var boundConnection: ServiceConnection? = null
    return try {
      val service =
        withTimeoutOrNull(5000L) {
          suspendCancellableCoroutine<ISysSweepUserService?> { cont ->
            val connection =
              object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                  if (binder != null && binder.pingBinder()) {
                    if (cont.isActive) {
                      cont.resume(ISysSweepUserService.Stub.asInterface(binder))
                    }
                  } else if (cont.isActive) {
                    cont.resume(null)
                  }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                  if (cont.isActive) {
                    cont.resume(null)
                  }
                }
              }
            boundConnection = connection
            runCatching { Shizuku.bindUserService(args, connection) }
              .onFailure {
                if (cont.isActive) cont.resume(null)
              }
          }
        }
      if (service != null) {
        block(service)
      } else {
        null
      }
    } finally {
      boundConnection?.let { conn -> runCatching { Shizuku.unbindUserService(args, conn, true) } }
    }
  }

  private fun currentUserId(): Int = Process.myUid() / 100000

  private fun buildSummaryMessage(
    actionType: SweepActionType,
    cacheOk: Boolean,
    stopped: Int,
    failed: Int,
    modeLabel: String,
  ): String {
    val parts = mutableListOf<String>()
    if (actionType == SweepActionType.CLEAR_CACHE || actionType == SweepActionType.BOOST_BOTH) {
      parts.add(if (cacheOk) "Caches trimmed" else "Cache trim failed")
    }
    if (actionType == SweepActionType.FORCE_STOP || actionType == SweepActionType.BOOST_BOTH) {
      val failText = if (failed > 0) " ($failed failed)" else ""
      parts.add("$stopped apps force-stopped$failText")
    }
    return "${parts.joinToString(" • ")} via $modeLabel"
  }
}
