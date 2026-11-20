package com.example.syssweep.shizuku

import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

@Keep
class SysSweepUserService() : ISysSweepUserService.Stub() {

  @Keep constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

  private val packageRegex = Regex("^[a-zA-Z0-9._]+$")

  private val neverRemovePackages =
    setOf(
      "android",
      "com.android.systemui",
      "com.android.settings",
      "com.android.phone",
      "com.android.providers.telephony",
      "com.android.server.telecom",
      "com.android.shell",
      "moe.shizuku.privileged.api",
      "rikka.shizuku.server",
    )

  override fun destroy() {
    exitProcess(0)
  }

  override fun getUid(): Int = Process.myUid()

  override fun trimAllCaches(): Boolean {
    return runCatching {
        val process =
          ProcessBuilder("pm", "trim-caches", "999999999999999")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
      }
      .getOrDefault(false)
  }

  override fun forceStopPackage(packageName: String, userId: Int): Boolean {
    if (!packageRegex.matches(packageName)) return false
    return runCatching {
        val process =
          ProcessBuilder("am", "force-stop", "--user", userId.toString(), packageName)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() == 0) {
          true
        } else {
          val fallback =
            ProcessBuilder("am", "force-stop", packageName).redirectErrorStream(true).start()
          fallback.inputStream.bufferedReader().use { it.readText() }
          fallback.waitFor() == 0
        }
      }
      .getOrDefault(false)
  }

  override fun forceStopPackages(packageNames: List<String>, userId: Int): Int {
    val validPackages = packageNames.filter { packageRegex.matches(it) }
    if (validPackages.isEmpty()) return 0

    return runCatching {
        val process = ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start()
        BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
          for (pkg in validPackages) {
            writer.write("am force-stop --user $userId $pkg && echo STOP_OK\n")
          }
          writer.write("exit 0\n")
          writer.flush()
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
          process.destroyForcibly()
          0
        } else {
          val okCount = output.lines().count { it.trim() == "STOP_OK" }
          if (okCount > 0) okCount else validPackages.size
        }
      }
      .getOrElse {
        var count = 0
        for (pkg in validPackages) {
          if (forceStopPackage(pkg, userId)) count++
        }
        count
      }
  }

  override fun uninstallPackage(packageName: String, userId: Int): Boolean {
    if (!packageRegex.matches(packageName) || packageName in neverRemovePackages) return false
    val userUninstallOk =
      runCatching {
          val process =
            ProcessBuilder("pm", "uninstall", "--user", userId.toString(), packageName)
              .redirectErrorStream(true)
              .start()
          val output = process.inputStream.bufferedReader().use { it.readText() }
          process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) &&
            output.contains("Success", ignoreCase = true)
        }
        .getOrDefault(false)
    if (userUninstallOk) return true

    return runCatching {
        val process =
          ProcessBuilder("pm", "uninstall", packageName).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) &&
          output.contains("Success", ignoreCase = true)
      }
      .getOrDefault(false)
  }

  override fun disablePackage(packageName: String, userId: Int): Boolean {
    if (!packageRegex.matches(packageName) || packageName in neverRemovePackages) return false
    return runCatching {
        val process =
          ProcessBuilder("pm", "disable-user", "--user", userId.toString(), packageName)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 &&
          (output.contains("disabled", ignoreCase = true) ||
            output.contains("new state", ignoreCase = true))
      }
      .getOrDefault(false)
  }

  override fun enablePackage(packageName: String): Boolean {
    if (!packageRegex.matches(packageName)) return false
    return runCatching {
        val process =
          ProcessBuilder("pm", "enable", packageName).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 &&
          (output.contains("enabled", ignoreCase = true) ||
            output.contains("new state", ignoreCase = true))
      }
      .getOrDefault(false)
  }
}
