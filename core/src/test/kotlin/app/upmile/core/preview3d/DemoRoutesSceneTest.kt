package app.upmile.core.preview3d

import app.upmile.core.route.AssetRouteRepository
import app.upmile.core.route.SegmentFinder
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every segment the demo routes really produce builds a scene that keeps the invariants (cues are checked per scene type). */
class DemoRoutesSceneTest {
    private val repo = AssetRouteRepository { javaClass.classLoader.getResource(it)!!.readText() }

    @Test fun everyDemoSegmentKeepsTheInvariants() = repo.destinations().forEach { d ->
        val segments = SegmentFinder.find(repo.route(d.id), d.hasGarage)
        assertTrue("${d.id} has segments", segments.isNotEmpty())
        segments.forEach { seg ->
            val s = SceneBuilder.build(seg)
            assertTrue("${seg.id} has cues", s.cues.isNotEmpty())
            SceneChecks.cues(s, s.cues.map { it.second }) // order and range; the wording is checked per scene type
            SceneChecks.timing(s); SceneChecks.onRoad(s); SceneChecks.gaps(s); SceneChecks.propsClear(s); SceneChecks.nothingStandsInTheWay(s)
        }
    }
}
