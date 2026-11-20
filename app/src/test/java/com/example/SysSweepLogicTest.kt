package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.data.JunkCategory
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.engine.PrivilegeBridge
import com.example.syssweep.engine.StorageJunkScanner
import com.example.syssweep.util.FormatUtils
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SysSweepLogicTest {

  @Test
  fun `formatBytes handles zero and positive values`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("0 B", FormatUtils.formatBytes(context, 0L))
    assertEquals("0 B", FormatUtils.formatBytes(context, -5L))
    val kb = FormatUtils.formatBytes(context, 2048L)
    assertFalse(kb.isBlank())
  }

  @Test
  fun `inspectPrivileges reports non-system mode in standard sandbox`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = PrivilegeBridge(context)
    val diag = bridge.inspectPrivileges(PreferredBackend.SYSTEM_PRIV_APP)
    assertFalse(diag.isSystemPartitionApp)
    assertEquals(PrivilegeMode.LIMITED_NONE, diag.activeMode)
  }

  @Test
  fun `blacklisted and system protected apps are counted as skipped in sweep`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = PrivilegeBridge(context)
    val sampleApps =
      listOf(
        InstalledAppInfo(
          packageName = "com.whatsapp",
          label = "WhatsApp",
          cacheBytes = 1024L,
          isSystemApp = false,
          isEnabled = true,
          isStopped = false,
          isProtectedByDefault = false,
          protectionReason = null,
          isBlacklisted = true,
        ),
        InstalledAppInfo(
          packageName = "com.android.systemui",
          label = "System UI",
          cacheBytes = 512L,
          isSystemApp = true,
          isEnabled = true,
          isStopped = false,
          isProtectedByDefault = true,
          protectionReason = "Core System",
          isBlacklisted = false,
        ),
      )

    val result =
      bridge.executeSweep(
        actionType = SweepActionType.FORCE_STOP,
        apps = sampleApps,
        includeSystemApps = false,
        autoProtectEssential = true,
        preferredBackend = PreferredBackend.SYSTEM_PRIV_APP,
      )
    assertEquals(2, result.skippedCount)
    assertEquals(0, result.stoppedCount)
  }

  @Test
  fun `storage junk scanner protects media and documents while flagging thumbnails temp empty dirs and apks`() =
    runTest {
      val root = Files.createTempDirectory("syssweep_scan_test").toFile()
      try {
        val dcim = File(root, "DCIM").apply { mkdirs() }
        val photo = File(dcim, "vacation.jpg").apply { writeText("photo-bytes") }
        val thumbDir = File(dcim, ".thumbnails").apply { mkdirs() }
        val thumbFile = File(thumbDir, "10001.jpg").apply { writeText("thumb-bytes") }
        val thumbData = File(thumbDir, ".thumbdata4").apply { writeText("thumbdata") }

        val docs = File(root, "Documents").apply { mkdirs() }
        val pdf = File(docs, "invoice.pdf").apply { writeText("pdf-bytes") }
        val song = File(root, "Music/song.mp3").apply {
          parentFile?.mkdirs()
          writeText("mp3-bytes")
        }
        val video = File(root, "Movies/clip.mp4").apply {
          parentFile?.mkdirs()
          writeText("mp4-bytes")
        }

        val tempLog = File(root, "debug_crash.log").apply { writeText("log-bytes") }
        val oldApk = File(root, "Download/old_app.apk").apply {
          parentFile?.mkdirs()
          writeText("apk-bytes")
        }
        val emptyCustomDir = File(root, "LeftoverAppFolder").apply { mkdirs() }
        val protectedEmptyDcim = File(root, "Pictures").apply { mkdirs() }

        val scanner = StorageJunkScanner()
        val found = scanner.scan(root)
        val foundPaths = found.map { it.path }.toSet()

        assertFalse(photo.absolutePath in foundPaths)
        assertFalse(pdf.absolutePath in foundPaths)
        assertFalse(song.absolutePath in foundPaths)
        assertFalse(video.absolutePath in foundPaths)
        assertFalse(protectedEmptyDcim.absolutePath in foundPaths)

        assertTrue(found.any { it.path == thumbFile.absolutePath && it.category == JunkCategory.THUMBNAILS })
        assertTrue(found.any { it.path == thumbData.absolutePath && it.category == JunkCategory.THUMBNAILS })
        assertTrue(found.any { it.path == tempLog.absolutePath && it.category == JunkCategory.TEMP_FILES })
        assertTrue(found.any { it.path == oldApk.absolutePath && it.category == JunkCategory.OLD_APKS })
        assertTrue(found.any { it.path == emptyCustomDir.absolutePath && it.category == JunkCategory.EMPTY_FOLDERS })

        val (deleted, _) = scanner.cleanItems(found)
        assertTrue(deleted >= 5)
        assertTrue(photo.exists())
        assertTrue(pdf.exists())
        assertTrue(song.exists())
        assertTrue(video.exists())
        assertFalse(tempLog.exists())
        assertFalse(oldApk.exists())
        assertFalse(emptyCustomDir.exists())
      } finally {
        root.deleteRecursively()
      }
    }
}
