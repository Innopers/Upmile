package app.upmile.core.route

import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {
    private fun readAsset(path: String) = javaClass.classLoader.getResource(path)!!.readText()
    private val repo = AssetRouteRepository(::readAsset)

    @Test fun commute_route_has_merge_left_and_ramp() {
        val segs = SegmentFinder.find(repo.route("commute"), destinationHasGarage = repo.destinations().first { it.id == "commute" }.hasGarage)
        assertEquals(setOf(SegmentType.HIGHWAY_MERGE, SegmentType.MULTILANE_LEFT, SegmentType.GARAGE_RAMP), segs.map { it.type }.toSet())
    }

    @Test fun destinations_are_the_three_demo_ids() {
        assertEquals(listOf("commute", "cafe", "inlaws"), repo.destinations().map { it.id })
    }

    @Test fun every_route_parses_and_has_a_segment() {
        for (d in repo.destinations()) {
            val r = repo.route(d.id)
            assertTrue(d.id, r.polyline.size >= 25)
            assertTrue(d.id, SegmentFinder.find(r, d.hasGarage).isNotEmpty())
        }
    }

    @Test fun total_distance_matches_polyline_length() {
        for (d in repo.destinations()) {
            val r = repo.route(d.id)
            val len = r.polyline.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }
            assertTrue("${d.id} total=${r.totalDistanceM} len=$len", Math.abs(r.totalDistanceM - len) <= len * 0.01)
        }
    }
}
