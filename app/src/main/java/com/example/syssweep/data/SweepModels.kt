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

data class InstalledAppInfo(
  val packageName: String,
  val label: String,
  val cacheBytes: Long,
  val isSystemApp: Boolean,
  val isStopped: Boolean,
  val isProtectedByDefault: Boolean,
  val protectionReason: String?,
  val isWhitelisted: Boolean,
)

data class PrivilegeDiagnostics(
  val activeMode: PrivilegeMode = PrivilegeMode.LIMITED_NONE,
  val isSystemPartitionApp: Boolean = false,
  val hasClearCachePerm: Boolean = false,
  val hasDeleteCachePerm: Boolean = false,
  val hasForceStopPerm: Boolean = false,
  val hasUsageStatsPerm: Boolean = false,
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
