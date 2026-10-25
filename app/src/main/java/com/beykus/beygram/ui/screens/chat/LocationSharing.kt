package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.beykus.beygram.domain.model.GeoPoint
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val LOCATION_REQUEST_TIMEOUT_MS = 10_000L
private const val FRESH_LAST_KNOWN_MS = 2 * 60 * 1000L

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * One coarse/fine fix for the compose-location action. A recent last-known fix is preferred; otherwise the enabled
 * GPS/network providers are asked for a fresh location. The caller shows [hasLocationPermission] and the system
 * permission dialog; this helper only reports a failure after all providers are exhausted.
 */
internal suspend fun currentGeoPoint(context: Context): Result<GeoPoint> {
    if (!hasLocationPermission(context)) return Result.failure(SecurityException("location permission"))
    val manager = context.getSystemService(LocationManager::class.java)
        ?: return Result.failure(IllegalStateException("location manager"))
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    if (providers.isEmpty()) return Result.failure(IllegalStateException("no location provider"))

    val lastKnown = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= FRESH_LAST_KNOWN_MS) {
        return Result.success(lastKnown.toGeoPoint())
    }
    for (provider in providers) {
        val received = requestSingleLocation(context, manager, provider)
        if (received != null) return Result.success(received.toGeoPoint())
    }
    return Result.failure(IllegalStateException("location unavailable"))
}

private suspend fun requestSingleLocation(
    context: Context,
    manager: LocationManager,
    provider: String
): Location? = withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MS) {
    suspendCancellableCoroutine { continuation ->
        val delivered = AtomicBoolean(false)
        val signal = CancellationSignal()
        fun deliver(location: Location?) {
            if (delivered.compareAndSet(false, true) && continuation.isActive) continuation.resume(location)
        }
        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context)
            ) { location -> deliver(location) }
        } catch (_: SecurityException) {
            deliver(null)
        } catch (_: IllegalArgumentException) {
            deliver(null)
        }
        continuation.invokeOnCancellation { signal.cancel() }
    }
}

private fun Location.toGeoPoint() = GeoPoint(latitude, longitude, accuracy.toDouble())
