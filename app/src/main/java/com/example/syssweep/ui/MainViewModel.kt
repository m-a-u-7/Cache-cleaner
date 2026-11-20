package com.example.syssweep.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syssweep.data.DeviceTelemetry
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.data.JunkCategory
import com.example.syssweep.data.JunkItem
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.PrivilegeDiagnostics
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.data.SweepPreferencesRepository
import com.example.syssweep.data.SweepUserPreferences
import com.example.syssweep.data.WidgetPrimaryAction
import com.example.syssweep.engine.AppInventoryManager
import com.example.syssweep.engine.PrivilegeBridge
import com.example.syssweep.engine.StorageJunkScanner
import com.example.syssweep.util.FormatUtils
import com.example.syssweep.widget.updateAllSysSweepWidgets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class AppFilter(val label: String) {
  USER("User Apps"),
  SYSTEM_BLOAT("System / Bloat"),
  ACTIVE("Running"),
  BLACKLISTED("Blacklist"),
  DISABLED("Disabled"),
  PROTECTED("Core Safe"),
}

data class DashboardUiState(
  val isLoadingInventory: Boolean = true,
  val telemetry: DeviceTelemetry = DeviceTelemetry(),
  val diagnostics: PrivilegeDiagnostics = PrivilegeDiagnostics(),
  val preferences: SweepUserPreferences = SweepUserPreferences(),
  val apps: List<InstalledAppInfo> = emptyList(),
  val selectedToKillPackages: Set<String> = emptySet(),
  val searchQuery: String = "",
  val selectedFilter: AppFilter = AppFilter.USER,
  val isScanningJunk: Boolean = false,
  val isCleaningJunk: Boolean = false,
  val hasScannedJunkOnce: Boolean = false,
  val junkItems: List<JunkItem> = emptyList(),
) {
  val totalUserCacheBytes: Long
    get() =
      apps
        .filter { preferences.includeSystemApps || !it.isSystemApp }
        .sumOf { it.cacheBytes }

  val cachedAppsPreview: List<InstalledAppInfo>
    get() =
      apps
        .filter { (preferences.includeSystemApps || !it.isSystemApp) && it.cacheBytes > 0L }
        .sortedByDescending { it.cacheBytes }
        .take(5)

  val cachedAppsCount: Int
    get() =
      apps.count { (preferences.includeSystemApps || !it.isSystemApp) && it.cacheBytes > 0L }

  val userAppsCount: Int
    get() = apps.count { preferences.includeSystemApps || !it.isSystemApp }

  val activeKillCandidates: List<InstalledAppInfo>
    get() =
      apps
        .filter { app ->
          val sysOk = preferences.includeSystemApps || !app.isSystemApp
          val essOk = !preferences.autoProtectEssential || !app.isProtectedByDefault
          sysOk && essOk && !app.isBlacklisted && !app.isStopped && app.isEnabled
        }
        .sortedBy { it.label.lowercase() }

  val activeKillPreview: List<InstalledAppInfo>
    get() = activeKillCandidates.take(8)

  val activeTargetAppsCount: Int
    get() = activeKillCandidates.size

  val effectiveSelectedToKill: Set<String>
    get() {
      val candidateSet = activeKillCandidates.map { it.packageName }.toSet()
      return selectedToKillPackages.intersect(candidateSet)
    }

  val blacklistedCount: Int
    get() = apps.count { it.isBlacklisted }

  val disabledCount: Int
    get() = apps.count { !it.isEnabled }

  val canDeleteSingleCache: Boolean
    get() =
      diagnostics.activeMode == PrivilegeMode.ROOT_SU ||
        (diagnostics.activeMode == PrivilegeMode.SYSTEM_PRIV_APP && diagnostics.hasDeleteCachePerm)

  val canPrivilegedAppManage: Boolean
    get() = diagnostics.activeMode != PrivilegeMode.LIMITED_NONE

  val canPrivilegedUninstall: Boolean
    get() =
      diagnostics.activeMode == PrivilegeMode.SHIZUKU ||
        diagnostics.activeMode == PrivilegeMode.ROOT_SU ||
        (diagnostics.activeMode == PrivilegeMode.SYSTEM_PRIV_APP &&
          diagnostics.hasDeletePackagesPerm)

  val cacheGaugePercent: Int
    get() {
      val totalStorage = telemetry.totalStorageBytes
      if (totalStorage <= 0L || totalUserCacheBytes <= 0L) return 0
      // Scale relative to a practical 2 GB / 5% visual reference so MB caches remain clearly visible
      val reference = (totalStorage / 20L).coerceAtLeast(512L * 1024L * 1024L)
      return ((totalUserCacheBytes * 100L) / reference).toInt().coerceIn(4, 100)
    }

  val selectedJunkCount: Int
    get() = junkItems.count { it.isSelected }

  val selectedJunkBytes: Long
    get() = junkItems.filter { it.isSelected }.sumOf { it.sizeBytes }

  val totalJunkBytes: Long
    get() = junkItems.sumOf { it.sizeBytes }

  val filteredApps: List<InstalledAppInfo>
    get() {
      val query = searchQuery.trim().lowercase()
      return apps
        .filter { app ->
          val matchesFilter =
            when (selectedFilter) {
              AppFilter.USER -> !app.isSystemApp
              AppFilter.SYSTEM_BLOAT -> app.isSystemApp
              AppFilter.ACTIVE ->
                (preferences.includeSystemApps || !app.isSystemApp) &&
                  !app.isStopped &&
                  app.isEnabled
              AppFilter.BLACKLISTED -> app.isBlacklisted
              AppFilter.DISABLED -> !app.isEnabled
              AppFilter.PROTECTED -> app.isProtectedByDefault
            }
          val matchesQuery =
            query.isEmpty() ||
              app.label.lowercase().contains(query) ||
              app.packageName.lowercase().contains(query)
          matchesFilter && matchesQuery
        }
        .let { list ->
          if (selectedFilter == AppFilter.SYSTEM_BLOAT) {
            list.sortedWith(
              compareBy<InstalledAppInfo> { it.isProtectedByDefault }
                .thenBy { !it.isEnabled }
                .thenBy { it.label.lowercase() }
            )
          } else {
            list
          }
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext
  private val repo = SweepPreferencesRepository(context)
  private val bridge = PrivilegeBridge(context)
  private val inventory = AppInventoryManager(context)
  private val junkScanner = StorageJunkScanner()

  private val telemetryFlow = MutableStateFlow(DeviceTelemetry())
  private val diagnosticsFlow = MutableStateFlow(PrivilegeDiagnostics())
  private val appsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  private val isLoadingInventoryFlow = MutableStateFlow(true)
  private val selectedToKillFlow = MutableStateFlow<Set<String>>(emptySet())
  private val searchQueryFlow = MutableStateFlow("")
  private val selectedFilterFlow = MutableStateFlow(AppFilter.USER)

  private val isScanningJunkFlow = MutableStateFlow(false)
  private val isCleaningJunkFlow = MutableStateFlow(false)
  private val hasScannedJunkOnceFlow = MutableStateFlow(false)
  private val junkItemsFlow = MutableStateFlow<List<JunkItem>>(emptyList())

  private val _events = MutableSharedFlow<String>(extraBufferCapacity = 10)
  val events = _events.asSharedFlow()

  private var autoPromptedShizuku = false
  private var killSelectionInitialized = false

  private data class CoreSnapshot(
    val loading: Boolean,
    val tel: DeviceTelemetry,
    val diag: PrivilegeDiagnostics,
    val prefs: SweepUserPreferences,
    val apps: List<InstalledAppInfo>,
  )

  private data class JunkSnapshot(
    val scanning: Boolean,
    val cleaning: Boolean,
    val scannedOnce: Boolean,
    val items: List<JunkItem>,
  )

  val uiState: StateFlow<DashboardUiState> =
    combine(
      combine(isLoadingInventoryFlow, telemetryFlow, diagnosticsFlow, repo.preferencesFlow, appsFlow) {
        loading,
        tel,
        diag,
        prefs,
        apps ->
        CoreSnapshot(loading, tel, diag, prefs, apps)
      },
      selectedToKillFlow,
      combine(searchQueryFlow, selectedFilterFlow) { q, f -> q to f },
      combine(isScanningJunkFlow, isCleaningJunkFlow, hasScannedJunkOnceFlow, junkItemsFlow) {
        scanning,
        cleaning,
        scannedOnce,
        items ->
        JunkSnapshot(scanning, cleaning, scannedOnce, items)
      },
    ) { core, killSel, (query, filter), junk ->
      val syncedApps =
        core.apps.map { app -> app.copy(isBlacklisted = app.packageName in core.prefs.blacklistedPackages) }
      DashboardUiState(
        isLoadingInventory = core.loading,
        telemetry = core.tel,
        diagnostics = core.diag,
        preferences = core.prefs,
        apps = syncedApps,
        selectedToKillPackages = killSel,
        searchQuery = query,
        selectedFilter = filter,
        isScanningJunk = junk.scanning,
        isCleaningJunk = junk.cleaning,
        hasScannedJunkOnce = junk.scannedOnce,
        junkItems = junk.items,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
      )

  init {
    viewModelScope.launch {
      repo.setSweepRunning(false)
      refreshAll()
    }
  }

  fun refreshAll() {
    viewModelScope.launch {
      isLoadingInventoryFlow.value = true
      val prefs = repo.getSnapshot()
      telemetryFlow.value = inventory.loadDeviceTelemetry()
      val diag = bridge.inspectPrivileges(prefs.preferredBackend)
      diagnosticsFlow.value = diag
      maybeAutoRequestShizuku(diag, prefs.preferredBackend)
      val loaded = inventory.loadInstalledApps(prefs.blacklistedPackages, diag.hasUsageStatsPerm)
      appsFlow.value = loaded
      if (!killSelectionInitialized) {
        killSelectionInitialized = true
        selectedToKillFlow.value =
          computeEligibleKillPackages(loaded, prefs).toSet()
      }
      isLoadingInventoryFlow.value = false
    }
  }

  fun refreshDiagnosticsOnly() {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      telemetryFlow.value = inventory.loadDeviceTelemetry()
      val diag = bridge.inspectPrivileges(prefs.preferredBackend)
      diagnosticsFlow.value = diag
      maybeAutoRequestShizuku(diag, prefs.preferredBackend)
      if (diag.hasUsageStatsPerm && appsFlow.value.none { it.cacheBytes > 0L }) {
        val loaded = inventory.loadInstalledApps(prefs.blacklistedPackages, diag.hasUsageStatsPerm)
        appsFlow.value = loaded
        if (!killSelectionInitialized) {
          killSelectionInitialized = true
          selectedToKillFlow.value = computeEligibleKillPackages(loaded, prefs).toSet()
        }
      }
    }
  }

  private fun computeEligibleKillPackages(
    apps: List<InstalledAppInfo>,
    prefs: SweepUserPreferences,
  ): List<String> =
    apps
      .filter { app ->
        val sysOk = prefs.includeSystemApps || !app.isSystemApp
        val essOk = !prefs.autoProtectEssential || !app.isProtectedByDefault
        sysOk && essOk && !app.isBlacklisted && !app.isStopped && app.isEnabled
      }
      .map { it.packageName }

  fun onShizukuPermissionResult(granted: Boolean) {
    refreshDiagnosticsOnly()
    _events.tryEmit(
      if (granted) "Shizuku permission granted." else "Shizuku permission denied."
    )
  }

  private fun maybeAutoRequestShizuku(
    diag: PrivilegeDiagnostics,
    preferredBackend: PreferredBackend,
  ) {
    if (autoPromptedShizuku) return
    val shizukuEligible =
      preferredBackend == PreferredBackend.AUTO || preferredBackend == PreferredBackend.SHIZUKU
    if (
      shizukuEligible &&
        !diag.hasAnyElevatedPrivilege &&
        diag.isShizukuRunning &&
        !diag.isShizukuPermissionGranted
    ) {
      autoPromptedShizuku = true
      runCatching {
        if (!Shizuku.isPreV11()) {
          Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
      }
    }
  }

  fun runSweep(actionType: SweepActionType) {
    executeSweepInternal(actionType = actionType, explicitStopPackages = null)
  }

  fun forceStopSelected() {
    val selected = uiState.value.effectiveSelectedToKill
    if (selected.isEmpty()) {
      _events.tryEmit("Select at least one background app to force stop.")
      return
    }
    executeSweepInternal(
      actionType = SweepActionType.FORCE_STOP,
      explicitStopPackages = selected,
    )
  }

  private fun executeSweepInternal(
    actionType: SweepActionType,
    explicitStopPackages: Set<String>?,
  ) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      if (prefs.isSweepRunning) return@launch

      repo.setSweepRunning(true)
      updateAllSysSweepWidgets(context)

      try {
        val diag = bridge.inspectPrivileges(prefs.preferredBackend)
        diagnosticsFlow.value = diag
        val currentApps =
          inventory.loadInstalledApps(prefs.blacklistedPackages, diag.hasUsageStatsPerm)
        appsFlow.value = currentApps

        val result =
          bridge.executeSweep(
            actionType = actionType,
            apps = currentApps,
            includeSystemApps = prefs.includeSystemApps,
            autoProtectEssential = prefs.autoProtectEssential,
            preferredBackend = prefs.preferredBackend,
            explicitStopPackages = explicitStopPackages,
          )
        repo.saveSweepResult(result)
        updateAllSysSweepWidgets(context)
        _events.tryEmit(result.summaryMessage)

        telemetryFlow.value = inventory.loadDeviceTelemetry()
        val updatedDiag = bridge.inspectPrivileges(prefs.preferredBackend)
        diagnosticsFlow.value = updatedDiag
        val refreshedApps =
          inventory.loadInstalledApps(prefs.blacklistedPackages, updatedDiag.hasUsageStatsPerm)
        appsFlow.value = refreshedApps
        if (explicitStopPackages != null) {
          selectedToKillFlow.value = selectedToKillFlow.value - explicitStopPackages
        }
      } finally {
        repo.setSweepRunning(false)
        updateAllSysSweepWidgets(context)
      }
    }
  }

  fun toggleSelectToKill(packageName: String) {
    val current = selectedToKillFlow.value
    selectedToKillFlow.value =
      if (packageName in current) current - packageName else current + packageName
  }

  fun selectAllToKill() {
    selectedToKillFlow.value = uiState.value.activeKillCandidates.map { it.packageName }.toSet()
  }

  fun clearAllToKill() {
    selectedToKillFlow.value = emptySet()
  }

  fun forceStopSingleApp(packageName: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      if (packageName in prefs.blacklistedPackages) {
        _events.tryEmit("$packageName is blacklisted and protected from Force Stop.")
        return@launch
      }
      val ok = bridge.forceStopSinglePackage(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Force-stopped $packageName" else "Failed to stop $packageName (check privilege)"
      )
      refreshAll()
    }
  }

  fun clearSingleAppCacheRootOnly(packageName: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.clearSinglePackageCacheRootOnly(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Cleared cache for $packageName"
        else "Per-app cache wipe requires Root (or use global Clear Cache)"
      )
      refreshAll()
    }
  }

  fun uninstallApp(
    packageName: String,
    label: String,
    isSystemApp: Boolean,
    onFallbackSystemIntent: (String) -> Unit,
  ) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.uninstallPackagePrivileged(packageName, prefs.preferredBackend)
      if (ok) {
        _events.tryEmit(
          if (isSystemApp) "Removed system bloatware: $label (for user 0)"
          else "Uninstalled $label"
        )
        refreshAll()
      } else if (!isSystemApp) {
        onFallbackSystemIntent(packageName)
      } else {
        _events.tryEmit(
          "System app removal requires Shizuku or Root (su) active."
        )
      }
    }
  }

  fun disableApp(packageName: String, label: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.disablePackagePrivileged(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Disabled $label"
        else "Disabling apps requires Shizuku, Root, or System Priv-App."
      )
      refreshAll()
    }
  }

  fun enableApp(packageName: String, label: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.enablePackagePrivileged(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Enabled $label"
        else "Enabling apps requires Shizuku, Root, or System Priv-App."
      )
      refreshAll()
    }
  }

  fun toggleBlacklist(packageName: String) {
    viewModelScope.launch {
      val wasBlacklisted = packageName in repo.getSnapshot().blacklistedPackages
      repo.toggleBlacklist(packageName)
      _events.tryEmit(
        if (wasBlacklisted) "Removed from Force-Stop Blacklist"
        else "Added to Blacklist (protected from Force Stop)"
      )
    }
  }

  fun scanStorageJunk() {
    viewModelScope.launch {
      if (isScanningJunkFlow.value || isCleaningJunkFlow.value) return@launch
      val prefs = repo.getSnapshot()
      val diag = bridge.inspectPrivileges(prefs.preferredBackend)
      diagnosticsFlow.value = diag
      if (!diag.hasAllFilesAccess) {
        _events.tryEmit("Grant All Files Access permission first to scan storage junk.")
        return@launch
      }
      isScanningJunkFlow.value = true
      try {
        val found = junkScanner.scan()
        junkItemsFlow.value = found
        hasScannedJunkOnceFlow.value = true
        val bytesText = FormatUtils.formatBytes(context, found.sumOf { it.sizeBytes })
        _events.tryEmit(
          if (found.isEmpty()) "Storage is clean! No junk files found."
          else "Found ${found.size} junk items ($bytesText)"
        )
      } finally {
        isScanningJunkFlow.value = false
      }
    }
  }

  fun toggleJunkItem(path: String) {
    junkItemsFlow.value =
      junkItemsFlow.value.map { item ->
        if (item.path == path) item.copy(isSelected = !item.isSelected) else item
      }
  }

  fun toggleJunkCategory(category: JunkCategory) {
    val itemsInCat = junkItemsFlow.value.filter { it.category == category }
    val allSelected = itemsInCat.isNotEmpty() && itemsInCat.all { it.isSelected }
    val targetSelect = !allSelected
    junkItemsFlow.value =
      junkItemsFlow.value.map { item ->
        if (item.category == category) item.copy(isSelected = targetSelect) else item
      }
  }

  fun selectAllJunk(selectAll: Boolean) {
    junkItemsFlow.value = junkItemsFlow.value.map { it.copy(isSelected = selectAll) }
  }

  fun cleanSelectedJunk() {
    viewModelScope.launch {
      if (isCleaningJunkFlow.value || isScanningJunkFlow.value) return@launch
      val selected = junkItemsFlow.value.filter { it.isSelected }
      if (selected.isEmpty()) {
        _events.tryEmit("No junk items selected to clean.")
        return@launch
      }
      isCleaningJunkFlow.value = true
      try {
        val (deletedCount, freedBytes) = junkScanner.cleanItems(selected)
        val updated = junkScanner.scan()
        junkItemsFlow.value = updated
        telemetryFlow.value = inventory.loadDeviceTelemetry()
        val freedText = FormatUtils.formatBytes(context, freedBytes)
        _events.tryEmit("Cleaned $deletedCount junk items ($freedText freed)")
      } finally {
        isCleaningJunkFlow.value = false
      }
    }
  }

  fun updateSearchQuery(query: String) {
    searchQueryFlow.value = query
  }

  fun selectFilter(filter: AppFilter) {
    selectedFilterFlow.value = filter
  }

  fun setPreferredBackend(backend: PreferredBackend) {
    viewModelScope.launch {
      repo.setPreferredBackend(backend)
      refreshDiagnosticsOnly()
    }
  }

  fun setIncludeSystemApps(include: Boolean) {
    viewModelScope.launch { repo.setIncludeSystemApps(include) }
  }

  fun setAutoProtectEssential(protect: Boolean) {
    viewModelScope.launch { repo.setAutoProtectEssential(protect) }
  }

  fun setWidgetPrimaryAction(action: WidgetPrimaryAction) {
    viewModelScope.launch {
      repo.setWidgetPrimaryAction(action)
      updateAllSysSweepWidgets(context)
    }
  }

  fun emitMessage(message: String) {
    _events.tryEmit(message)
  }

  fun requestShizukuPermission() {
    runCatching {
        if (!Shizuku.pingBinder()) {
          _events.tryEmit("Shizuku service is not running. Open Shizuku app and start it first.")
          return
        }
        if (Shizuku.isPreV11()) {
          _events.tryEmit("Please update Shizuku to v11 or newer.")
          return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
          refreshDiagnosticsOnly()
          _events.tryEmit("Shizuku permission is already granted.")
        } else {
          Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
      }
      .onFailure {
        _events.tryEmit("Could not contact Shizuku: ${it.localizedMessage ?: "Not installed"}")
      }
  }

  companion object {
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
  }
}
