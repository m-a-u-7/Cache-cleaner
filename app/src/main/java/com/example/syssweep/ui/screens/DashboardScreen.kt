package com.example.syssweep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.syssweep.data.InstalledAppInfo
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.ui.AppFilter
import com.example.syssweep.ui.DashboardUiState
import com.example.syssweep.util.FormatUtils
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
  state: DashboardUiState,
  onSweep: (SweepActionType) -> Unit,
  onToggleSelectToKill: (String) -> Unit,
  onSelectAllToKill: () -> Unit,
  onClearAllToKill: () -> Unit,
  onForceStopSelected: () -> Unit,
  onForceStopSingle: (String) -> Unit,
  onClearSingleCacheRootOnly: (String) -> Unit,
  onOpenAppInfo: (String) -> Unit,
  onRefresh: () -> Unit,
  onRequestShizuku: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  onOpenManager: (AppFilter) -> Unit,
  onOpenStorageCleaner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isRunning = state.preferences.isSweepRunning

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Top App Bar Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Clean Master",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "System Cache, RAM & Storage Optimizer",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
          ) {
            IconButton(
              onClick = onRefresh,
              modifier = Modifier.testTag("refresh_button"),
            ) {
              if (state.isLoadingInventory) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              } else {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = "Refresh status",
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }

      // Privilege & Quick Telemetry Hero Card
      item {
        HeroStatusBannerCard(
          state = state,
          onRequestShizuku = onRequestShizuku,
        )
      }

      // Active Sweep Progress Banner (only visible when cleaning)
      item {
        AnimatedVisibility(visible = isRunning) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Optimizing device… trimming caches & stopping background apps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
          }
        }
      }

      // Row 1: RAM Cleaner | Cache Cleaner
      item {
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val ramRingColor =
            when {
              state.telemetry.ramUsedPercent >= 85 -> StatusDanger
              state.telemetry.ramUsedPercent >= 70 -> StatusWarning
              else -> EmeraldAccent
            }

          CircularCleanerGaugeCard(
            title = "RAM Cleaner",
            centerPrimary = "${state.telemetry.ramUsedPercent}%",
            centerSecondary = "RAM Used",
            progress = state.telemetry.ramUsedPercent / 100f,
            ringColor = ramRingColor,
            detailLine1 =
              "${FormatUtils.formatBytes(context, state.telemetry.usedRamBytes)} / ${FormatUtils.formatBytes(context, state.telemetry.totalRamBytes)}",
            detailLine2 = "${state.activeTargetAppsCount} active apps",
            buttonLabel = "Clean RAM",
            buttonTestTag = "ram_cleaner_button",
            isSweeping = isRunning,
            onActionClick = { onSweep(SweepActionType.FORCE_STOP) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )

          val cacheDisplay =
            if (state.diagnostics.hasUsageStatsPerm) {
              FormatUtils.formatBytes(context, state.totalUserCacheBytes)
            } else {
              "${state.telemetry.storageUsedPercent}%"
            }
          val cacheProgress =
            if (state.diagnostics.hasUsageStatsPerm) {
              state.cacheGaugePercent / 100f
            } else {
              state.telemetry.storageUsedPercent / 100f
            }

          CircularCleanerGaugeCard(
            title = "Cache Cleaner",
            centerPrimary = cacheDisplay,
            centerSecondary =
              if (state.diagnostics.hasUsageStatsPerm) "App Cache" else "Storage Used",
            progress = cacheProgress,
            ringColor = MaterialTheme.colorScheme.primary,
            detailLine1 =
              if (state.diagnostics.hasUsageStatsPerm) "${state.cachedAppsCount} cached apps"
              else "${state.userAppsCount} installed apps",
            detailLine2 =
              if (state.diagnostics.hasUsageStatsPerm) "All caches in 1 tap"
              else "Global trim ready",
            buttonLabel = "Clear Cache",
            buttonTestTag = "clean_cache_button",
            isSweeping = isRunning,
            onActionClick = { onSweep(SweepActionType.CLEAR_CACHE) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
      }

      // Row 2: Storage Cleaner (Left) | One-Tap Clean All (Right)
      item {
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val storageRingColor =
            when {
              state.telemetry.storageUsedPercent >= 90 -> StatusDanger
              state.telemetry.storageUsedPercent >= 78 -> StatusWarning
              else -> CyanSecondary
            }

          CircularCleanerGaugeCard(
            title = "Storage Cleaner",
            centerPrimary = "${state.telemetry.storageUsedPercent}%",
            centerSecondary = "Used",
            progress = state.telemetry.storageUsedPercent / 100f,
            ringColor = storageRingColor,
            detailLine1 =
              "Total: ${FormatUtils.formatBytes(context, state.telemetry.totalStorageBytes)}",
            detailLine2 =
              "Free: ${FormatUtils.formatBytes(context, state.telemetry.freeStorageBytes)}",
            buttonLabel = "Clean Storage",
            buttonTestTag = "storage_clean_button",
            isSweeping = false,
            onActionClick = onOpenStorageCleaner,
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .testTag("open_storage_cleaner_card"),
          )

          OneTapCircularCard(
            activeTargets = state.activeTargetAppsCount,
            cacheText =
              if (state.diagnostics.hasUsageStatsPerm) {
                FormatUtils.formatBytes(context, state.totalUserCacheBytes)
              } else {
                "All App Caches"
              },
            isSweeping = isRunning,
            onCleanAll = { onSweep(SweepActionType.BOOST_BOTH) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
      }

      // Background Apps (Select to Kill)
      item {
        ForceStopSelectionCard(
          candidatesCount = state.activeTargetAppsCount,
          selectedPackages = state.effectiveSelectedToKill,
          blacklistedCount = state.blacklistedCount,
          previewApps = state.activeKillPreview,
          isSweeping = isRunning,
          onToggleSelect = onToggleSelectToKill,
          onSelectAll = onSelectAllToKill,
          onClearSelection = onClearAllToKill,
          onForceStopSelected = onForceStopSelected,
          onForceStopAll = { onSweep(SweepActionType.FORCE_STOP) },
          onForceStopSingle = onForceStopSingle,
          onOpenBlacklist = { onOpenManager(AppFilter.BLACKLISTED) },
          onOpenAllRunning = { onOpenManager(AppFilter.ACTIVE) },
        )
      }

      // Cached Apps Preview
      item {
        CachedAppsPreviewCard(
          cacheText = FormatUtils.formatBytes(context, state.totalUserCacheBytes),
          userAppsCount = state.userAppsCount,
          hasUsageStats = state.diagnostics.hasUsageStatsPerm,
          canDeleteSingleCache = state.canDeleteSingleCache,
          previewApps = state.cachedAppsPreview,
          onClearSingleCacheRootOnly = onClearSingleCacheRootOnly,
          onOpenAppInfo = onOpenAppInfo,
          onGrantUsageAccess = onOpenUsageAccess,
          onViewAll = { onOpenManager(AppFilter.USER) },
        )
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun HeroStatusBannerCard(
  state: DashboardUiState,
  onRequestShizuku: () -> Unit,
) {
  val context = LocalContext.current
  val mode = state.diagnostics.activeMode
  val isReady = mode != PrivilegeMode.LIMITED_NONE

  val (badgeLabel, badgeBg) =
    when (mode) {
      PrivilegeMode.SYSTEM_PRIV_APP -> "System Priv-App Active" to EmeraldAccent
      PrivilegeMode.ROOT_SU -> "Root (su) Active" to EmeraldAccent
      PrivilegeMode.SHIZUKU -> "Shizuku Active" to EmeraldAccent
      PrivilegeMode.LIMITED_NONE ->
        if (state.diagnostics.isShizukuRunning) "Tap to Grant Shizuku" to StatusWarning
        else "Privilege Required" to StatusDanger
    }

  val freeRam = FormatUtils.formatBytes(context, state.telemetry.freeRamBytes)
  val freeStorage = FormatUtils.formatBytes(context, state.telemetry.freeStorageBytes)
  val lastSummary = state.preferences.lastSweepResult?.summaryMessage

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        1.dp,
        if (isReady) EmeraldAccent.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
      ),
    modifier =
      Modifier.fillMaxWidth().clickable(enabled = !isReady) {
        onRequestShizuku()
      },
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.horizontalGradient(
              colors =
                if (isReady) {
                  listOf(EmeraldGlow.copy(alpha = 0.9f), MaterialTheme.colorScheme.surface)
                } else {
                  listOf(
                    StatusWarning.copy(alpha = 0.18f),
                    MaterialTheme.colorScheme.surface,
                  )
                }
            )
          )
          .padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(52.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    EmeraldAccent.copy(alpha = 0.38f),
                  )
                )
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.RocketLaunch,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = badgeBg,
          ) {
            Text(
              text = badgeLabel,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF062010),
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TelemetryMiniChip(text = "Free RAM: $freeRam", accent = EmeraldAccent)
            TelemetryMiniChip(text = "Free Disk: $freeStorage", accent = CyanSecondary)
          }

          Text(
            text =
              lastSummary
                ?: if (isReady) "Elevated cleaner ready for RAM, Cache & Bloatware"
                else "Start Shizuku, grant Root, or install as System Priv-App",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryMiniChip(text: String, accent: Color) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = accent.copy(alpha = 0.14f),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accent,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
private fun CircularCleanerGaugeCard(
  title: String,
  centerPrimary: String,
  centerSecondary: String,
  progress: Float,
  ringColor: Color,
  detailLine1: String,
  detailLine2: String,
  buttonLabel: String,
  buttonTestTag: String,
  isSweeping: Boolean,
  onActionClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 650),
      label = "gauge_progress",
    )
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(10.dp))

      Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val stroke = 8.5.dp.toPx()
          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
          drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = (animatedProgress * 360f).coerceAtLeast(10f),
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = centerPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
          Text(
            text = centerSecondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = detailLine1,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
        Text(
          text = detailLine2,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onActionClick,
        enabled = !isSweeping,
        shape = RoundedCornerShape(999.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = ringColor,
            contentColor =
              if (ringColor == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary
              else Color(0xFF061B10),
          ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag(buttonTestTag),
      ) {
        Text(
          text = buttonLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun OneTapCircularCard(
  activeTargets: Int,
  cacheText: String,
  isSweeping: Boolean,
  onCleanAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val accentBrush = Brush.linearGradient(listOf(primaryColor, EmeraldAccent))

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f)),
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent)
            )
          )
          .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "One-Tap Clean",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Circular Glowing Boost Orb matching the 92dp ring height
      Box(
        modifier =
          Modifier.size(92.dp)
            .clip(CircleShape)
            .clickable(enabled = !isSweeping, onClick = onCleanAll),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val stroke = 7.dp.toPx()
          drawArc(
            brush = accentBrush,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
        }
        Surface(
          shape = CircleShape,
          color = primaryColor.copy(alpha = 0.18f),
          modifier = Modifier.size(72.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = "One-Tap Clean All",
              tint = EmeraldAccent,
              modifier = Modifier.size(30.dp),
            )
            Text(
              text = if (isSweeping) "Running" else "BOOST",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // What will be cleaned summary lines
      Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        CleanTargetLine(
          icon = Icons.Rounded.CheckCircle,
          text = "Cache: $cacheText",
          tint = primaryColor,
        )
        CleanTargetLine(
          icon = Icons.Rounded.CheckCircle,
          text = "Stop $activeTargets BG apps",
          tint = EmeraldAccent,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onCleanAll,
        enabled = !isSweeping,
        shape = RoundedCornerShape(999.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("sweep_all_button"),
      ) {
        Text(
          text = if (isSweeping) "Cleaning…" else "Clean All",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CleanTargetLine(icon: ImageVector, text: String, tint: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(13.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ForceStopSelectionCard(
  candidatesCount: Int,
  selectedPackages: Set<String>,
  blacklistedCount: Int,
  previewApps: List<InstalledAppInfo>,
  isSweeping: Boolean,
  onToggleSelect: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearSelection: () -> Unit,
  onForceStopSelected: () -> Unit,
  onForceStopAll: () -> Unit,
  onForceStopSingle: (String) -> Unit,
  onOpenBlacklist: () -> Unit,
  onOpenAllRunning: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
          modifier = Modifier.size(38.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.StopCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Background Apps",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "${selectedPackages.size} selected to stop • $candidatesCount running",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        FilledTonalButton(
          onClick = onForceStopAll,
          enabled = !isSweeping && candidatesCount > 0,
          shape = RoundedCornerShape(999.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier.heightIn(min = 44.dp).testTag("force_stop_button"),
        ) {
          Text("Stop All", style = MaterialTheme.typography.labelLarge)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Check apps you want to force stop",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = onOpenBlacklist,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
          Text("Blacklist ($blacklistedCount)", style = MaterialTheme.typography.labelMedium)
        }
      }

      if (previewApps.isEmpty()) {
        Text(
          text = "All unnecessary background apps are currently stopped.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick =
              if (selectedPackages.size == candidatesCount) onClearSelection else onSelectAll,
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 40.dp).testTag("toggle_select_all_kill"),
          ) {
            Text(
              text =
                if (selectedPackages.size == candidatesCount && candidatesCount > 0) "Clear"
                else "Select All ($candidatesCount)",
              style = MaterialTheme.typography.labelMedium,
            )
          }

          Button(
            onClick = onForceStopSelected,
            enabled = !isSweeping && selectedPackages.isNotEmpty(),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier =
              Modifier.weight(1f).heightIn(min = 42.dp).testTag("stop_selected_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Memory,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Stop Selected (${selectedPackages.size})",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          previewApps.forEach { app ->
            val checked = app.packageName in selectedPackages
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { onToggleSelect(app.packageName) }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = { onToggleSelect(app.packageName) },
                colors =
                  CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.testTag("select_kill_${app.packageName}"),
              )
              AppMiniIcon(packageName = app.packageName, label = app.label)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = app.label,
                  style = MaterialTheme.typography.bodyLarge,
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
              }
              TextButton(
                onClick = { onForceStopSingle(app.packageName) },
                enabled = !isSweeping,
                modifier = Modifier.testTag("quick_stop_${app.packageName}"),
              ) {
                Text("Stop")
              }
            }
          }
        }

        if (candidatesCount > previewApps.size) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenAllRunning) {
              Text("View all $candidatesCount running apps")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CachedAppsPreviewCard(
  cacheText: String,
  userAppsCount: Int,
  hasUsageStats: Boolean,
  canDeleteSingleCache: Boolean,
  previewApps: List<InstalledAppInfo>,
  onClearSingleCacheRootOnly: (String) -> Unit,
  onOpenAppInfo: (String) -> Unit,
  onGrantUsageAccess: () -> Unit,
  onViewAll: () -> Unit,
) {
  val context = LocalContext.current

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
          modifier = Modifier.size(38.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.CleaningServices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Top Cached Apps",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              if (hasUsageStats) "$cacheText total across $userAppsCount apps"
              else "Enable Usage Access to inspect per-app cache MB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!hasUsageStats) {
        OutlinedButton(
          onClick = onGrantUsageAccess,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("grant_usage_button"),
        ) {
          Text("Grant Usage Access Permission")
        }
      } else {
        Text(
          text =
            if (canDeleteSingleCache) {
              "Root / Delete-Cache active: tap the trash icon to clear an app's cache individually."
            } else {
              "Global 'Clear Cache' above trims all app caches together. Tap an app's info icon to open System App Info."
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (previewApps.isEmpty()) {
          Text(
            text = "No significant app caches found right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            previewApps.forEach { app ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                AppMiniIcon(packageName = app.packageName, label = app.label)
                Text(
                  text = app.label,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = FormatUtils.formatBytes(context, app.cacheBytes),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canDeleteSingleCache) {
                  IconButton(
                    onClick = { onClearSingleCacheRootOnly(app.packageName) },
                    modifier = Modifier.size(44.dp).testTag("quick_clear_${app.packageName}"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = "Clear cache for ${app.label}",
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                } else {
                  IconButton(
                    onClick = { onOpenAppInfo(app.packageName) },
                    modifier = Modifier.size(44.dp).testTag("app_info_${app.packageName}"),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                      contentDescription = "Open App Info for ${app.label}",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onViewAll) { Text("Manage all apps") }
          }
        }
      }
    }
  }
}

@Composable
fun AppMiniIcon(packageName: String, label: String, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
              context.packageManager.getApplicationIcon(packageName).toBitmap(84, 84).asImageBitmap()
            }
            .getOrNull()
        }
    }

  if (iconBitmap != null) {
    Image(
      bitmap = iconBitmap!!,
      contentDescription = "$label icon",
      modifier = modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
    )
  } else {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = modifier.size(34.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Apps,
        contentDescription = null,
        modifier = Modifier.padding(6.dp),
      )
    }
  }
}
