package app.upmile.core.preview3d

/** Surface and solid materials; the app maps each to a Filament material. */
enum class Mat { ASPHALT, GRASS, PAVING, CONCRETE, EPOXY, KERB, WHITE_PAINT, YELLOW_PAINT, BARRIER, STEEL, NOISE_WALL, WALL, CEILING, HAZARD }

/** A flat band [width] wide along [center], raised by [lift] (paint sits just above asphalt). */
data class Ribbon(val center: List<Vec3>, val width: Float, val mat: Mat, val lift: Float = 0f)

/** A convex polygon (either winding); faces up, or down when [faceDown] (a ceiling). */
data class Patch(val outline: List<Vec3>, val mat: Mat, val lift: Float = 0f, val faceDown: Boolean = false)

/** A closed cross-section swept along [path]. [profile] is (right, up) metres in the path's frame, counter-clockwise. */
data class Extrusion(val path: List<Vec3>, val profile: List<Pair<Float, Float>>, val mat: Mat)

/**
 * Placed objects; the app builds each kind's mesh. Local frame: +Z faces the approaching driver, +X is that driver's
 * right, +Y up; arms over the road reach toward -X. [Prop.size] meaning per kind is documented in PropKit (app).
 */
enum class PropKind {
    STREET_LAMP, TWIN_LAMP, POST, SIGNAL_MAST, SIGN_GREEN, SIGN_BLUE, GANTRY_GREEN,
    WARNING_CURVE_LEFT, WARNING_CURVE_RIGHT, WARNING_ACCIDENT, SPEED_LIMIT, ROUNDABOUT_SIGN, YIELD_SIGN, CHEVRON_LEFT, CHEVRON_RIGHT,
    TREE, BUILDING_GLASS, BUILDING_APT, BUILDING_SHOP, PILLAR, CEILING_LIGHT, CONVEX_MIRROR, HEIGHT_BAR, SPEED_BUMP, ROAD_TEXT,
}

/** [heading]: travel heading of the lane the prop serves (radians, 0 = -Z, positive turns right). */
data class Prop(val kind: PropKind, val at: Vec3, val heading: Float = 0f, val size: Vec3? = null, val text: List<String> = emptyList())

/** Kenney Car Kit file names under preview3d/models/. */
enum class CarModel(val file: String) { SEDAN("sedan"), SUV("suv"), VAN("van"), HATCH("hatchback-sports"), TAXI("taxi"), TRUCK("truck"), DELIVERY("delivery") }

/** Linear RGB multipliers for the white body paint of the recoloured palette. */
enum class Paint(val r: Float, val g: Float, val b: Float) {
    WHITE(1f, 1f, 1f), SILVER(.62f, .64f, .66f), GREY(.34f, .35f, .37f), BLACK(.07f, .07f, .08f), NAVY(.09f, .13f, .24f), WINE(.33f, .06f, .08f),
}

/** At progress [t] (time fraction 0..1) the mover is [s] metres along its path. */
data class Key(val t: Float, val s: Float)

/** A car on [path] (y = road surface); [keys] sorted by t. */
data class Car(val model: CarModel, val paint: Paint, val path: List<Vec3>, val keys: List<Key>)

/** Eye-height camera [path] and its progress keys. */
data class Timeline(val path: List<Vec3>, val keys: List<Key>)

/** Lamps of a Korean horizontal 4-light signal head, left to right. */
enum class Lamp { RED, YELLOW, ARROW, GREEN }
data class SignalPhase(val t: Float, val lit: Set<Lamp>)

data class SceneSpec(
    val surfaces: List<Ribbon>,
    val patches: List<Patch>,
    val solids: List<Extrusion>,
    val props: List<Prop>,
    val cars: List<Car>,
    val camera: Timeline,
    /** (progress, text), progress strictly increasing inside (0, 1). */
    val cues: List<Pair<Float, String>>,
    val durationMs: Long,
    /** Phases of every signal head facing the camera; empty = no signals. */
    val signal: List<SignalPhase> = emptyList(),
    /** Camera-path metres where the roof starts; infinity = outdoors throughout. */
    val indoorFromM: Float = Float.POSITIVE_INFINITY,
) {
    /** Camera path length, metres. */
    val pathM: Float by lazy { arcLengths(camera.path).last() }
}
