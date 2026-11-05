package app.upmile.core.route

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.GuidePoint
import app.upmile.core.model.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object TmapRouteParser {
    fun parse(id: String, name: String, json: String): Route {
        val features = Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray
        val polyline = mutableListOf<GeoPoint>()
        val guides = mutableListOf<GuidePoint>()
        var roadType: Int? = null
        var dist = 0
        var time = 0
        for (f in features) {
            val geom = f.jsonObject.getValue("geometry").jsonObject
            val props = f.jsonObject["properties"] as? JsonObject ?: JsonObject(emptyMap())
            val coords = geom.getValue("coordinates").jsonArray
            when (geom.getValue("type").jsonPrimitive.content) {
                "LineString" -> {
                    roadType = props["roadType"]?.jsonPrimitive?.intOrNull
                    coords.forEach { c -> polyline += c.jsonArray.let { GeoPoint(it[1].jsonPrimitive.content.toDouble(), it[0].jsonPrimitive.content.toDouble()) } }
                }
                "Point" -> {
                    if (guides.isEmpty()) {
                        dist = props["totalDistance"]?.jsonPrimitive?.intOrNull ?: 0
                        time = props["totalTime"]?.jsonPrimitive?.intOrNull ?: 0
                    }
                    val turn = props["turnType"]?.jsonPrimitive?.int ?: continue
                    guides += GuidePoint(
                        guides.size,
                        GeoPoint(coords[1].jsonPrimitive.content.toDouble(), coords[0].jsonPrimitive.content.toDouble()),
                        turn,
                        props["description"]?.jsonPrimitive?.content ?: "",
                        roadType,
                    )
                }
            }
        }
        return Route(id, name, polyline, guides, dist, time)
    }
}
