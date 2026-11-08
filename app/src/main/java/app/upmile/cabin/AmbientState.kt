package app.upmile.cabin

internal sealed interface AmbientWrite {
    data object SwitchOn : AmbientWrite
    data class Brightness(val value: Int) : AmbientWrite
    data class Color(val value: Int) : AmbientWrite
}

/** Which originals could be read; a property whose original is unknown is never written. */
internal data class Known(val switch: Boolean, val brightness: Boolean, val color: Boolean)

/** What the ambient light needs next. Pure: the controller runs the writes and reports failures through [failed]. */
internal class AmbientState {
    @Volatile var on = false; private set
    @Volatile private var lastBrightness: Int? = null
    @Volatile private var lastColor: Int? = null
    private var brightnessKnown = false
    private var colorKnown = false

    /** [original] reads the originals (called only while the light is not yet on); false = nothing was written. */
    fun apply(brightness: Int, argb: Int, original: () -> Known, run: (AmbientWrite) -> Unit): Boolean {
        if (!on) {
            val k = original()
            if (!k.switch) return false
            brightnessKnown = k.brightness; colorKnown = k.color
            on = true // before the write: a failure resets it
            run(AmbientWrite.SwitchOn)
        }
        if (brightnessKnown && brightness != lastBrightness) { lastBrightness = brightness; run(AmbientWrite.Brightness(brightness)) }
        if (colorKnown && argb != lastColor) { lastColor = argb; run(AmbientWrite.Color(argb)) }
        return true
    }

    fun failed(w: AmbientWrite) = when (w) {
        AmbientWrite.SwitchOn -> on = false
        is AmbientWrite.Brightness -> lastBrightness = null
        is AmbientWrite.Color -> lastColor = null
    }

    fun reset() { on = false; lastBrightness = null; lastColor = null }
}
