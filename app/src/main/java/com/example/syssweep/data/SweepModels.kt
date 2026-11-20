package com.example.syssweep.data

enum class PrivilegeMode {
  SYSTEM_PRIV_APP,
  ROOT_SU,
  SHIZUKU,
  LIMITED_NONE,
}

enum class PreferredBackend {
  AUTO,
  SYSTEM_PRIV_APP,
  ROOT_SU,
  SHIZUKU,
}

enum class WidgetPrimaryAction {
  BOOST_BOTH,
  CLEAR_CACHE,
  FORCE_STOP,
}

enum class SweepActionType {
  CLEAR_CACHE,
  FORCE_STOP,
  BOOST_BOTH,
}

enum class JunkCategory(val title: String, val subtitle: String) {
  THUMBNAILS("Thumbnails", "Gallery .thumbnails & .thumbdata caches"),
  TEMP_FILES("Temp & Logs", ".tmp, .temp, .log, .bak & crash files"),
  EMPTY_FOLDERS("Empty Folders", "Unused empty directories in storage"),
  OLD_APKS("Old APK Files", "Downloaded .apk, .apks & .xapk installers"),
  OTHER_JUNK("Other Non-Media", "Residual .exo, .cache & junk (no photos/audio/video/docs)"),
}

data class JunkItem(
  val path: String,
  val name: String,
  val sizeBytes: Long,
  val isDirectory: Boolean,
  val category: JunkCategory,
  val isSelected: Boolean = true,
)

data class DeviceTelemetry(
  val totalStorageBytes: Long = 0L,
  val usedStorageBytes: Long = 0L,
  val freeStorageBytes: Long = 0L,
  val storageUsedPercent: Int = 0,
  val totalRamBytes: Long = 0L,
  val usedRamBytes: Long = 0L,
  val freeRamBytes: Long = 0L,
  val ramUsedPercent: Int = 0,
)

data class InstalledAppInfo(
  val packageName: String,
  val label: String,
  val cacheBytes: Long,
  val isSystemApp: Boolean,
  val isEnabled: Boolean,
  val isStopped: Boolean,
  val isProtectedByDefault: Boolean,
  val protectionReason: String?,
  val isBlacklisted: Boolean,
)

data class PrivilegeDiagnostics(
  val activeMode: PrivilegeMode = PrivilegeMode.LIMITED_NONE,
  val isSystemPartitionApp: Boolean = false,
  val hasClearCachePerm: Boolean = false,
  val hasDeleteCachePerm: Boolean = false,
  val hasForceStopPerm: Boolean = false,
  val hasDeletePackagesPerm: Boolean = false,
  val hasUsageStatsPerm: Boolean = false,
  val hasAllFilesAccess: Boolean = false,
  val isRootAvailable: Boolean = false,
  val isShizukuRunning: Boolean = false,
  val isShizukuPermissionGranted: Boolean = false,
) {
  val hasAnyElevatedPrivilege: Boolean
    get() = activeMode != PrivilegeMode.LIMITED_NONE

  val hasFullSystemPrivAppPerms: Boolean
    get() = hasClearCachePerm && hasForceStopPerm
}

data class SweepResult(
  val actionType: SweepActionType,
  val timestampMillis: Long,
  val modeUsed: PrivilegeMode,
  val cacheTrimmedSuccess: Boolean,
  val freedBytesEstimate: Long,
  val stoppedCount: Int,
  val failedCount: Int,
  val skippedCount: Int,
  val summaryMessage: String,
)
