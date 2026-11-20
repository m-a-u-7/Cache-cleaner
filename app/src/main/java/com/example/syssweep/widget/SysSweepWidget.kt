package com.example.syssweep.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.syssweep.data.PrivilegeMode
import com.example.syssweep.data.SweepActionType
import com.example.syssweep.data.SweepPreferencesRepository
import com.example.syssweep.data.SweepUserPreferences
import com.example.syssweep.data.WidgetPrimaryAction
import com.example.syssweep.service.CleanupForegroundService
import com.example.syssweep.util.FormatUtils

suspend fun updateAllSysSweepWidgets(context: Context) {
  runCatching { SysSweepWidget().updateAll(context) }
  runCatching { MiniRamWidget().updateAll(context) }
  runCatching { MiniCacheWidget().updateAll(context) }
  runCatching { MiniDualWidget().updateAll(context) }
}

class SysSweepWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SysSweepWidget()
}

class MiniRamWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = MiniRamWidget()
}

class MiniCacheWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = MiniCacheWidget()
}

class MiniDualWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = MiniDualWidget()
}

class SysSweepWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = SweepPreferencesRepository(context)
    val initialPrefs = repo.getSnapshot()

    provideContent {
      val prefs by repo.preferencesFlow.collectAsState(initial = initialPrefs)
      GlanceTheme {
        WidgetContent(prefs = prefs)
      }
    }
  }
}

class MiniRamWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = SweepPreferencesRepository(context)
    val initialPrefs = repo.getSnapshot()

    provideContent {
      val prefs by repo.preferencesFlow.collectAsState(initial = initialPrefs)
      GlanceTheme {
        MiniSingleIconWidgetContent(
          isSweeping = prefs.isSweepRunning,
          iconRes = R.drawable.ic_widget_ram,
          label = if (prefs.isSweepRunning) "…" else "RAM",
          accentColor = Color(0xFF22C55E),
          iconTint = Color(0xFF052E16),
          actionType = SweepActionType.FORCE_STOP,
          contentDescription = "RAM Clean",
        )
      }
    }
  }
}

class MiniCacheWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = SweepPreferencesRepository(context)
    val initialPrefs = repo.getSnapshot()

    provideContent {
      val prefs by repo.preferencesFlow.collectAsState(initial = initialPrefs)
      GlanceTheme {
        MiniSingleIconWidgetContent(
          isSweeping = prefs.isSweepRunning,
          iconRes = R.drawable.ic_widget_cache,
          label = if (prefs.isSweepRunning) "…" else "Cache",
          accentColor = Color(0xFF3B82F6),
          iconTint = Color.White,
          actionType = SweepActionType.CLEAR_CACHE,
          contentDescription = "Cache Clean",
        )
      }
    }
  }
}

class MiniDualWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = SweepPreferencesRepository(context)
    val initialPrefs = repo.getSnapshot()

    provideContent {
      val prefs by repo.preferencesFlow.collectAsState(initial = initialPrefs)
      GlanceTheme {
        MiniDualIconWidgetContent(isSweeping = prefs.isSweepRunning)
      }
    }
  }
}

@Composable
private fun MiniSingleIconWidgetContent(
  isSweeping: Boolean,
  iconRes: Int,
  label: String,
  accentColor: Color,
  iconTint: Color,
  actionType: SweepActionType,
  contentDescription: String,
) {
  Box(
    modifier =
      GlanceModifier.fillMaxSize()
        .background(Color(0xEE1A222C))
        .cornerRadius(20.dp)
        .clickable(
          onClick =
            actionRunCallback<RunSweepWidgetAction>(
              actionParametersOf(ACTION_TYPE_KEY to actionType.name)
            )
        )
        .padding(6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier =
          GlanceModifier.size(30.dp)
            .background(if (isSweeping) Color(0xFF475569) else accentColor)
            .cornerRadius(999.dp),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          provider = ImageProvider(iconRes),
          contentDescription = contentDescription,
          colorFilter = ColorFilter.tint(ColorProvider(iconTint)),
          modifier = GlanceModifier.size(18.dp),
        )
      }
      Text(
        text = label,
        style =
          TextStyle(
            color = ColorProvider(Color(0xFFF1F5F9)),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
          ),
      )
    }
  }
}

@Composable
private fun MiniDualIconWidgetContent(isSweeping: Boolean) {
  Row(
    modifier =
      GlanceModifier.fillMaxSize()
        .background(Color(0xEE1A222C))
        .cornerRadius(24.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        GlanceModifier.defaultWeight()
          .size(38.dp)
          .background(if (isSweeping) Color(0xFF334155) else Color(0xFF22C55E))
          .cornerRadius(999.dp)
          .clickable(
            onClick =
              actionRunCallback<RunSweepWidgetAction>(
                actionParametersOf(ACTION_TYPE_KEY to SweepActionType.FORCE_STOP.name)
              )
          ),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        provider = ImageProvider(R.drawable.ic_widget_ram),
        contentDescription = "RAM Clean",
        colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF052E16))),
        modifier = GlanceModifier.size(20.dp),
      )
    }

    Spacer(modifier = GlanceModifier.width(8.dp))

    Box(
      modifier =
        GlanceModifier.defaultWeight()
          .size(38.dp)
          .background(if (isSweeping) Color(0xFF334155) else Color(0xFF3B82F6))
          .cornerRadius(999.dp)
          .clickable(
            onClick =
              actionRunCallback<RunSweepWidgetAction>(
                actionParametersOf(ACTION_TYPE_KEY to SweepActionType.CLEAR_CACHE.name)
              )
          ),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        provider = ImageProvider(R.drawable.ic_widget_cache),
        contentDescription = "Cache Clean",
        colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
        modifier = GlanceModifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun WidgetContent(prefs: SweepUserPreferences) {
  val lastResult = prefs.lastSweepResult
  val statusText =
    when {
      prefs.isSweepRunning -> "Sweeping caches & background apps…"
      lastResult != null -> {
        val whenText = FormatUtils.formatRelativeTime(lastResult.timestampMillis)
        "$whenText • ${lastResult.summaryMessage}"
      }
      else -> "Tap an action below to sweep"
    }

  val modeBadge =
    when (lastResult?.modeUsed) {
      PrivilegeMode.SYSTEM_PRIV_APP -> "PRIV-APP"
      PrivilegeMode.ROOT_SU -> "ROOT"
      PrivilegeMode.SHIZUKU -> "SHIZUKU"
      PrivilegeMode.LIMITED_NONE -> "NO PRIV"
      null -> "READY"
    }

  Column(
    modifier =
      GlanceModifier.fillMaxSize()
        .background(GlanceTheme.colors.surface)
        .cornerRadius(22.dp)
        .padding(14.dp),
    verticalAlignment = Alignment.Vertical.Top,
  ) {
    Row(
      modifier =
        GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
          text = "Clean Master",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurface,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
            ),
        )
        Text(
          text = statusText,
          maxLines = 2,
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurfaceVariant,
              fontSize = 11.sp,
            ),
        )
      }
      Spacer(modifier = GlanceModifier.width(8.dp))
      Text(
        text = modeBadge,
        modifier =
          GlanceModifier.background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style =
          TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
          ),
      )
    }

    Spacer(modifier = GlanceModifier.defaultWeight())

    Row(
      modifier = GlanceModifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val isCachePrimary = prefs.widgetPrimaryAction == WidgetPrimaryAction.CLEAR_CACHE
      val isStopPrimary = prefs.widgetPrimaryAction == WidgetPrimaryAction.FORCE_STOP
      val isBoostPrimary = prefs.widgetPrimaryAction == WidgetPrimaryAction.BOOST_BOTH

      Button(
        text = "Cache",
        enabled = !prefs.isSweepRunning,
        onClick =
          actionRunCallback<RunSweepWidgetAction>(
            actionParametersOf(ACTION_TYPE_KEY to SweepActionType.CLEAR_CACHE.name)
          ),
        modifier = GlanceModifier.defaultWeight(),
        colors =
          ButtonDefaults.buttonColors(
            backgroundColor =
              if (isCachePrimary) GlanceTheme.colors.primary
              else GlanceTheme.colors.secondaryContainer,
            contentColor =
              if (isCachePrimary) GlanceTheme.colors.onPrimary
              else GlanceTheme.colors.onSecondaryContainer,
          ),
      )
      Spacer(modifier = GlanceModifier.width(8.dp))
      Button(
        text = "Stop",
        enabled = !prefs.isSweepRunning,
        onClick =
          actionRunCallback<RunSweepWidgetAction>(
            actionParametersOf(ACTION_TYPE_KEY to SweepActionType.FORCE_STOP.name)
          ),
        modifier = GlanceModifier.defaultWeight(),
        colors =
          ButtonDefaults.buttonColors(
            backgroundColor =
              if (isStopPrimary) GlanceTheme.colors.primary
              else GlanceTheme.colors.secondaryContainer,
            contentColor =
              if (isStopPrimary) GlanceTheme.colors.onPrimary
              else GlanceTheme.colors.onSecondaryContainer,
          ),
      )
      Spacer(modifier = GlanceModifier.width(8.dp))
      Button(
        text = if (prefs.isSweepRunning) "…" else "Boost",
        enabled = !prefs.isSweepRunning,
        onClick =
          actionRunCallback<RunSweepWidgetAction>(
            actionParametersOf(ACTION_TYPE_KEY to SweepActionType.BOOST_BOTH.name)
          ),
        modifier = GlanceModifier.defaultWeight(),
        colors =
          ButtonDefaults.buttonColors(
            backgroundColor =
              if (isBoostPrimary) GlanceTheme.colors.primary
              else GlanceTheme.colors.secondaryContainer,
            contentColor =
              if (isBoostPrimary) GlanceTheme.colors.onPrimary
              else GlanceTheme.colors.onSecondaryContainer,
          ),
      )
    }
  }
}

private val ACTION_TYPE_KEY = ActionParameters.Key<String>("sweep_action_type")

class RunSweepWidgetAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val actionName = parameters[ACTION_TYPE_KEY] ?: SweepActionType.BOOST_BOTH.name
    val actionType =
      runCatching { SweepActionType.valueOf(actionName) }.getOrDefault(SweepActionType.BOOST_BOTH)
    CleanupForegroundService.startSweep(context, actionType)
  }
}
