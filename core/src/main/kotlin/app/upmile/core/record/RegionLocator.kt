package app.upmile.core.record

import app.upmile.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Properties read: `sgg` (5-digit code) and `sggnm` (name), as written by tools/build_regions.sh. */
class RegionLocator(geojson: String) {
    private class Poly(val rings: List<List<DoubleArray>>) {
        val minLon = rings[0].minOf { it[0] }; val maxLon = rings[0].maxOf { it[0] }
        val minLat = rings[0].minOf { it[1] }; val maxLat = rings[0].maxOf { it[1] }
    }

    private class Entry(val region: Region, val polys: List<Poly>)

    private val entries: List<Entry> = Json.parseToJsonElement(geojson).jsonObject["features"]!!.jsonArray.map { f ->
        val props = f.jsonObject["properties"]!!.jsonObject
        val geom = f.jsonObject["geometry"]!!.jsonObject
        val coords = geom["coordinates"]!!.jsonArray
        val polygons = if (geom["type"]!!.jsonPrimitive.content == "Polygon") listOf(coords) else coords.map { it.jsonArray }
        Entry(
            Region(props["sgg"]!!.jsonPrimitive.content, props["sggnm"]!!.jsonPrimitive.content),
            polygons.map { poly -> Poly(poly.map { ring -> ring.jsonArray.map { pt -> doubleArrayOf(pt.jsonArray[0].jsonPrimitive.doubleOrNull!!, pt.jsonArray[1].jsonPrimitive.doubleOrNull!!) } }) },
        )
    }

    fun locate(p: GeoPoint): Region? = entries.firstOrNull { e -> e.polys.any { contains(it, p) } }?.region

    private fun contains(poly: Poly, p: GeoPoint): Boolean {
        if (p.lon < poly.minLon || p.lon > poly.maxLon || p.lat < poly.minLat || p.lat > poly.maxLat) return false
        val inOuter = inRing(poly.rings[0], p)
        return inOuter && poly.rings.drop(1).none { inRing(it, p) }
    }

    private fun inRing(ring: List<DoubleArray>, p: GeoPoint): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (xi, yi) = ring[i]; val (xj, yj) = ring[j]
            if ((yi > p.lat) != (yj > p.lat) && p.lon < (xj - xi) * (p.lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
