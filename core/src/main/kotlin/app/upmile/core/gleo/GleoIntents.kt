package app.upmile.core.gleo

object GleoIntents {
    private val destinations = mapOf(
        "회사" to "commute", "처음 가는 카페" to "cafe", "처가" to "inlaws",
        "commute" to "commute", "cafe" to "cafe", "inlaws" to "inlaws",
    )

    fun routeFor(action: String, params: Map<String, String>): String? = when (action) {
        "start_routine" -> "routine"
        "preview_today_route" -> destinations[params["destination"]?.trim()]?.let { "preview/$it" } ?: "preview"
        "show_my_record" -> "record"
        else -> null
    }
}
