package com.example.syssweep.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.syssweep.data.JunkCategory
import com.example.syssweep.data.JunkItem
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.util.FormatUtils
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusWarning

@Composable
fun StorageCleanerScreen(
  state: DashboardUiState,
  onScanJunk: () -> Unit,
  onToggleJunkItem: (String) -> Unit,
  onToggleJunkCategory: (JunkCategory) -> Unit,
  onSelectAllJunk: (Boolean) -> Unit,
  onCleanSelectedJunk: () -> Unit,
  onRefreshPermission: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val hasAccess = state.diagnostics.hasAllFilesAccess
  val expandedCategories = remember {
    mutableStateMapOf<JunkCategory, Boolean>().apply {
      JunkCategory.entries.forEach { put(it, false) }
    }
  }
  var showConfirmCleanDialog by remember { mutableStateOf(false) }

  val legacyStoragePermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      onRefreshPermission()
    }

  val requestStorageAccess: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching {
          context.startActivity(
            Intent(
              Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
              Uri.parse("package:${context.packageName}"),
            )
          )
        }
        .onFailure {
          runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
          }
        }
      Unit
    } else {
      legacyStoragePermLauncher.launch(
        arrayOf(
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
      )
    }
  }

  if (showConfirmCleanDialog) {
    val sizeText = FormatUtils.formatBytes(context, state.selectedJunkBytes)
    AlertDialog(
      onDismissRequest = { showConfirmCleanDialog = false },
      title = { Text("Clean Selected Junk?") },
      text = {
        Text(
          "Permanently delete ${state.selectedJunkCount} selected junk items ($sizeText)?\n\nAll Photos, Audio, Video, Documents, and Archives remain untouched."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showConfirmCleanDialog = false
            onCleanSelectedJunk()
          },
          colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
          modifier = Modifier.testTag("confirm_clean_junk_button"),
        ) {
          Text("Delete Junk", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmCleanDialog = false }) { Text("Cancel") }
      },
    )
  }

  val grouped = remember(state.junkItems) { state.junkItems.groupBy { it.category } }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("storage_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Back to Home",
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Storage Junk Cleaner",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                "Total: ${FormatUtils.formatBytes(context, state.telemetry.totalStorageBytes)} • Free: ${FormatUtils.formatBytes(context, state.telemetry.freeStorageBytes)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = EmeraldAccent.copy(alpha = 0.12f),
          border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint = EmeraldAccent,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text =
                "Safe Scan: Photos, Audio, Video, Documents & Archives are strictly protected and never flagged.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      if (!hasAccess) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.6f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "All Files Access Needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text =
                  "To detect .thumbnails, temp/log files, empty folders, old APKs, and residual non-media files across internal storage, grant All Files Access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = requestStorageAccess,
                shape = RoundedCornerShape(999.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("grant_all_files_button"),
              ) {
                Text("Grant All Files Access", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      } else {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = CyanSecondary.copy(alpha = 0.16f),
                  modifier = Modifier.size(46.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.FolderDelete,
                    contentDescription = null,
                    tint = CyanSecondary,
                    modifier = Modifier.padding(10.dp),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      if (state.hasScannedJunkOnce) {
                        "${FormatUtils.formatBytes(context, state.totalJunkBytes)} Junk Found"
                      } else {
                        "Ready to Scan Internal Storage"
                      },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text =
                      if (state.hasScannedJunkOnce) {
                        "${state.selectedJunkCount} of ${state.junkItems.size} items selected (${FormatUtils.formatBytes(context, state.selectedJunkBytes)})"
                      } else {
                        "Thumbnails, temp files, empty folders, APKs & residual junk"
                      },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              AnimatedVisibility(visible = state.isScanningJunk || state.isCleaningJunk) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = onScanJunk,
                  enabled = !state.isScanningJunk && !state.isCleaningJunk,
                  shape = RoundedCornerShape(999.dp),
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("scan_junk_button"),
                ) {
                  if (state.isScanningJunk) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.Search,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (state.isScanningJunk) "Scanning…" else "Scan Storage")
                }

                Button(
                  onClick = { showConfirmCleanDialog = true },
                  enabled =
                    state.selectedJunkCount > 0 &&
                      !state.isScanningJunk &&
                      !state.isCleaningJunk,
                  shape = RoundedCornerShape(999.dp),
                  modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("clean_junk_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    if (state.isCleaningJunk) "Cleaning…"
                    else "Clean (${state.selectedJunkCount})",
                  )
                }
              }

              if (state.junkItems.isNotEmpty()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  val allSelected = state.selectedJunkCount == state.junkItems.size
                  TextButton(onClick = { onSelectAllJunk(!allSelected) }) {
                    Text(if (allSelected) "Deselect All" else "Select All")
                  }
                }
              }
            }
          }
        }

        if (state.hasScannedJunkOnce && state.junkItems.isEmpty() && !state.isScanningJunk) {
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f)),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CleaningServices,
                  contentDescription = null,
                  tint = EmeraldAccent,
                  modifier = Modifier.size(40.dp),
                )
                Text(
                  text = "No Junk Files Found",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Your shared storage has no leftover thumbnails, temp files, or empty folders.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(JunkCategory.entries) { category ->
            val itemsInCat = grouped[category].orEmpty()
            if (itemsInCat.isNotEmpty() || state.hasScannedJunkOnce) {
              JunkCategoryCard(
                category = category,
                items = itemsInCat,
                expanded = expandedCategories[category] == true,
                onToggleExpand = {
                  expandedCategories[category] = !(expandedCategories[category] ?: false)
                },
                onToggleCategory = { onToggleJunkCategory(category) },
                onToggleItem = onToggleJunkItem,
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun JunkCategoryCard(
  category: JunkCategory,
  items: List<JunkItem>,
  expanded: Boolean,
  onToggleExpand: () -> Unit,
  onToggleCategory: () -> Unit,
  onToggleItem: (String) -> Unit,
) {
  val context = LocalContext.current
  val selectedInCat = items.count { it.isSelected }
  val totalSizeInCat = items.sumOf { it.sizeBytes }
  val selectedSizeInCat = items.filter { it.isSelected }.sumOf { it.sizeBytes }

  val icon =
    when (category) {
      JunkCategory.THUMBNAILS -> Icons.Rounded.Image
      JunkCategory.TEMP_FILES -> Icons.Rounded.InsertDriveFile
      JunkCategory.EMPTY_FOLDERS -> Icons.Rounded.FolderOff
      JunkCategory.OLD_APKS -> Icons.Rounded.Android
      JunkCategory.OTHER_JUNK -> Icons.Rounded.CleaningServices
    }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = items.isNotEmpty()) { onToggleExpand() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Checkbox(
          checked = items.isNotEmpty() && selectedInCat == items.size,
          enabled = items.isNotEmpty(),
          onCheckedChange = { onToggleCategory() },
          modifier = Modifier.testTag("junk_cat_${category.name.lowercase()}"),
        )

        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = CyanSecondary,
          modifier = Modifier.size(24.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              if (items.isEmpty()) "${category.subtitle} • 0 found"
              else if (category == JunkCategory.EMPTY_FOLDERS) "${items.size} empty folders ($selectedInCat selected)"
              else "${items.size} items • ${FormatUtils.formatBytes(context, selectedSizeInCat)} / ${FormatUtils.formatBytes(context, totalSizeInCat)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (items.isNotEmpty()) {
          IconButton(onClick = onToggleExpand) {
            Icon(
              imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = if (expanded) "Collapse ${category.title}" else "Expand ${category.title}",
            )
          }
        }
      }

      AnimatedVisibility(visible = expanded && items.isNotEmpty()) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
          Spacer(modifier = Modifier.height(6.dp))
          items.take(80).forEach { item ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onToggleItem(item.path) }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggleItem(item.path) },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.name,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = item.path.removePrefix("/storage/emulated/0"),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (!item.isDirectory) {
                Text(
                  text = FormatUtils.formatBytes(context, item.sizeBytes),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          if (items.size > 80) {
            Text(
              text = "Showing first 80 of ${items.size} items (all selected items will still be cleaned).",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
        }
      }
    }
  }
}
