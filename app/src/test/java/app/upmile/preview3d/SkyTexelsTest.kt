package app.upmile.preview3d

import org.junit.Assert.assertTrue
import org.junit.Test

class SkyTexelsTest {
    private val n = 8
    private val face = n * n * 3
    private fun px(face: Int, row: Int, col: Int, c: Int) = skyTexels(n)[face * this.face + (row * n + col) * 3 + c]

    @Test fun topAndBottomFacesAreFlat() {
        val t = skyTexels(n)
        for (i in 0 until face) {
            assertEquals(SKY_TOP[i % 3], t[2 * face + i])
            assertEquals(SKY_HORIZON[i % 3], t[3 * face + i])
        }
    }

    @Test fun sideFaceRunsFromTopToHorizonMonotonically() {
        for (c in 0 until 3) {
            assertEquals(SKY_TOP[c], px(0, 0, 0, c))
            assertEquals(SKY_HORIZON[c], px(0, n - 1, 0, c))
            val col = (0 until n).map { px(0, it, 0, c) }
            val down = SKY_HORIZON[c] < SKY_TOP[c]
            assertTrue(col.zipWithNext().all { (a, b) -> if (down) b <= a else b >= a })
        }
    }

    private fun assertEquals(want: Float, got: Float) = org.junit.Assert.assertEquals(want, got, 1e-4f)
}
