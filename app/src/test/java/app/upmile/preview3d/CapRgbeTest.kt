package app.upmile.preview3d

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CapRgbeTest {
    @Test fun capsOnlyTheExponentChannelInRunsAndLiterals() {
        val header = "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 1 +X 8\n".toByteArray()
        val line = intArrayOf(
            2, 2, 0, 8,
            128 + 8, 200,                       // R: a run of 200 (above the cap: not an exponent, kept)
            8, 1, 2, 3, 4, 5, 6, 7, 250,        // G: literals
            128 + 8, 140,                       // B: a run
            4, 120, 140, 133, 200, 128 + 4, 150, // E: literals, then a run
        ).map { it.toByte() }.toByteArray()
        val out = capRgbe(header + line, 133)
        val want = line.copyOf().also { it[19] = 133.toByte(); it[21] = 133.toByte(); it[23] = 133.toByte() }
        assertArrayEquals(header + want, out)
    }

    @Test fun walksTheWholeSky() {
        val sky = javaClass.classLoader!!.getResourceAsStream("preview3d/sky.hdr")!!.use { it.readBytes() }
        assertEquals(sky.size, capRgbe(sky.copyOf(), 133).size) // throws if a scanline is out of step
    }
}
