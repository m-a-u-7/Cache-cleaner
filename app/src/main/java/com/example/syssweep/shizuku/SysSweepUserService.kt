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
            writer.write("am force-stop --user $userId $pkg\n")
          }
          writer.write("exit 0\n")
          writer.flush()
        }
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        validPackages.size
      }
      .getOrElse {
        var count = 0
        for (pkg in validPackages) {
          if (forceStopPackage(pkg, userId)) count++
        }
        count
      }
  }
}
