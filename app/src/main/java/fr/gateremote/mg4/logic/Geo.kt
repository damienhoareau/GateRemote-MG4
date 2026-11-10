package fr.gateremote.mg4.logic

import fr.gateremote.mg4.data.Gate
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Distance à vol d'oiseau entre deux points (formule de haversine). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Un portail et sa distance à la voiture (`null` tant que la position est inconnue). */
data class RankedGate(val gate: Gate, val distanceMeters: Double?) {
    val isNearby: Boolean
        get() = distanceMeters != null && distanceMeters <= gate.radiusMeters
}

/**
 * Classe les portails du plus proche au plus lointain.
 * Sans position connue, ils sont triés par nom et aucun n'est considéré comme proche.
 */
fun rankGates(gates: List<Gate>, latitude: Double?, longitude: Double?): List<RankedGate> {
    if (latitude == null || longitude == null) {
        return gates.sortedBy { it.name.lowercase() }.map { RankedGate(it, null) }
    }
    return gates
        .map { RankedGate(it, distanceMeters(latitude, longitude, it.latitude, it.longitude)) }
        .sortedBy { it.distanceMeters }
}

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m"
    else String.format(Locale.FRENCH, "%.1f km", meters / 1000)
