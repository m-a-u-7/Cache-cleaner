package com.example.syssweep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.syssweep.data.PrivilegeDiagnostics
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.util.FormatUtils
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun DashboardScreen(
  state: DashboardUiState,
  onSweep: (SweepActionType) -> Unit,
  onRefresh: () -> Unit,
  onRequestShizuku: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  onNavigateToConfig: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isRunning = state.preferences.isSweepRunning

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        HeaderRow(
          activeMode = state.diagnostics.activeMode,
          isLoading = state.isLoadingInventory,
          onRefresh = onRefresh,
        )
      }

      item {
        TelemetryHeroCard(
          cacheText =
            if (state.diagnostics.hasUsageStatsPerm) {
              FormatUtils.formatBytes(context, state.totalUserCacheBytes)
            } else {
              "Grant Usage Access"
            },
          activeAppsCount = state.activeTargetAppsCount,
          totalTargetsCount = state.totalTargetAppsCount,
          hasUsageAccess = state.diagnostics.hasUsageStatsPerm,
          isSweeping = isRunning,
          onGrantUsageAccess = onOpenUsageAccess,
        )
      }

      item {
        SweepActionDeck(
          isSweeping = isRunning,
          hasPrivilege = state.diagnostics.hasAnyElevatedPrivilege,
          onSweep = onSweep,
        )
      }

      item {
        PrivilegeStatusCard(
          diagnostics = state.diagnostics,
          onRequestShizuku = onRequestShizuku,
          onOpenConfig = onNavigateToConfig,
        )
      }

      state.preferences.lastSweepResult?.let { last ->
        item {
          LastSweepSummaryCard(
            summary = last.summaryMessage,
            whenText = FormatUtils.formatRelativeTime(last.timestampMillis),
            skippedCount = last.skippedCount,
            freedText = FormatUtils.formatBytes(context, last.freedBytesEstimate),
            cacheTrimmed = last.cacheTrimmedSuccess,
          )
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun HeaderRow(
  activeMode: PrivilegeMode,
  isLoading: Boolean,
  onRefresh: () -> Unit,
) {
  val (badgeText, badgeColor) =
    when (activeMode) {
      PrivilegeMode.SYSTEM_PRIV_APP -> "SYSTEM PRIV-APP" to StatusSuccess
      PrivilegeMode.ROOT_SU -> "ROOT (SU)" to StatusSuccess
      PrivilegeMode.SHIZUKU -> "SHIZUKU ACTIVE" to MaterialTheme.colorScheme.secondary
      PrivilegeMode.LIMITED_NONE -> "NO PRIVILEGE" to StatusWarning
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
        text = "SysSweep",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = "Cache & Process Telemetry",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
      ) {
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelMedium,
          color = badgeColor,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }

      IconButton(
        onClick = onRefresh,
        modifier = Modifier.testTag("refresh_button"),
      ) {
        if (isLoading) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Refresh device telemetry",
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryHeroCard(
  cacheText: String,
  activeAppsCount: Int,
  totalTargetsCount: Int,
  hasUsageAccess: Boolean,
  isSweeping: Boolean,
  onGrantUsageAccess: () -> Unit,
) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Canvas(modifier = Modifier.matchParentSize()) {
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(primary.copy(alpha = 0.18f), Color.Transparent),
              center = Offset(size.width * 0.9f, size.height * 0.15f),
              radius = size.maxDimension * 0.65f,
            )
        )
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(secondary.copy(alpha = 0.12f), Color.Transparent),
              center = Offset(size.width * 0.1f, size.height * 0.95f),
              radius = size.maxDimension * 0.5f,
            )
        )
      }

      Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "RECLAIMABLE CACHE",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = cacheText,
              style =
                if (hasUsageAccess) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Icon(
              imageVector = Icons.Rounded.Storage,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(10.dp).size(24.dp),
            )
          }
        }

        if (!hasUsageAccess) {
          OutlinedButton(
            onClick = onGrantUsageAccess,
            modifier = Modifier.testTag("grant_usage_button").heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text("Enable Usage Access to Measure Cache Sizes")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricTile(
            label = "ACTIVE TARGETS",
            value = "$activeAppsCount",
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            label = "SWEEPABLE APPS",
            value = "$totalTargetsCount",
            modifier = Modifier.weight(1f),
          )
        }

        AnimatedVisibility(visible = isSweeping) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
              text = "Executing sweep across background packages…",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun SweepActionDeck(
  isSweeping: Boolean,
  hasPrivilege: Boolean,
  onSweep: (SweepActionType) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Button(
      onClick = { onSweep(SweepActionType.BOOST_BOTH) },
      enabled = !isSweeping,
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("sweep_all_button"),
      shape = RoundedCornerShape(18.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
      Icon(imageVector = Icons.Rounded.Bolt, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = if (isSweeping) "Sweeping System…" else "Boost & Sweep All",
        style = MaterialTheme.typography.titleMedium,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      FilledTonalButton(
        onClick = { onSweep(SweepActionType.CLEAR_CACHE) },
        enabled = !isSweeping,
        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("clean_cache_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.CleaningServices,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Clear Cache")
      }

      FilledTonalButton(
        onClick = { onSweep(SweepActionType.FORCE_STOP) },
        enabled = !isSweeping,
        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("force_stop_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.PowerSettingsNew,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Force Stop")
      }
    }

    if (!hasPrivilege) {
      Text(
        text =
          "Tip: Connect Shizuku for immediate testing, or flash the built APK to /system/priv-app for permanent rootless privilege.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PrivilegeStatusCard(
  diagnostics: PrivilegeDiagnostics,
  onRequestShizuku: () -> Unit,
  onOpenConfig: () -> Unit,
) {
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
          imageVector = Icons.Rounded.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
          text = "Privilege Capabilities",
          style = MaterialTheme.typography.titleMedium,
        )
      }

      CapabilityRow("System Priv-App Partition", diagnostics.isSystemPartitionApp)
      CapabilityRow("CLEAR_APP_CACHE Permission", diagnostics.hasClearCachePerm)
      CapabilityRow("FORCE_STOP_PACKAGES Permission", diagnostics.hasForceStopPerm)
      CapabilityRow("Root (su) Binary", diagnostics.isRootAvailable)
      CapabilityRow(
        "Shizuku Bridge",
        diagnostics.isShizukuRunning && diagnostics.isShizukuPermissionGranted,
        pendingText =
          when {
            diagnostics.isShizukuRunning && !diagnostics.isShizukuPermissionGranted ->
              "Running (Tap Grant)"
            else -> "Not Running"
          },
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!diagnostics.isShizukuPermissionGranted) {
          OutlinedButton(
            onClick = onRequestShizuku,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("request_shizuku_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Terminal,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Grant Shizuku")
          }
        }

        OutlinedButton(
          onClick = onOpenConfig,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_zip_guide_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Priv-App XML")
        }
      }
    }
  }
}

@Composable
private fun CapabilityRow(
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun LastSweepSummaryCard(
  summary: String,
  whenText: String,
  skippedCount: Int,
  freedText: String,
  cacheTrimmed: Boolean,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "LAST SWEEP LOG",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = whenText,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = summary,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      val extras = buildList {
        if (cacheTrimmed && freedText != "0 B") add("Estimated cache swept: $freedText")
        add("Protected/skipped: $skippedCount")
      }
      Text(
        text = extras.joinToString(" • "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
