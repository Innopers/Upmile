package app.upmile.core.preview3d

/** Camera metres along its path at progress [t]. */
fun SceneSpec.distanceAt(t: Float): Float = at(camera.keys, t).coerceIn(0f, pathM)

/** What the HUD shows as 남은 거리. */
fun SceneSpec.remainingM(t: Float): Float = pathM - distanceAt(t)

/** Arclength fraction for the Canvas fallback, which walks its path by distance. */
fun SceneSpec.arcFraction(t: Float): Float = if (pathM == 0f) 0f else distanceAt(t) / pathM

/** Eye position and the point it looks at. */
fun SceneSpec.cameraAt(t: Float): Pair<Vec3, Vec3> {
    val s = distanceAt(t)
    val ahead = pointAt(camera.path, s + LOOK_AHEAD).at
    return pointAt(camera.path, s).at to ahead.copy(y = ahead.y - LOOK_DROP)
}

fun Car.poseAt(t: Float): Pose = pointAt(path, at(keys, t))

/** Lamps lit at [t]: the last phase starting at or before [t]. */
fun SceneSpec.signalAt(t: Float): Set<Lamp> = signal.lastOrNull { it.t <= t }?.lit ?: signal.firstOrNull()?.lit ?: emptySet()

/** 0 outdoors → 1 once the camera is 10 m past the roof line. */
fun SceneSpec.indoorAt(t: Float): Float = ((distanceAt(t) - indoorFromM) / INDOOR_FADE_M).coerceIn(0f, 1f)

private const val INDOOR_FADE_M = 10f

/** Canvas colours; materials not listed are not drawn by the fallback. */
private val FLAT = mapOf(
    Mat.ASPHALT to 0xFF31363DL, Mat.EPOXY to 0xFF2F5D4AL, Mat.CONCRETE to 0xFF8A8F96L,
    Mat.WHITE_PAINT to 0xFFE9EDF2L, Mat.YELLOW_PAINT to 0xFFF2C230L,
)

/** The Canvas fallback's view of this scene: ribbons as strips, walls as WALL strips, cues by arclength. */
fun SceneSpec.flat(): TemplateScene {
    val ground = surfaces.mapNotNull { r ->
        FLAT[r.mat]?.let { c -> Strip(subdivide(r.center.map { it.copy(y = it.y + r.lift) }), r.width, c) }
    }
    val walls = solids.filter { it.mat == Mat.WALL }.map { e ->
        val mid = (e.profile.minOf { it.first } + e.profile.maxOf { it.first }) / 2
        Strip(subdivide(offset(e.path, mid).map { it.copy(y = it.y + 1f) }), .3f, WALL)
    }
    return TemplateScene(ground + walls, camera.path, cues.map { (t, text) -> arcFraction(t) to text })
}
