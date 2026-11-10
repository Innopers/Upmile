package app.upmile.core.gleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the app tells Gleo ([json]) and where it opens ([route], null = stay put). */
data class GleoReply(val functionName: String, val status: String, val message: String, val route: String?) {
    fun json(): String = buildJsonObject {
        put("functionName", functionName); put("status", status); put("message", message)
    }.toString()
}

object GleoIntents {
    private val destinations = mapOf(
        "회사" to "commute", "처음 가는 카페" to "cafe", "처가" to "inlaws",
        "commute" to "commute", "cafe" to "cafe", "inlaws" to "inlaws",
    )

    fun routeFor(action: String, params: Map<String, String>): String? = when (action.trim().lowercase()) {
        "start_routine" -> "routine"
        "preview_today_route" -> destinations[params["destination"]?.trim()]?.let { "preview/$it" } ?: "preview"
        "show_my_record" -> "record"
        else -> null
    }

    /** ACTION_PARAM: an object, or (as the Gleo doc sample sends it) a JSON string holding the object. Never throws. */
    fun params(raw: String?): Map<String, String> {
        fun parse(text: String) = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        val first = parse(raw?.trim().orEmpty())
        val o = first as? JsonObject
            ?: (first as? JsonPrimitive)?.takeIf { it.isString }?.let { parse(it.content) as? JsonObject }
        return o?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it.content } }?.toMap().orEmpty()
    }

    private val places = mapOf("commute" to "work", "cafe" to "the new cafe", "inlaws" to "the in-laws' home")

    /** null = not a Gleo request. */
    fun reply(action: String, params: Map<String, String>, parked: Boolean): GleoReply? {
        val route = routeFor(action, params) ?: return null
        val fn = action.trim().uppercase()
        if (!parked) return GleoReply(fn, "fail", "Upmile can be used only while the car is parked.", null)
        return when (route) {
            "routine" -> GleoReply(fn, "success", "Started the pre-departure routine.", route)
            "record" -> GleoReply(fn, "success", "Opened your driving record.", route)
            "preview" -> GleoReply(
                fn, "asking", "Which destination should I preview: work, the new cafe, or the in-laws' home?", route,
            )
            else -> GleoReply(fn, "success", "Opened the route preview to ${places.getValue(route.removePrefix("preview/"))}.", route)
        }
    }
}
