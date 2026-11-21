package com.example.syssweep.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.BuildConfig
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.WidgetPrimaryAction
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.widget.MiniCacheWidget
import com.example.syssweep.widget.MiniCacheWidgetReceiver
import com.example.syssweep.widget.MiniDualWidget
import com.example.syssweep.widget.MiniDualWidgetReceiver
import com.example.syssweep.widget.MiniRamWidget
import com.example.syssweep.widget.MiniRamWidgetReceiver
import com.example.syssweep.widget.SysSweepWidget
import com.example.syssweep.widget.SysSweepWidgetReceiver
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  state: DashboardUiState,
  onSetPreferredBackend: (PreferredBackend) -> Unit,
  onSetIncludeSystemApps: (Boolean) -> Unit,
  onSetAutoProtectEssential: (Boolean) -> Unit,
  onSetWidgetAction: (WidgetPrimaryAction) -> Unit,
  onRequestShizuku: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val privAppXml = buildPrivAppWhitelistXml(BuildConfig.APPLICATION_ID)
  val diag = state.diagnostics
  val widgetDiag = remember(context) { inspectWidgetEnvironment(context) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Settings & Privileges",
          style = MaterialTheme.typography.headlineMedium,
        )
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Privilege Diagnostics",
                style = MaterialTheme.typography.titleMedium,
              )
            }

            DiagnosticStatusRow("System Priv-App Partition", diag.isSystemPartitionApp)
            DiagnosticStatusRow("CLEAR_APP_CACHE Permission", diag.hasClearCachePerm)
            DiagnosticStatusRow("FORCE_STOP_PACKAGES Permission", diag.hasForceStopPerm)
            DiagnosticStatusRow("DELETE_PACKAGES Permission", diag.hasDeletePackagesPerm)
            DiagnosticStatusRow("Usage Access (Cache Sizes)", diag.hasUsageStatsPerm)
            DiagnosticStatusRow("All Files Access (Junk Cleaner)", diag.hasAllFilesAccess)
            DiagnosticStatusRow("Root (su) Binary", diag.isRootAvailable)
            DiagnosticStatusRow(
              "Shizuku Bridge",
              diag.isShizukuRunning && diag.isShizukuPermissionGranted,
              pendingText =
                if (diag.isShizukuRunning) "Running (Not Granted)" else "Not Running",
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onRequestShizuku,
                modifier =
                  Modifier.weight(1f).heightIn(min = 48.dp).testTag("request_shizuku_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grant Shizuku")
              }
              OutlinedButton(
                onClick = onOpenUsageAccess,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text("Usage Access")
              }
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.SettingsApplications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = "Execution Backend & Safety",
                style = MaterialTheme.typography.titleMedium,
              )
            }

            PreferredBackend.entries.forEach { backend ->
              val label =
                when (backend) {
                  PreferredBackend.AUTO -> "Auto (System Priv-App → Root → Shizuku)"
                  PreferredBackend.SYSTEM_PRIV_APP -> "System Priv-App Only"
                  PreferredBackend.ROOT_SU -> "Root (su) Only"
                  PreferredBackend.SHIZUKU -> "Shizuku Only"
                }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = state.preferences.preferredBackend == backend,
                  onClick = { onSetPreferredBackend(backend) },
                  modifier = Modifier.testTag("backend_${backend.name.lowercase()}"),
                )
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Protect Essentials", style = MaterialTheme.typography.bodyLarge)
                Text(
                  "Never force-stop Launcher, Keyboard, Shizuku, or Core System",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.preferences.autoProtectEssential,
                onCheckedChange = onSetAutoProtectEssential,
                modifier = Modifier.testTag("toggle_auto_protect"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Include System Apps", style = MaterialTheme.typography.bodyLarge)
                Text(
                  "Show and target non-essential system packages",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.preferences.includeSystemApps,
                onCheckedChange = onSetIncludeSystemApps,
                modifier = Modifier.testTag("toggle_include_system"),
              )
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Home Screen Widgets & Highlight",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = Color(0xFF1A222C),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = EmeraldAccent,
                      modifier = Modifier.size(28.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "RAM icon preview",
                        tint = Color(0xFF052E16),
                        modifier = Modifier.padding(5.dp),
                      )
                    }
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(28.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = "Cache icon preview",
                        tint = Color.White,
                        modifier = Modifier.padding(5.dp),
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = {
                    scope.launch {
                      pinCleanMasterWidget(
                        context = context,
                        receiverClass = MiniDualWidgetReceiver::class.java,
                        previewWidget = MiniDualWidget(),
                        previewSize = DpSize(128.dp, 64.dp),
                        widgetLabel = "Mini Dual Icons (2×1)",
                        onNotify = onNotify,
                      )
                    }
                  },
                  shape = RoundedCornerShape(999.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  modifier = Modifier.heightIn(min = 42.dp).testTag("pin_mini_dual_widget"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add Mini Bar (2×1)", style = MaterialTheme.typography.labelMedium)
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = {
                  scope.launch {
                    pinCleanMasterWidget(
                      context = context,
                      receiverClass = MiniRamWidgetReceiver::class.java,
                      previewWidget = MiniRamWidget(),
                      previewSize = DpSize(64.dp, 64.dp),
                      widgetLabel = "RAM Clean (1×1)",
                      onNotify = onNotify,
                    )
                  }
                },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("pin_mini_ram_widget"),
              ) {
                Text("RAM Icon (1×1)", style = MaterialTheme.typography.labelMedium, maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  scope.launch {
                    pinCleanMasterWidget(
                      context = context,
                      receiverClass = MiniCacheWidgetReceiver::class.java,
                      previewWidget = MiniCacheWidget(),
                      previewSize = DpSize(64.dp, 64.dp),
                      widgetLabel = "Cache Clean (1×1)",
                      onNotify = onNotify,
                    )
                  }
                },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("pin_mini_cache_widget"),
              ) {
                Text("Cache Icon (1×1)", style = MaterialTheme.typography.labelMedium, maxLines = 1)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Registered: ${widgetDiag.registeredCount}/4 • Launcher: ${widgetDiag.launcherName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = {
                  scope.launch {
                    pinCleanMasterWidget(
                      context = context,
                      receiverClass = SysSweepWidgetReceiver::class.java,
                      previewWidget = SysSweepWidget(),
                      previewSize = DpSize(250.dp, 110.dp),
                      widgetLabel = "Clean Master Quick Boost",
                      onNotify = onNotify,
                    )
                  }
                },
                modifier = Modifier.testTag("pin_full_widget"),
              ) {
                Text("Full Bar (4×2)")
              }
            }

            if (widgetDiag.launcherPackage != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "If missing in Home picker: unlock layout or clear Launcher cache",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
                TextButton(
                  onClick = {
                    runCatching {
                      context.startActivity(
                        Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:${widgetDiag.launcherPackage}"),
                        )
                      )
                    }
                  },
                  modifier = Modifier.testTag("open_launcher_info_button"),
                ) {
                  Text("Launcher Info")
                }
              }
            }

            WidgetPrimaryAction.entries.forEach { action ->
              val label =
                when (action) {
                  WidgetPrimaryAction.BOOST_BOTH -> "Boost (Clear Cache + Force Stop)"
                  WidgetPrimaryAction.CLEAR_CACHE -> "Cache (Clear All Cache)"
                  WidgetPrimaryAction.FORCE_STOP -> "Stop (Force Stop Non-Blacklisted)"
                }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = state.preferences.widgetPrimaryAction == action,
                  onClick = { onSetWidgetAction(action) },
                  modifier = Modifier.testTag("widget_action_${action.name.lowercase()}"),
                )
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Recovery ZIP / Priv-App XML Reference",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text =
                "Place APK at /system/priv-app/CleanMaster/CleanMaster.apk (or /product/priv-app/CleanMaster/CleanMaster.apk) and whitelist XML at matching /etc/permissions/privapp-permissions-cleanmaster.xml:",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = privAppXml,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
              )
            }

            OutlinedButton(
              onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("privapp-permissions", privAppXml))
                onNotify("Copied privapp-permissions XML to clipboard")
              },
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("copy_privapp_xml_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Copy Whitelist XML")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun DiagnosticStatusRow(
  label: String,
  granted: Boolean,
  pendingText: String = "Not Granted",
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
        contentDescription = null,
        tint = if (granted) StatusSuccess else StatusDanger.copy(alpha = 0.85f),
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = if (granted) "Active" else pendingText,
        style = MaterialTheme.typography.labelMedium,
        color = if (granted) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private data class WidgetEnvDiagnostics(
  val registeredCount: Int,
  val launcherName: String,
  val launcherPackage: String?,
)

private fun inspectWidgetEnvironment(context: Context): WidgetEnvDiagnostics {
  val registeredCount =
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        manager.getInstalledProvidersForPackage(context.packageName, null).size
      }
      .getOrDefault(0)

  val homeResolve =
    runCatching {
        val homeIntent =
          Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
      }
      .getOrNull()

  val launcherPkg = homeResolve?.activityInfo?.packageName
  val launcherLabel =
    homeResolve?.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
      ?: launcherPkg
      ?: "Default"

  return WidgetEnvDiagnostics(
    registeredCount = registeredCount,
    launcherName = launcherLabel,
    launcherPackage = launcherPkg,
  )
}

private suspend fun <T : GlanceAppWidgetReceiver> pinCleanMasterWidget(
  context: Context,
  receiverClass: Class<T>,
  previewWidget: GlanceAppWidget,
  previewSize: DpSize,
  widgetLabel: String,
  onNotify: (String) -> Unit,
) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val provider = ComponentName(context, receiverClass)
    if (manager != null && manager.isRequestPinAppWidgetSupported) {
      val glancePinned =
        runCatching {
            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
              receiver = receiverClass,
              preview = previewWidget,
              previewState = previewSize,
            )
          }
          .getOrDefault(false)

      if (glancePinned) {
        onNotify("Confirm adding '$widgetLabel' on your Home Screen prompt.")
        return
      }

      val legacyPinned =
        runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
      if (legacyPinned) {
        onNotify("Confirm adding '$widgetLabel' on your Home Screen prompt.")
        return
      }

      val isRegistered =
        runCatching {
            manager.getInstalledProvidersForPackage(context.packageName, null).any {
              it.provider == provider
            }
          }
          .getOrDefault(false)

      if (!isRegistered) {
        onNotify("Widget provider not registered yet. Clear Launcher cache or reboot device.")
      } else {
        onNotify(
          "Launcher declined auto-pin (check Home layout lock / Launcher cache). Try Home → Widgets → Clean Master."
        )
      }
      return
    }
  }
  onNotify("Long-press your Home Screen → Widgets → choose '$widgetLabel'.")
}

private fun buildPrivAppWhitelistXml(packageName: String): String =
  """
  <?xml version="1.0" encoding="utf-8"?>
  <permissions>
      <privapp-permissions package="$packageName">
          <permission name="android.permission.CLEAR_APP_CACHE" />
          <permission name="android.permission.DELETE_CACHE_FILES" />
          <permission name="android.permission.FORCE_STOP_PACKAGES" />
          <permission name="android.permission.DELETE_PACKAGES" />
          <permission name="android.permission.CHANGE_COMPONENT_ENABLED_STATE" />
          <permission name="android.permission.PACKAGE_USAGE_STATS" />
      </privapp-permissions>
  </permissions>
  """.trimIndent()
