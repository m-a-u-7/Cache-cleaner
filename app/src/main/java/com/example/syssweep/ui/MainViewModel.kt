package com.example.syssweep.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.PrivilegeDiagnostics
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.data.SweepPreferencesRepository
import com.example.syssweep.data.SweepUserPreferences
import com.example.syssweep.data.WidgetPrimaryAction
import com.example.syssweep.engine.AppInventoryManager
import com.example.syssweep.engine.PrivilegeBridge
import com.example.syssweep.widget.SysSweepWidget
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
  ALL_USER("User Apps"),
  HAS_CACHE("Has Cache"),
  ACTIVE("Active"),
  WHITELISTED("Protected"),
}

data class DashboardUiState(
  val isLoadingInventory: Boolean = true,
  val diagnostics: PrivilegeDiagnostics = PrivilegeDiagnostics(),
  val preferences: SweepUserPreferences = SweepUserPreferences(),
  val apps: List<InstalledAppInfo> = emptyList(),
  val searchQuery: String = "",
  val selectedFilter: AppFilter = AppFilter.ALL_USER,
) {
  val totalUserCacheBytes: Long
    get() =
      apps
        .filter { preferences.includeSystemApps || !it.isSystemApp }
        .sumOf { it.cacheBytes }

  val activeTargetAppsCount: Int
    get() =
      apps.count { app ->
        val sysOk = preferences.includeSystemApps || !app.isSystemApp
        val essOk = !preferences.autoProtectEssential || !app.isProtectedByDefault
        sysOk && essOk && !app.isWhitelisted && !app.isStopped
      }

  val totalTargetAppsCount: Int
    get() =
      apps.count { app ->
        val sysOk = preferences.includeSystemApps || !app.isSystemApp
        val essOk = !preferences.autoProtectEssential || !app.isProtectedByDefault
        sysOk && essOk && !app.isWhitelisted
      }

  val filteredApps: List<InstalledAppInfo>
    get() {
      val query = searchQuery.trim().lowercase()
      return apps.filter { app ->
        val matchesSystem = preferences.includeSystemApps || !app.isSystemApp
        val matchesFilter =
          when (selectedFilter) {
            AppFilter.ALL_USER -> matchesSystem
            AppFilter.HAS_CACHE -> matchesSystem && app.cacheBytes > 0L
            AppFilter.ACTIVE -> matchesSystem && !app.isStopped
            AppFilter.WHITELISTED -> app.isWhitelisted || app.isProtectedByDefault
          }
        val matchesQuery =
          query.isEmpty() ||
            app.label.lowercase().contains(query) ||
            app.packageName.lowercase().contains(query)
        matchesFilter && matchesQuery
      }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext
  private val repo = SweepPreferencesRepository(context)
  private val bridge = PrivilegeBridge(context)
  private val inventory = AppInventoryManager(context)

  private val diagnosticsFlow = MutableStateFlow(PrivilegeDiagnostics())
  private val appsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  private val isLoadingInventoryFlow = MutableStateFlow(true)
  private val searchQueryFlow = MutableStateFlow("")
  private val selectedFilterFlow = MutableStateFlow(AppFilter.ALL_USER)

  private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val events = _events.asSharedFlow()

  val uiState: StateFlow<DashboardUiState> =
    combine(
      isLoadingInventoryFlow,
      diagnosticsFlow,
      repo.preferencesFlow,
      appsFlow,
      combine(searchQueryFlow, selectedFilterFlow) { q, f -> q to f },
    ) { loading, diag, prefs, apps, (query, filter) ->
      val syncedApps =
        apps.map { app -> app.copy(isWhitelisted = app.packageName in prefs.whitelistedPackages) }
      DashboardUiState(
        isLoadingInventory = loading,
        diagnostics = diag,
        preferences = prefs,
        apps = syncedApps,
        searchQuery = query,
        selectedFilter = filter,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
      )

  init {
    refreshAll()
  }

  fun refreshAll() {
    viewModelScope.launch {
      isLoadingInventoryFlow.value = true
      val prefs = repo.getSnapshot()
      val diag = bridge.inspectPrivileges(prefs.preferredBackend)
      diagnosticsFlow.value = diag
      appsFlow.value = inventory.loadInstalledApps(prefs.whitelistedPackages, diag.hasUsageStatsPerm)
      isLoadingInventoryFlow.value = false
    }
  }

  fun refreshDiagnosticsOnly() {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val diag = bridge.inspectPrivileges(prefs.preferredBackend)
      diagnosticsFlow.value = diag
      if (diag.hasUsageStatsPerm && appsFlow.value.none { it.cacheBytes > 0L }) {
        appsFlow.value =
          inventory.loadInstalledApps(prefs.whitelistedPackages, diag.hasUsageStatsPerm)
      }
    }
  }

  fun runSweep(actionType: SweepActionType) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      if (prefs.isSweepRunning) return@launch

      repo.setSweepRunning(true)
      runCatching { SysSweepWidget().updateAll(context) }

      try {
        val diag = bridge.inspectPrivileges(prefs.preferredBackend)
        diagnosticsFlow.value = diag
        val currentApps =
          inventory.loadInstalledApps(prefs.whitelistedPackages, diag.hasUsageStatsPerm)
        appsFlow.value = currentApps

        val result =
          bridge.executeSweep(
            actionType = actionType,
            apps = currentApps,
            includeSystemApps = prefs.includeSystemApps,
            autoProtectEssential = prefs.autoProtectEssential,
            preferredBackend = prefs.preferredBackend,
          )
        repo.saveSweepResult(result)
        runCatching { SysSweepWidget().updateAll(context) }
        _events.tryEmit(result.summaryMessage)

        // Refresh inventory after sweep to reflect updated stopped flags & cache sizes
        val updatedDiag = bridge.inspectPrivileges(prefs.preferredBackend)
        appsFlow.value =
          inventory.loadInstalledApps(prefs.whitelistedPackages, updatedDiag.hasUsageStatsPerm)
      } finally {
        repo.setSweepRunning(false)
        runCatching { SysSweepWidget().updateAll(context) }
      }
    }
  }

  fun forceStopSingleApp(packageName: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.forceStopSinglePackage(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Force-stopped $packageName" else "Failed to stop $packageName (check privilege)"
      )
      refreshAll()
    }
  }

  fun clearSingleAppCache(packageName: String) {
    viewModelScope.launch {
      val prefs = repo.getSnapshot()
      val ok = bridge.clearSinglePackageCache(packageName, prefs.preferredBackend)
      _events.tryEmit(
        if (ok) "Cache sweep completed for $packageName"
        else "Cache clear unavailable in current privilege mode"
      )
      refreshAll()
    }
  }

  fun toggleWhitelist(packageName: String) {
    viewModelScope.launch { repo.toggleWhitelist(packageName) }
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
      runCatching { SysSweepWidget().updateAll(context) }
    }
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
