package com.example.syssweep.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SettingsApplications
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.syssweep.data.PreferredBackend
import com.example.syssweep.data.WidgetPrimaryAction
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.widget.SysSweepWidgetReceiver

@Composable
fun WidgetSettingsScreen(
  state: DashboardUiState,
  onSetPreferredBackend: (PreferredBackend) -> Unit,
  onSetIncludeSystemApps: (Boolean) -> Unit,
  onSetAutoProtectEssential: (Boolean) -> Unit,
  onSetWidgetAction: (WidgetPrimaryAction) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val privAppXml = buildPrivAppWhitelistXml(BuildConfig.APPLICATION_ID)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Widget & Configuration",
          style = MaterialTheme.typography.headlineMedium,
        )
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                text = "Home Screen Widget",
                style = MaterialTheme.typography.titleMedium,
              )
            }

            Text(
              text =
                "The SysSweep Glance widget gives one-tap Cache, Force Stop, and Boost buttons right on your launcher.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  val mgr = context.getSystemService(AppWidgetManager::class.java)
                  val provider = ComponentName(context, SysSweepWidgetReceiver::class.java)
                  if (mgr != null && mgr.isRequestPinAppWidgetSupported) {
                    mgr.requestPinAppWidget(provider, null, null)
                  } else {
                    onNotify("Add the SysSweep widget from your launcher's widget picker.")
                  }
                } else {
                  onNotify("Add the SysSweep widget from your launcher's widget picker.")
                }
              },
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pin_widget_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Pin Widget to Home Screen")
            }

            Text(
              text = "Default Widget Action",
              style = MaterialTheme.typography.labelLarge,
            )

            WidgetPrimaryAction.entries.forEach { action ->
              val label =
                when (action) {
                  WidgetPrimaryAction.BOOST_BOTH -> "Boost & Sweep All (Cache + Force Stop)"
                  WidgetPrimaryAction.CLEAR_CACHE -> "Clear All Cache Only"
                  WidgetPrimaryAction.FORCE_STOP -> "Force Stop All Only"
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
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Recovery ZIP / Priv-App XML Reference",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text =
                "Place APK at /system/priv-app/SysSweep/SysSweep.apk (or /product/priv-app/SysSweep/SysSweep.apk) and whitelist XML at matching /etc/permissions/privapp-permissions-syssweep.xml:",
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

private fun buildPrivAppWhitelistXml(packageName: String): String =
  """
  <?xml version="1.0" encoding="utf-8"?>
  <permissions>
      <privapp-permissions package="$packageName">
          <permission name="android.permission.CLEAR_APP_CACHE" />
          <permission name="android.permission.DELETE_CACHE_FILES" />
          <permission name="android.permission.FORCE_STOP_PACKAGES" />
          <permission name="android.permission.PACKAGE_USAGE_STATS" />
      </privapp-permissions>
  </permissions>
  """.trimIndent()
