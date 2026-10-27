package com.beykus.beygram.data.media

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import java.util.Locale

/**
 * Snapshot of the build and the device taken before a diagnostic run, so reports taken on
 * different builds can be compared. Reads only public, non-identifying state; no serial,
 * account, chat or file identifiers are included.
 */
internal object VideoNoteEnvironment {
    /** Bump when the layout of the report text changes, so before/after reports are not mixed up. */
    const val REPORT_SCHEMA_VERSION = 5

    fun snapshot(context: Context): String = buildString {
        appendLine(context.getString(R.string.env_heading, REPORT_SCHEMA_VERSION))
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        appendLine(context.getString(R.string.env_app, context.packageName, info?.versionName ?: "?", code?.toString() ?: "?"))
        appendLine(context.getString(R.string.env_build, BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, BuildConfig.BUILD_GIT_SHA))
        appendLine(context.getString(R.string.env_libraries, BuildConfig.CAMERAX_VERSION,
            BuildConfig.MEDIA3_VERSION, Build.VERSION.SDK_INT))
        appendLine(context.getString(R.string.env_device, Build.MANUFACTURER, Build.MODEL, Build.DEVICE, soc()))
        appendLine(context.getString(R.string.env_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            Build.VERSION.SECURITY_PATCH.ifBlank { "?" }))
        appendLine(context.getString(R.string.env_fingerprint, Build.FINGERPRINT))
        appendLine(context.getString(R.string.env_permissions,
            granted(context, Manifest.permission.CAMERA), granted(context, Manifest.permission.RECORD_AUDIO)))
        appendLine(context.getString(R.string.env_power, battery(context), powerSaver(context)))
        thermalNow(context)?.let { appendLine(context.getString(R.string.camera_report_thermal, it)) }
        appendLine(context.getString(R.string.env_resources, memory(context), freeDisk(context),
            SystemClock.elapsedRealtime() / 60_000))
        appendLine(context.getString(R.string.env_orientation, context.resources.configuration.orientation.let {
            if (it == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        }))
        appendLine(context.getString(R.string.env_privacy_notice))
    }

    private fun soc(): String = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { "?" }
    } else Build.HARDWARE

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun battery(context: Context): String {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "?"
        return "$percent${if (charging) "+" else ""}"
    }

    private fun powerSaver(context: Context): Boolean? =
        runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode }.getOrNull()

    private fun thermalNow(context: Context): String? {
        val status = runCatching { context.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull()
            ?: return null
        return thermalLabel(context, status)
    }

    private fun memory(context: Context): String {
        val info = ActivityManager.MemoryInfo()
        runCatching { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info) }
        return if (info.totalMem > 0) "${info.availMem shr 20}/${info.totalMem shr 20} MB${if (info.lowMemory) " low" else ""}" else "?"
    }

    private fun freeDisk(context: Context): String = runCatching {
        val stat = StatFs(context.filesDir.path)
        String.format(Locale.US, "%d MB", stat.availableBytes shr 20)
    }.getOrDefault("?")
}
