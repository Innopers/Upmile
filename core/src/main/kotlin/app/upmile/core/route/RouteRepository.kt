package app.upmile.core.route

import app.upmile.core.model.Route

data class Destination(val id: String, val name: String, val lat: Double, val lon: Double, val hasGarage: Boolean)

interface RouteRepository {
    fun destinations(): List<Destination>
    fun route(destinationId: String): Route
}

class AssetRouteRepository(private val read: (String) -> String) : RouteRepository {
    private val list = listOf(
        Destination("commute", "회사", 37.3948, 127.1112, true),
        Destination("cafe", "처음 가는 카페", 37.5446, 127.0557, false),
        Destination("inlaws", "처가", 37.3670, 127.1084, true),
    )

    override fun destinations() = list

    override fun route(destinationId: String): Route {
        val d = list.first { it.id == destinationId }
        return TmapRouteParser.parse(d.id, d.name, read("routes/${d.id}.json"))
    }
}
