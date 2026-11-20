package com.example.syssweep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.ui.AppFilter
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.util.FormatUtils
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(
  state: DashboardUiState,
  onSearchQueryChange: (String) -> Unit,
  onSelectFilter: (AppFilter) -> Unit,
  onToggleWhitelist: (String) -> Unit,
  onForceStopSingle: (String) -> Unit,
  onClearCacheSingle: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val filtered = state.filteredApps

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Apps & Exclusions",
          style = MaterialTheme.typography.headlineMedium,
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
            FilterChip(
              selected = state.selectedFilter == filter,
              onClick = { onSelectFilter(filter) },
              label = { Text(filter.label) },
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
              onToggleWhitelist = { onToggleWhitelist(app.packageName) },
              onForceStop = { onForceStopSingle(app.packageName) },
              onClearCache = { onClearCacheSingle(app.packageName) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AppPackageRow(
  app: InstalledAppInfo,
  onToggleWhitelist: () -> Unit,
  onForceStop: () -> Unit,
  onClearCache: () -> Unit,
) {
  val context = LocalContext.current
  var menuExpanded by remember { mutableStateOf(false) }

  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
              context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
            }
            .getOrNull()
        }
    }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        1.dp,
        if (app.isWhitelisted || app.isProtectedByDefault)
          MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap!!,
          contentDescription = "${app.label} icon",
          modifier = Modifier.size(42.dp),
        )
      } else {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.size(42.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
          )
        }
      }

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

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (app.cacheBytes > 0L) {
            MiniBadge(
              text = FormatUtils.formatBytes(context, app.cacheBytes),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          MiniBadge(
            text = if (app.isStopped) "Stopped" else "Active",
            color = if (app.isStopped) MaterialTheme.colorScheme.onSurfaceVariant else StatusSuccess,
          )
          if (app.isProtectedByDefault && app.protectionReason != null) {
            MiniBadge(text = app.protectionReason, color = MaterialTheme.colorScheme.primary)
          }
        }
      }

      if (app.isProtectedByDefault) {
        Icon(
          imageVector = Icons.Rounded.Shield,
          contentDescription = "Protected by system safety rules",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      } else {
        Switch(
          checked = app.isWhitelisted,
          onCheckedChange = { onToggleWhitelist() },
          modifier = Modifier.testTag("whitelist_toggle_${app.packageName}"),
        )
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
          DropdownMenuItem(
            text = { Text("Force Stop Now") },
            leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onForceStop()
            },
          )
          DropdownMenuItem(
            text = { Text("Clear App Cache") },
            leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onClearCache()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun MiniBadge(text: String, color: androidx.compose.ui.graphics.Color) {
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
