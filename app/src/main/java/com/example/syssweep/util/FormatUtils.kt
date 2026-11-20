package com.example.syssweep.util

import android.text.format.Formatter
import android.content.Context
import java.util.concurrent.TimeUnit

object FormatUtils {
  fun formatBytes(context: Context, bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return Formatter.formatShortFileSize(context, bytes)
  }

  fun formatRelativeTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Never"
    val diff = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
      diff < 60_000L -> "Just now"
      minutes < 60L -> "${minutes}m ago"
      hours < 24L -> "${hours}h ago"
      else -> "${days}d ago"
    }
  }
}
