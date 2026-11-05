package app.upmile.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmapRouteParserTest {
    private fun fixture(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test fun parse_readsPolylineGuidesAndTotals() {
        val r = TmapRouteParser.parse("commute", "회사", fixture("tmap_commute_small.json"))
        assertTrue(r.polyline.size >= 2)
        assertEquals(listOf(200, 12, 101, 201), r.guides.map { it.turnType }) // fixture order
        assertEquals(18200, r.totalDistanceM)
        assertEquals(1440, r.totalTimeS)
        assertEquals(listOf(null, 6, 0, 1), r.guides.map { it.roadType })
        assertEquals(37.4979, r.polyline.first().lat, 1e-9) // coordinates are [lon, lat]
    }
}
