package app.upmile.core.preview3d

import kotlin.random.Random

/** Korean paint mix: white most common, then black, silver and grey. */
fun Random.pickPaint(): Paint = when (nextInt(20)) {
    in 0..6 -> Paint.WHITE
    in 7..9 -> Paint.SILVER
    in 10..11 -> Paint.GREY
    in 12..17 -> Paint.BLACK
    18 -> Paint.NAVY
    else -> Paint.WINE
}

private val MODEL_MIX = listOf(
    CarModel.SEDAN, CarModel.SEDAN, CarModel.SEDAN, CarModel.SUV, CarModel.SUV,
    CarModel.HATCH, CarModel.VAN, CarModel.TAXI, CarModel.TRUCK, CarModel.DELIVERY,
)
fun Random.pickModel(): CarModel = MODEL_MIX[nextInt(MODEL_MIX.size)]

/** A car at a steady [mps] along [path], [s0] metres in at the start of a [totalSec]-second scene. */
fun Random.cruise(path: List<Vec3>, s0: Float, mps: Float, totalSec: Float, model: CarModel = pickModel()) =
    Car(model, pickPaint(), path, listOf(Key(0f, s0), Key(1f, s0 + mps * totalSec)))

/** A car standing [s] metres along [path] the whole scene. */
fun Random.parked(path: List<Vec3>, s: Float, model: CarModel = pickModel()) =
    Car(model, pickPaint(), path, listOf(Key(0f, s), Key(1f, s)))

/** Trees roughly every [every] metres along [path], on both sides, [near]..[far] metres out. */
fun Random.trees(path: List<Vec3>, near: Float, far: Float, every: Float = 12f): List<Prop> {
    val total = arcLengths(path).last()
    val out = mutableListOf<Prop>()
    var s = nextFloat() * every
    while (s < total) {
        val here = listOf(pointAt(path, s).at, pointAt(path, s + 1f).at)
        for (side in listOf(-1f, 1f)) {
            val p = offset(here, side * (near + nextFloat() * (far - near)))[0]
            out += Prop(PropKind.TREE, p, size = Vec3(1.8f + nextFloat(), 6f + 3f * nextFloat(), 0f))
        }
        s += every * (.7f + .6f * nextFloat())
    }
    return out
}

/** Grass under everything, just below the road. */
fun bigGround(zNear: Float, zFar: Float, half: Float = 400f) =
    Patch(listOf(v(-half, zNear), v(half, zNear), v(half, zFar), v(-half, zFar)), Mat.GRASS, lift = -.03f)

/** W-beam guardrail rail (posts come from [posts]). */
val GUARDRAIL = listOf(-.05f to .45f, .05f to .45f, .05f to .8f, -.05f to .8f)
/** Concrete median barrier, 1 m tall. */
val NEW_JERSEY = listOf(-.3f to 0f, .3f to 0f, .2f to .3f, .1f to 1f, -.1f to 1f, -.2f to .3f)
/** Kerb stone 15 cm tall. */
val KERB = listOf(-.1f to 0f, .1f to 0f, .1f to .15f, -.1f to .15f)

/** Guardrail posts every [every] metres along [path]. */
fun posts(path: List<Vec3>, every: Float = 4f): List<Prop> {
    val total = arcLengths(path).last()
    return generateSequence(0f) { it + every }.takeWhile { it <= total }.map { Prop(PropKind.POST, pointAt(path, it).at) }.toList()
}
