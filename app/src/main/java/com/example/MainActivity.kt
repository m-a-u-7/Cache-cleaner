package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import kotlinx.coroutines.launch
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
import com.example.syssweep.ui.screens.WidgetSettingsScreen
import com.example.ui.theme.SysSweepTheme
import rikka.shizuku.Shizuku

private enum class MainDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DASHBOARD("dashboard", "Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
  APPS("apps", "Apps", Icons.Rounded.Apps, Icons.Outlined.Apps),
  CONFIG("widget_settings", "Widget & Config", Icons.Rounded.Tune, Icons.Outlined.Tune),
}

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  private val shizukuPermissionListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == MainViewModel.SHIZUKU_PERMISSION_REQUEST_CODE) {
        viewModel.refreshDiagnosticsOnly()
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

  // Request POST_NOTIFICATIONS on Android 13+ so ForegroundService progress/result notifications show cleanly
  val notificationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

  BackHandler(enabled = currentDest != MainDestination.DASHBOARD) {
    currentDest = MainDestination.DASHBOARD
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            MainDestination.entries.forEach { dest ->
              val selected = currentDest == dest
              NavigationBarItem(
                selected = selected,
                onClick = { currentDest = dest },
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
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            MainDestination.entries.forEach { dest ->
              val selected = currentDest == dest
              NavigationRailItem(
                selected = selected,
                onClick = { currentDest = dest },
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
          when (currentDest) {
            MainDestination.DASHBOARD ->
              DashboardScreen(
                state = uiState,
                onSweep = { action -> viewModel.runSweep(action) },
                onRefresh = { viewModel.refreshAll() },
                onRequestShizuku = { viewModel.requestShizukuPermission() },
                onOpenUsageAccess = {
                  runCatching {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                  }
                },
                onNavigateToConfig = { currentDest = MainDestination.CONFIG },
              )

            MainDestination.APPS ->
              AppsScreen(
                state = uiState,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSelectFilter = viewModel::selectFilter,
                onToggleWhitelist = viewModel::toggleWhitelist,
                onForceStopSingle = viewModel::forceStopSingleApp,
                onClearCacheSingle = viewModel::clearSingleAppCache,
              )

            MainDestination.CONFIG ->
              WidgetSettingsScreen(
                state = uiState,
                onSetPreferredBackend = viewModel::setPreferredBackend,
                onSetIncludeSystemApps = viewModel::setIncludeSystemApps,
                onSetAutoProtectEssential = viewModel::setAutoProtectEssential,
                onSetWidgetAction = viewModel::setWidgetPrimaryAction,
                onNotify = { msg ->
                  scope.launch { snackbarHostState.showSnackbar(msg) }
                },
              )
          }
        }
      }
    }
  }
}
