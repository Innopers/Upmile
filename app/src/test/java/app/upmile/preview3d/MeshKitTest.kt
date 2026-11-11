package app.upmile.preview3d

import app.upmile.core.preview3d.Extrusion
import app.upmile.core.preview3d.Mat
import app.upmile.core.preview3d.Patch
import app.upmile.core.preview3d.Ribbon
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.Timeline
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.v
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshKitTest {
    private fun MeshData.p(i: Int) = Vec3(positions[3 * i], positions[3 * i + 1], positions[3 * i + 2])
    private fun MeshData.n(i: Int) = Vec3(normals[3 * i], normals[3 * i + 1], normals[3 * i + 2])
    /** Every triangle's winding agrees with its first vertex's normal. */
    private fun MeshData.windingMatchesNormals() = (0 until indexCount / 3).all { k ->
        val a = indices[3 * k]; val b = indices[3 * k + 1]; val c = indices[3 * k + 2]
        dot(cross(p(b) - p(a), p(c) - p(a)), n(a)) > 0f
    }

    @Test fun ribbonIsOneQuadPerPieceFacingUpWithWorldUvs() {
        val m = MeshData().also { MeshKit.ribbon(it, Ribbon(listOf(v(0f, 0f), v(0f, -12f)), 2f, Mat.ASPHALT, lift = .01f), 6f) }
        assertEquals(4, m.vertexCount); assertEquals(6, m.indexCount)
        (0 until 4).forEach { assertEquals(Vec3(0f, 1f, 0f), m.n(it)); assertEquals(.01f, m.p(it).y, 1e-6f) }
        assertTrue(m.windingMatchesNormals())
        (0 until 4).forEach { assertEquals(m.p(it).x / 6f, m.uvs[2 * it], 1e-6f); assertEquals(m.p(it).z / 6f, m.uvs[2 * it + 1], 1e-6f) }
    }

    @Test fun patchFacesUpOrDownWhateverTheWinding() {
        val sq = listOf(v(0f, 0f), v(1f, 0f), v(1f, -1f), v(0f, -1f))
        for (outline in listOf(sq, sq.reversed())) for (down in listOf(false, true)) {
            val m = MeshData().also { MeshKit.patch(it, Patch(outline, Mat.GRASS, faceDown = down), 5f) }
            assertEquals(6, m.indexCount)
            assertEquals(if (down) -1f else 1f, m.n(0).y)
            assertTrue(m.windingMatchesNormals())
        }
    }

    @Test fun extrusionOfABoxHasFourOutwardSides() {
        val box = listOf(-1f to 0f, 1f to 0f, 1f to 2f, -1f to 2f)
        val m = MeshData().also { MeshKit.extrusion(it, Extrusion(listOf(v(0f, 0f), v(0f, -10f)), box, Mat.WALL), 3f) }
        assertEquals(16, m.vertexCount)
        val normals = (0 until 16).map { m.n(it) }.toSet()
        assertEquals(setOf(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(-1f, 0f, 0f)), normals)
        assertTrue(m.windingMatchesNormals())
    }

    @Test fun extrusionWithARepeatedPathPointHasNoNaN() {
        val box = listOf(-1f to 0f, 1f to 0f, 1f to 2f, -1f to 2f)
        val m = MeshData().also { MeshKit.extrusion(it, Extrusion(listOf(v(0f, 0f), v(0f, 0f), v(0f, -10f)), box, Mat.WALL), 3f) }
        assertTrue(m.vertexCount > 0)
        assertTrue((0 until 3 * m.vertexCount).none { m.positions[it].isNaN() || m.normals[it].isNaN() })
    }

    @Test fun closedExtrusionHasNoSeam() {
        val box = listOf(-1f to 0f, 1f to 0f, 1f to 2f, -1f to 2f)
        val sq = listOf(v(0f, 0f), v(10f, 0f), v(10f, -10f), v(0f, -10f), v(0f, 0f))
        val m = MeshData().also { MeshKit.extrusion(it, Extrusion(sq, box, Mat.WALL), 3f) }
        // Per profile edge there are 4 quads (16 verts); quad i uses path points i-1 (first two verts) and i (last two).
        // First ring = start of quad 0; last ring = b of quad 3.
        for (edge in 0 until 4) {
            val first = edge * 16 + 0 // a of quad 0
            val last = edge * 16 + 3 * 4 + 1 // b of quad 3: same profile corner at the last path point
            assertEquals(m.p(first).x, m.p(last).x, 1e-5f)
            assertEquals(m.p(first).y, m.p(last).y, 1e-5f)
            assertEquals(m.p(first).z, m.p(last).z, 1e-5f)
        }
    }

    @Test fun buildGroupsByMaterial() {
        val spec = SceneSpec(
            surfaces = listOf(Ribbon(listOf(v(0f, 0f), v(0f, -5f)), 3f, Mat.ASPHALT), Ribbon(listOf(v(0f, 0f), v(0f, -5f)), .15f, Mat.WHITE_PAINT, .01f)),
            patches = listOf(Patch(listOf(v(0f, 0f), v(1f, 0f), v(1f, -1f)), Mat.ASPHALT)),
            solids = emptyList(), props = emptyList(), cars = emptyList(),
            camera = Timeline(listOf(v(0f, 0f), v(0f, -1f)), emptyList()), cues = emptyList(), durationMs = 12_000,
        )
        val built = MeshKit.build(spec)
        assertEquals(setOf(Mat.ASPHALT, Mat.WHITE_PAINT), built.keys)
        assertEquals(6 + 3, built.getValue(Mat.ASPHALT).indexCount)
    }
}
