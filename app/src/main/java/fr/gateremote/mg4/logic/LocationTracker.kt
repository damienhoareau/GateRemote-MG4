package fr.gateremote.mg4.logic

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class Position(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

private const val MAX_LAST_KNOWN_AGE_MS = 2 * 60 * 1000L

/**
 * Positions de la voiture via le [LocationManager] du système.
 * On n'utilise pas les services Google Play : l'écran de la MG4 n'en dispose pas.
 * L'appelant doit avoir obtenu la permission de localisation.
 */
@SuppressLint("MissingPermission")
fun locationUpdates(context: Context): Flow<Position> = callbackFlow {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.allProviders.contains(it) }.getOrDefault(false) }

    fun Location.toPosition() =
        Position(latitude, longitude, if (hasAccuracy()) accuracy else null)

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(location.toPosition())
        }

        // Requis sur les anciennes versions d'Android, où ces méthodes sont abstraites.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // Dernière position connue, si elle est assez récente pour être fiable en roulant.
    providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_LAST_KNOWN_AGE_MS * 1_000_000 }
        .maxByOrNull { it.elapsedRealtimeNanos }
        ?.let { trySend(it.toPosition()) }

    providers.forEach { provider ->
        runCatching {
            manager.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper())
        }
    }
    awaitClose { manager.removeUpdates(listener) }
}
