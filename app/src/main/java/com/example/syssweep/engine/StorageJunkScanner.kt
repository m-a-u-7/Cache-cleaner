package com.example.syssweep.engine

import android.os.Environment
import com.example.syssweep.data.JunkCategory
import com.example.syssweep.data.JunkItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageJunkScanner {

  suspend fun scan(
    rootDir: File = Environment.getExternalStorageDirectory(),
  ): List<JunkItem> =
    withContext(Dispatchers.IO) {
      if (!rootDir.exists() || !rootDir.isDirectory) return@withContext emptyList()
      val results = mutableListOf<JunkItem>()
      scanRecursive(dir = rootDir, rootDir = rootDir, depth = 0, results = results)
      results.sortedWith(
        compareBy<JunkItem> { it.category.ordinal }
          .thenByDescending { it.sizeBytes }
          .thenBy { it.name.lowercase() }
      )
    }

  suspend fun cleanItems(items: List<JunkItem>): Pair<Int, Long> =
    withContext(Dispatchers.IO) {
      var deletedCount = 0
      var freedBytes = 0L

      val filesFirst = items.filter { !it.isDirectory }
      for (item in filesFirst) {
        val file = File(item.path)
        if (!file.exists()) continue
        if (item.category != JunkCategory.THUMBNAILS && isProtectedMediaOrDocument(file.name)) {
          continue
        }
        val length = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) {
          deletedCount++
          freedBytes += length
        }
      }

      // Delete directories deepest-first so nested empty dirs clean in one pass
      val dirsDeepestFirst =
        items
          .filter { it.isDirectory }
          .sortedByDescending { it.path.count { ch -> ch == File.separatorChar } }
      for (dirItem in dirsDeepestFirst) {
        val dir = File(dirItem.path)
        if (!dir.exists() || !dir.isDirectory) continue
        if (dir.name in PROTECTED_ROOT_DIRS) continue
        val children = dir.listFiles()
        if (children != null && children.isEmpty()) {
          if (runCatching { dir.delete() }.getOrDefault(false)) {
            deletedCount++
          }
        }
      }

      deletedCount to freedBytes
    }

  private fun scanRecursive(
    dir: File,
    rootDir: File,
    depth: Int,
    results: MutableList<JunkItem>,
  ) {
    if (depth > MAX_SCAN_DEPTH) return
    val dirPath = dir.absolutePath
    // Skip Android/data and Android/obb which are restricted on Android 11+
    if (dirPath.endsWith("/Android/data") || dirPath.endsWith("/Android/obb")) return

    val children = runCatching { dir.listFiles() }.getOrNull() ?: return
    if (children.isEmpty()) {
      if (dir != rootDir && !isProtectedDirectory(dir, rootDir)) {
        results.add(
          JunkItem(
            path = dir.absolutePath,
            name = dir.name,
            sizeBytes = 0L,
            isDirectory = true,
            category = JunkCategory.EMPTY_FOLDERS,
            isSelected = true,
          )
        )
      }
      return
    }

    val dirNameLower = dir.name.lowercase()
    val inThumbnailsFolder =
      dirNameLower == ".thumbnails" ||
        dirPath.contains("/.thumbnails/", ignoreCase = true) ||
        dirPath.contains("/thumbnails/", ignoreCase = true)
    val inCacheFolder =
      dirNameLower == "cache" ||
        dirNameLower == ".cache" ||
        dirNameLower == "temp" ||
        dirNameLower == "logs" ||
        dirPath.contains("/.cache/", ignoreCase = true)

    for (child in children) {
      // Never follow symlinks to avoid loops
      if (isSymlink(child)) continue

      if (child.isDirectory) {
        scanRecursive(dir = child, rootDir = rootDir, depth = depth + 1, results = results)
      } else if (child.isFile) {
        val fileName = child.name
        if (fileName == ".nomedia") continue

        val category = classifyFile(fileName, inThumbnailsFolder, inCacheFolder)
        if (category != null) {
          results.add(
            JunkItem(
              path = child.absolutePath,
              name = fileName,
              sizeBytes = child.length().coerceAtLeast(0L),
              isDirectory = false,
              category = category,
              isSelected = category != JunkCategory.OLD_APKS, // let user review APKs before wiping
            )
          )
        }
      }
    }
  }

  internal fun classifyFile(
    fileName: String,
    inThumbnailsFolder: Boolean,
    inCacheFolder: Boolean,
  ): JunkCategory? {
    val lower = fileName.lowercase()
    if (lower == ".nomedia") return null

    if (
      inThumbnailsFolder ||
        lower.startsWith(".thumbdata") ||
        lower.startsWith("thumbcache") ||
        lower.endsWith(".thumb")
    ) {
      return JunkCategory.THUMBNAILS
    }

    if (isProtectedMediaOrDocument(lower)) return null

    val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
    if (ext in APK_EXTENSIONS) {
      return JunkCategory.OLD_APKS
    }

    if (ext in TEMP_LOG_EXTENSIONS || lower.endsWith("~")) {
      return JunkCategory.TEMP_FILES
    }

    if (ext in OTHER_JUNK_EXTENSIONS || (inCacheFolder && ext.isNotEmpty())) {
      return JunkCategory.OTHER_JUNK
    }

    return null
  }

  internal fun isProtectedMediaOrDocument(fileName: String): Boolean {
    val ext = fileName.lowercase().substringAfterLast('.', missingDelimiterValue = "")
    return ext in PROTECTED_EXTENSIONS
  }

  private fun isProtectedDirectory(dir: File, rootDir: File): Boolean {
    if (dir.parentFile == rootDir && dir.name in PROTECTED_ROOT_DIRS) return true
    if (dir.name.equals("Android", ignoreCase = true) && dir.parentFile == rootDir) return true
    return false
  }

  private fun isSymlink(file: File): Boolean {
    return runCatching {
        val canonParent = file.parentFile?.canonicalFile ?: return@runCatching false
        val fileInCanonDir = File(canonParent, file.name)
        fileInCanonDir.canonicalFile != fileInCanonDir.absoluteFile
      }
      .getOrDefault(false)
  }

  companion object {
    private const val MAX_SCAN_DEPTH = 10

    private val PROTECTED_ROOT_DIRS =
      setOf(
        "DCIM",
        "Pictures",
        "Movies",
        "Music",
        "Documents",
        "Download",
        "Downloads",
        "Android",
        "Ringtones",
        "Notifications",
        "Alarms",
        "Podcasts",
        "Audiobooks",
        "Recordings",
      )

    private val PROTECTED_EXTENSIONS =
      setOf(
        // Photos & Graphics
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng", "raw", "avif", "tiff",
        "tif", "svg", "ico",
        // Audio
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr", "mid", "midi", "alac",
        "aiff",
        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mts", "vob",
        // Documents, Text & Books
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods",
        "odp", "epub", "mobi", "azw3", "md", "json", "xml", "html", "htm", "vcf", "ics",
        // Archives & Backups (preserve user zips/backups)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "img", "iso",
      )

    private val TEMP_LOG_EXTENSIONS =
      setOf("tmp", "temp", "log", "bak", "chk", "part", "dmp", "trace", "crdownload", "opdownload")

    private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")

    private val OTHER_JUNK_EXTENSIONS =
      setOf("exo", "cache", "idx", "tmb", "lost", "crash", "stackdump")
  }
}
