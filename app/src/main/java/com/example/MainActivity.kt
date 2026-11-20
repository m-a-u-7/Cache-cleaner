package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.syssweep.ui.MainViewModel
import com.example.syssweep.ui.screens.AppsScreen
import com.example.syssweep.ui.screens.DashboardScreen
import com.example.syssweep.ui.screens.SettingsScreen
import com.example.syssweep.ui.screens.StorageCleanerScreen
import com.example.ui.theme.SysSweepTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private enum class MainDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DASHBOARD("dashboard", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
  MANAGER("manager", "Apps", Icons.Rounded.Build, Icons.Outlined.Build),
  SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  private val shizukuPermissionListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == MainViewModel.SHIZUKU_PERMISSION_REQUEST_CODE) {
        viewModel.onShizukuPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
      }
    }

  private val shizukuBinderListener =
    Shizuku.OnBinderReceivedListener { viewModel.refreshDiagnosticsOnly() }

  private val shizukuDeadListener =
    Shizuku.OnBinderDeadListener { viewModel.refreshDiagnosticsOnly() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    runCatching {
      Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
      Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
      Shizuku.addBinderDeadListener(shizukuDeadListener)
    }

    setContent {
      SysSweepTheme {
        SysSweepRoot(viewModel = viewModel)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshDiagnosticsOnly()
  }

  override fun onDestroy() {
    runCatching {
      Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
      Shizuku.removeBinderReceivedListener(shizukuBinderListener)
      Shizuku.removeBinderDeadListener(shizukuDeadListener)
    }
    super.onDestroy()
  }
}

@Composable
private fun SysSweepRoot(viewModel: MainViewModel) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var currentDest by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD) }
  var showStorageCleaner by rememberSaveable { mutableStateOf(false) }

  val notificationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  val openAppInfo: (String) -> Unit = { pkg ->
    runCatching {
      context.startActivity(
        Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:$pkg"),
        )
      )
    }
  }

  val launchSystemUninstallIntent: (String) -> Unit = { pkg ->
    runCatching {
      @Suppress("DEPRECATION")
      context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }
  }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
  }

  BackHandler(enabled = showStorageCleaner || currentDest != MainDestination.DASHBOARD) {
    if (showStorageCleaner) {
      showStorageCleaner = false
    } else {
      currentDest = MainDestination.DASHBOARD
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            MainDestination.entries.forEach { dest ->
              val selected = !showStorageCleaner && currentDest == dest
              NavigationBarItem(
                selected = selected,
                onClick = {
                  showStorageCleaner = false
                  currentDest = dest
                },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = { Text(dest.label, maxLines = 1) },
                alwaysShowLabel = true,
                colors =
                  NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                modifier = Modifier.testTag("nav_${dest.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            MainDestination.entries.forEach { dest ->
              val selected = !showStorageCleaner && currentDest == dest
              NavigationRailItem(
                selected = selected,
                onClick = {
                  showStorageCleaner = false
                  currentDest = dest
                },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag("nav_${dest.route}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          if (showStorageCleaner) {
            StorageCleanerScreen(
              state = uiState,
              onScanJunk = viewModel::scanStorageJunk,
              onToggleJunkItem = viewModel::toggleJunkItem,
              onToggleJunkCategory = viewModel::toggleJunkCategory,
              onSelectAllJunk = viewModel::selectAllJunk,
              onCleanSelectedJunk = viewModel::cleanSelectedJunk,
              onRefreshPermission = viewModel::refreshDiagnosticsOnly,
              onBack = { showStorageCleaner = false },
            )
          } else {
            when (currentDest) {
              MainDestination.DASHBOARD ->
                DashboardScreen(
                  state = uiState,
                  onSweep = { action -> viewModel.runSweep(action) },
                  onToggleSelectToKill = viewModel::toggleSelectToKill,
                  onSelectAllToKill = viewModel::selectAllToKill,
                  onClearAllToKill = viewModel::clearAllToKill,
                  onForceStopSelected = viewModel::forceStopSelected,
                  onForceStopSingle = viewModel::forceStopSingleApp,
                  onClearSingleCacheRootOnly = viewModel::clearSingleAppCacheRootOnly,
                  onOpenAppInfo = openAppInfo,
                  onRefresh = { viewModel.refreshAll() },
                  onRequestShizuku = { viewModel.requestShizukuPermission() },
                  onOpenUsageAccess = {
                    runCatching {
                      context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                  },
                  onOpenManager = { filter ->
                    viewModel.selectFilter(filter)
                    currentDest = MainDestination.MANAGER
                  },
                  onOpenStorageCleaner = { showStorageCleaner = true },
                )

              MainDestination.MANAGER ->
                AppsScreen(
                  state = uiState,
                  onSearchQueryChange = viewModel::updateSearchQuery,
                  onSelectFilter = viewModel::selectFilter,
                  onToggleBlacklist = viewModel::toggleBlacklist,
                  onForceStopSingle = viewModel::forceStopSingleApp,
                  onClearCacheSingleRootOnly = viewModel::clearSingleAppCacheRootOnly,
                  onOpenAppInfo = openAppInfo,
                  onUninstallApp = { app ->
                    viewModel.uninstallApp(
                      packageName = app.packageName,
                      label = app.label,
                      isSystemApp = app.isSystemApp,
                      onFallbackSystemIntent = launchSystemUninstallIntent,
                    )
                  },
                  onDisableApp = { app -> viewModel.disableApp(app.packageName, app.label) },
                  onEnableApp = { app -> viewModel.enableApp(app.packageName, app.label) },
                )

              MainDestination.SETTINGS ->
                SettingsScreen(
                  state = uiState,
                  onSetPreferredBackend = viewModel::setPreferredBackend,
                  onSetIncludeSystemApps = viewModel::setIncludeSystemApps,
                  onSetAutoProtectEssential = viewModel::setAutoProtectEssential,
                  onSetWidgetAction = viewModel::setWidgetPrimaryAction,
                  onRequestShizuku = { viewModel.requestShizukuPermission() },
                  onOpenUsageAccess = {
                    runCatching {
                      context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                  },
                  onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                )
            }
          }
        }
      }
    }
  }
}
