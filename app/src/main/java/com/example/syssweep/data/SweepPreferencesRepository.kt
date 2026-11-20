package com.example.syssweep.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sweepDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "syssweep_prefs")

data class SweepUserPreferences(
  val whitelistedPackages: Set<String> = emptySet(),
  val preferredBackend: PreferredBackend = PreferredBackend.AUTO,
  val includeSystemApps: Boolean = false,
  val autoProtectEssential: Boolean = true,
  val widgetPrimaryAction: WidgetPrimaryAction = WidgetPrimaryAction.BOOST_BOTH,
  val isSweepRunning: Boolean = false,
  val lastSweepResult: SweepResult? = null,
)

class SweepPreferencesRepository(private val context: Context) {

  private object Keys {
    val WHITELISTED_PACKAGES = stringSetPreferencesKey("whitelisted_packages")
    val PREFERRED_BACKEND = stringPreferencesKey("preferred_backend")
    val INCLUDE_SYSTEM_APPS = booleanPreferencesKey("include_system_apps")
    val AUTO_PROTECT_ESSENTIAL = booleanPreferencesKey("auto_protect_essential")
    val WIDGET_PRIMARY_ACTION = stringPreferencesKey("widget_primary_action")
    val IS_SWEEP_RUNNING = booleanPreferencesKey("is_sweep_running")

    val LAST_ACTION_TYPE = stringPreferencesKey("last_action_type")
    val LAST_TIMESTAMP = longPreferencesKey("last_timestamp")
    val LAST_MODE_USED = stringPreferencesKey("last_mode_used")
    val LAST_CACHE_TRIMMED = booleanPreferencesKey("last_cache_trimmed")
    val LAST_FREED_BYTES = longPreferencesKey("last_freed_bytes")
    val LAST_STOPPED_COUNT = intPreferencesKey("last_stopped_count")
    val LAST_FAILED_COUNT = intPreferencesKey("last_failed_count")
    val LAST_SKIPPED_COUNT = intPreferencesKey("last_skipped_count")
    val LAST_SUMMARY = stringPreferencesKey("last_summary")
  }

  val preferencesFlow: Flow<SweepUserPreferences> =
    context.sweepDataStore.data.map { prefs -> prefs.toSweepUserPreferences() }

  suspend fun getSnapshot(): SweepUserPreferences = preferencesFlow.first()

  suspend fun toggleWhitelist(packageName: String) {
    context.sweepDataStore.edit { prefs ->
      val current = prefs[Keys.WHITELISTED_PACKAGES] ?: emptySet()
      prefs[Keys.WHITELISTED_PACKAGES] =
        if (packageName in current) current - packageName else current + packageName
    }
  }

  suspend fun setPreferredBackend(backend: PreferredBackend) {
    context.sweepDataStore.edit { prefs -> prefs[Keys.PREFERRED_BACKEND] = backend.name }
  }

  suspend fun setIncludeSystemApps(include: Boolean) {
    context.sweepDataStore.edit { prefs -> prefs[Keys.INCLUDE_SYSTEM_APPS] = include }
  }

  suspend fun setAutoProtectEssential(protect: Boolean) {
    context.sweepDataStore.edit { prefs -> prefs[Keys.AUTO_PROTECT_ESSENTIAL] = protect }
  }

  suspend fun setWidgetPrimaryAction(action: WidgetPrimaryAction) {
    context.sweepDataStore.edit { prefs -> prefs[Keys.WIDGET_PRIMARY_ACTION] = action.name }
  }

  suspend fun setSweepRunning(running: Boolean) {
    context.sweepDataStore.edit { prefs -> prefs[Keys.IS_SWEEP_RUNNING] = running }
  }

  suspend fun saveSweepResult(result: SweepResult) {
    context.sweepDataStore.edit { prefs ->
      prefs[Keys.IS_SWEEP_RUNNING] = false
      prefs[Keys.LAST_ACTION_TYPE] = result.actionType.name
      prefs[Keys.LAST_TIMESTAMP] = result.timestampMillis
      prefs[Keys.LAST_MODE_USED] = result.modeUsed.name
      prefs[Keys.LAST_CACHE_TRIMMED] = result.cacheTrimmedSuccess
      prefs[Keys.LAST_FREED_BYTES] = result.freedBytesEstimate
      prefs[Keys.LAST_STOPPED_COUNT] = result.stoppedCount
      prefs[Keys.LAST_FAILED_COUNT] = result.failedCount
      prefs[Keys.LAST_SKIPPED_COUNT] = result.skippedCount
      prefs[Keys.LAST_SUMMARY] = result.summaryMessage
    }
  }

  private fun Preferences.toSweepUserPreferences(): SweepUserPreferences {
    val backend =
      this[Keys.PREFERRED_BACKEND]?.let { name ->
        runCatching { PreferredBackend.valueOf(name) }.getOrNull()
      } ?: PreferredBackend.AUTO

    val widgetAction =
      this[Keys.WIDGET_PRIMARY_ACTION]?.let { name ->
        runCatching { WidgetPrimaryAction.valueOf(name) }.getOrNull()
      } ?: WidgetPrimaryAction.BOOST_BOTH

    val lastTimestamp = this[Keys.LAST_TIMESTAMP]
    val lastResult =
      if (lastTimestamp != null && lastTimestamp > 0L) {
        SweepResult(
          actionType =
            this[Keys.LAST_ACTION_TYPE]?.let {
              runCatching { SweepActionType.valueOf(it) }.getOrNull()
            } ?: SweepActionType.BOOST_BOTH,
          timestampMillis = lastTimestamp,
          modeUsed =
            this[Keys.LAST_MODE_USED]?.let {
              runCatching { PrivilegeMode.valueOf(it) }.getOrNull()
            } ?: PrivilegeMode.LIMITED_NONE,
          cacheTrimmedSuccess = this[Keys.LAST_CACHE_TRIMMED] ?: false,
          freedBytesEstimate = this[Keys.LAST_FREED_BYTES] ?: 0L,
          stoppedCount = this[Keys.LAST_STOPPED_COUNT] ?: 0,
          failedCount = this[Keys.LAST_FAILED_COUNT] ?: 0,
          skippedCount = this[Keys.LAST_SKIPPED_COUNT] ?: 0,
          summaryMessage = this[Keys.LAST_SUMMARY] ?: "",
        )
      } else {
        null
      }

    return SweepUserPreferences(
      whitelistedPackages = this[Keys.WHITELISTED_PACKAGES] ?: emptySet(),
      preferredBackend = backend,
      includeSystemApps = this[Keys.INCLUDE_SYSTEM_APPS] ?: false,
      autoProtectEssential = this[Keys.AUTO_PROTECT_ESSENTIAL] ?: true,
      widgetPrimaryAction = widgetAction,
      isSweepRunning = this[Keys.IS_SWEEP_RUNNING] ?: false,
      lastSweepResult = lastResult,
    )
  }
}
