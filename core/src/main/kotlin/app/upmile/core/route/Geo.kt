package app.upmile.core.route

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.Route
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_R = 6_371_000.0

fun distanceM(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_R * asin(sqrt(h))
}

/** Distance from route start to the projection of [p] onto the nearest polyline segment. */
fun alongRouteM(route: Route, p: GeoPoint): Double {
    val pl = route.polyline
    var cum = 0.0
    var best = Double.MAX_VALUE
    var bestAlong = 0.0
    for (i in 0 until pl.size - 1) {
        val a = pl[i]
        val b = pl[i + 1]
        val len = distanceM(a, b)
        // shortcut: local flat projection around a, fine for segments of a few km
        val k = cos(Math.toRadians(a.lat)) * PI / 180 * EARTH_R
        val m = PI / 180 * EARTH_R
        val bx = (b.lon - a.lon) * k
        val by = (b.lat - a.lat) * m
        val px = (p.lon - a.lon) * k
        val py = (p.lat - a.lat) * m
        val l2 = bx * bx + by * by
        val t = if (l2 == 0.0) 0.0 else max(0.0, min(1.0, (px * bx + py * by) / l2))
        val d = hypot(px - t * bx, py - t * by)
        if (d < best) {
            best = d
            bestAlong = cum + t * len
        }
        cum += len
    }
    return bestAlong
}
