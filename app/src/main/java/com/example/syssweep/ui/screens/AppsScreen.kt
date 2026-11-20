package com.example.syssweep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.ui.AppFilter
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.util.FormatUtils
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusWarning

private enum class ConfirmAppActionType {
  UNINSTALL,
  DISABLE,
}

private data class PendingAppConfirmation(
  val app: InstalledAppInfo,
  val action: ConfirmAppActionType,
)

@Composable
fun AppsScreen(
  state: DashboardUiState,
  onSearchQueryChange: (String) -> Unit,
  onSelectFilter: (AppFilter) -> Unit,
  onToggleBlacklist: (String) -> Unit,
  onForceStopSingle: (String) -> Unit,
  onClearCacheSingleRootOnly: (String) -> Unit,
  onOpenAppInfo: (String) -> Unit,
  onUninstallApp: (InstalledAppInfo) -> Unit,
  onDisableApp: (InstalledAppInfo) -> Unit,
  onEnableApp: (InstalledAppInfo) -> Unit,
  modifier: Modifier = Modifier,
) {
  val filtered = state.filteredApps
  var pendingConfirmation by remember { mutableStateOf<PendingAppConfirmation?>(null) }

  pendingConfirmation?.let { pending ->
    val isSystem = pending.app.isSystemApp
    val isUninstall = pending.action == ConfirmAppActionType.UNINSTALL
    AlertDialog(
      onDismissRequest = { pendingConfirmation = null },
      title = {
        Text(
          text =
            when {
              isUninstall && isSystem -> "Remove System Bloatware?"
              isUninstall -> "Uninstall App?"
              else -> "Disable App?"
            }
        )
      },
      text = {
        Text(
          text =
            when {
              isUninstall && isSystem ->
                "Remove '${pending.app.label}' (${pending.app.packageName}) for current user (pm uninstall --user 0)?\n\nCaution: Only remove known bloatware. Removing required vendor services can cause glitches."
              isUninstall ->
                "Uninstall '${pending.app.label}' (${pending.app.packageName}) from your device?"
              else ->
                "Disable '${pending.app.label}' (${pending.app.packageName})? It will stay dormant and won't run in the background until you re-enable it."
            }
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val target = pending.app
            val action = pending.action
            pendingConfirmation = null
            if (action == ConfirmAppActionType.UNINSTALL) {
              onUninstallApp(target)
            } else {
              onDisableApp(target)
            }
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (isUninstall) StatusDanger else StatusWarning,
              contentColor = if (isUninstall) Color.White else Color.Black,
            ),
          modifier = Modifier.testTag("confirm_app_action_button"),
        ) {
          Text(if (isUninstall) "Uninstall" else "Disable", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingConfirmation = null }) { Text("Cancel") }
      },
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "App & Bloatware Manager",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            "Manage Force-Stop Blacklist (${state.blacklistedCount}) or disable/uninstall bloatware with Shizuku/Root/Priv-App.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search app name or package ID") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().testTag("app_search_field"),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(AppFilter.entries) { filter ->
            val label =
              when (filter) {
                AppFilter.BLACKLISTED -> "${filter.label} (${state.blacklistedCount})"
                AppFilter.DISABLED -> "${filter.label} (${state.disabledCount})"
                else -> filter.label
              }
            FilterChip(
              selected = state.selectedFilter == filter,
              onClick = { onSelectFilter(filter) },
              label = { Text(label) },
              modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
            )
          }
        }
      }

      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Apps,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = "No matching apps found",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Try adjusting your search query or filter chip.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(items = filtered, key = { it.packageName }) { app ->
            AppPackageRow(
              app = app,
              canDeleteSingleCache = state.canDeleteSingleCache,
              canPrivilegedAppManage = state.canPrivilegedAppManage,
              canPrivilegedUninstall = state.canPrivilegedUninstall,
              onToggleBlacklist = { onToggleBlacklist(app.packageName) },
              onForceStop = { onForceStopSingle(app.packageName) },
              onClearCacheRootOnly = { onClearCacheSingleRootOnly(app.packageName) },
              onOpenAppInfo = { onOpenAppInfo(app.packageName) },
              onRequestUninstall = {
                pendingConfirmation = PendingAppConfirmation(app, ConfirmAppActionType.UNINSTALL)
              },
              onRequestDisable = {
                pendingConfirmation = PendingAppConfirmation(app, ConfirmAppActionType.DISABLE)
              },
              onEnable = { onEnableApp(app) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPackageRow(
  app: InstalledAppInfo,
  canDeleteSingleCache: Boolean,
  canPrivilegedAppManage: Boolean,
  canPrivilegedUninstall: Boolean,
  onToggleBlacklist: () -> Unit,
  onForceStop: () -> Unit,
  onClearCacheRootOnly: () -> Unit,
  onOpenAppInfo: () -> Unit,
  onRequestUninstall: () -> Unit,
  onRequestDisable: () -> Unit,
  onEnable: () -> Unit,
) {
  val context = LocalContext.current
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        1.dp,
        when {
          !app.isEnabled -> StatusDanger.copy(alpha = 0.45f)
          app.isBlacklisted -> StatusWarning.copy(alpha = 0.55f)
          app.isProtectedByDefault -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
          else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        },
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AppMiniIcon(
        packageName = app.packageName,
        label = app.label,
        modifier = Modifier.size(42.dp),
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = app.label,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = app.packageName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (app.cacheBytes > 0L) {
            MiniBadge(
              text = FormatUtils.formatBytes(context, app.cacheBytes),
              color = CyanSecondary,
            )
          }
          if (!app.isEnabled) {
            MiniBadge(text = "Disabled", color = StatusDanger)
          } else {
            MiniBadge(
              text = if (app.isStopped) "Stopped" else "Running",
              color =
                if (app.isStopped) MaterialTheme.colorScheme.onSurfaceVariant else EmeraldAccent,
            )
          }
          if (app.isSystemApp) {
            MiniBadge(text = "System", color = MaterialTheme.colorScheme.secondary)
          }
          if (app.isBlacklisted) {
            MiniBadge(text = "Blacklisted", color = StatusWarning)
          } else if (app.isProtectedByDefault && app.protectionReason != null) {
            MiniBadge(text = app.protectionReason, color = MaterialTheme.colorScheme.primary)
          }
        }
      }

      if (app.isProtectedByDefault) {
        Icon(
          imageVector = Icons.Rounded.Shield,
          contentDescription = "Protected core system package",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Switch(
            checked = app.isBlacklisted,
            onCheckedChange = { onToggleBlacklist() },
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = StatusWarning,
              ),
            modifier = Modifier.testTag("blacklist_toggle_${app.packageName}"),
          )
          Text(
            text = if (app.isBlacklisted) "Blacklist" else "Normal",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (app.isBlacklisted) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.testTag("app_menu_${app.packageName}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "Actions for ${app.label}",
          )
        }
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
        ) {
          if (!app.isProtectedByDefault) {
            DropdownMenuItem(
              text = {
                Text(if (app.isBlacklisted) "Remove from Blacklist" else "Add to Blacklist")
              },
              leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onToggleBlacklist()
              },
            )
          }
          if (app.isEnabled && !app.isBlacklisted) {
            DropdownMenuItem(
              text = { Text("Force Stop Now") },
              leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onForceStop()
              },
            )
          }
          if (canDeleteSingleCache) {
            DropdownMenuItem(
              text = { Text("Clear App Cache (Root)") },
              leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onClearCacheRootOnly()
              },
            )
          } else {
            DropdownMenuItem(
              text = { Text("Open System App Info") },
              leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
              },
              onClick = {
                menuExpanded = false
                onOpenAppInfo()
              },
            )
          }

          if (!app.isProtectedByDefault) {
            if (app.isEnabled && canPrivilegedAppManage) {
              DropdownMenuItem(
                text = { Text("Disable App") },
                leadingIcon = {
                  Icon(
                    Icons.Rounded.PauseCircleOutline,
                    contentDescription = null,
                    tint = StatusWarning,
                  )
                },
                onClick = {
                  menuExpanded = false
                  onRequestDisable()
                },
              )
            } else if (!app.isEnabled && canPrivilegedAppManage) {
              DropdownMenuItem(
                text = { Text("Enable App") },
                leadingIcon = {
                  Icon(
                    Icons.Rounded.CheckCircleOutline,
                    contentDescription = null,
                    tint = EmeraldAccent,
                  )
                },
                onClick = {
                  menuExpanded = false
                  onEnable()
                },
              )
            }

            if (!app.isSystemApp || canPrivilegedUninstall) {
              DropdownMenuItem(
                text = {
                  Text(
                    text = if (app.isSystemApp) "Uninstall Bloatware" else "Uninstall App",
                    color = StatusDanger,
                  )
                },
                leadingIcon = {
                  Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = StatusDanger,
                  )
                },
                onClick = {
                  menuExpanded = false
                  onRequestUninstall()
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = color.copy(alpha = 0.14f),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
