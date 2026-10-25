package com.beykus.beygram.ui.screens.profile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.beykus.beygram.domain.model.validateExternalPaymentUrl
import java.util.Locale

/**
 * Opens a payment page outside BeyGram. The URL is validated again at the Intent boundary,
 * and the app never loads it into an embedded WebView or asks for card details itself.
 *
 * For t.me links BeyGram is also a handler. Those links get a chooser that excludes our own
 * activities, so a payment URL can never be re-ingested into the app's deep-link storage.
 */
internal fun openExternalPaymentPage(context: Context, rawUrl: String): Boolean {
    val url = validateExternalPaymentUrl(rawUrl) ?: return false
    val uri = Uri.parse(url.value)
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host == "t.me" || host == "telegram.me") {
            val ownComponents = ownPaymentHandlers(context, intent)
            if (ownComponents.isEmpty()) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents.toTypedArray())
                context.startActivity(chooser)
            }
        } else {
            context.startActivity(intent)
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun ownPaymentHandlers(context: Context, intent: Intent): List<ComponentName> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ownPaymentHandlersApi33(context, intent)
    } else {
        ownPaymentHandlersLegacy(context, intent)
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun ownPaymentHandlersApi33(context: Context, intent: Intent): List<ComponentName> =
    context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        .ownPaymentComponents(context)

@Suppress("DEPRECATION")
private fun ownPaymentHandlersLegacy(context: Context, intent: Intent): List<ComponentName> =
    context.packageManager.queryIntentActivities(intent, 0)
        .ownPaymentComponents(context)

private fun List<ResolveInfo>.ownPaymentComponents(context: Context): List<ComponentName> =
    map { it.activityInfo }
        .filter { it.packageName == context.packageName }
        .map { ComponentName(it.packageName, it.name) }
