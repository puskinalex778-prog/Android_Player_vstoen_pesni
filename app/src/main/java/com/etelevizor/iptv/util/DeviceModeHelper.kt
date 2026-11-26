package com.etelevizor.iptv.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.model.DeviceMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceModeHelper {

    /**
     * Accurately detects whether the Android device is a TV / Android TV Box vs Phone / Tablet.
     */
    fun isHardwareTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.hardware.type.television")
        ) {
            return true
        }
        // Fallback: devices without touchscreen (many Chinese AOSP Android TV boxes!)
        val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        if (!hasTouch) {
            return true
        }
        // Check if D-Pad navigation is primary without touch
        val nav = context.resources.configuration.navigation
        val touch = context.resources.configuration.touchscreen
        if (touch == Configuration.TOUCHSCREEN_NOTOUCH &&
            (nav == Configuration.NAVIGATION_DPAD || nav == Configuration.NAVIGATION_TRACKBALL)
        ) {
            return true
        }
        return false
    }

    fun detectedHardwareLabel(context: Context): String {
        return if (isHardwareTv(context)) {
            "Android TV / Приставка"
        } else {
            val smallestDp = context.resources.configuration.smallestScreenWidthDp
            if (smallestDp >= 600) "Планшет (сенсор)" else "Смартфон (сенсор)"
        }
    }

    fun isTvMode(context: Context, prefs: AppPreferences): Boolean {
        return when (prefs.deviceMode) {
            DeviceMode.TV -> true
            DeviceMode.PHONE -> false
            DeviceMode.AUTO -> isHardwareTv(context)
        }
    }

    fun activeModeShortLabel(context: Context, prefs: AppPreferences): String {
        val isTv = isTvMode(context, prefs)
        return when (prefs.deviceMode) {
            DeviceMode.AUTO -> if (isTv) "Авто (ТВ)" else "Авто (Телефон)"
            DeviceMode.TV -> "Режим ТВ"
            DeviceMode.PHONE -> "Режим Телефон"
        }
    }

    fun applyOrientation(activity: Activity, prefs: AppPreferences, isPlayer: Boolean) {
        if (isTvMode(activity, prefs)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            return
        }
        when (prefs.phoneOrientation) {
            "landscape_always" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "landscape_player" -> {
                activity.requestedOrientation = if (isPlayer) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            "portrait_menu" -> {
                activity.requestedOrientation = if (isPlayer) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun hasHardwareDpad(): Boolean {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val dev = InputDevice.getDevice(id) ?: continue
            if ((dev.sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
                return true
            }
        }
        return false
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE, dd MMM", Locale("ru"))

    fun formatTime(ms: Long): String = synchronized(timeFormat) { timeFormat.format(Date(ms)) }

    fun formatDateTime(ms: Long): String = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(ms)) }

    fun formatDay(ms: Long): String = synchronized(dayFormat) { dayFormat.format(Date(ms)) }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f КБ", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f МБ", mb)
        return String.format(Locale.US, "%.2f ГБ", mb / 1024.0)
    }
}
