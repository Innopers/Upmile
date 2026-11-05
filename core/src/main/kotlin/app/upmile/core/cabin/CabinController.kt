package app.upmile.core.cabin

/** false = not applied. */
interface CabinController {
    fun ambient(brightness: Int, argb: Int): Boolean
    fun quietFan(): Boolean
    fun restore()
}
