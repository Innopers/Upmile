package app.upmile.preview3d

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import app.upmile.BuildConfig
import app.upmile.core.preview3d.Car
import app.upmile.core.preview3d.Lamp
import app.upmile.core.preview3d.Mat
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.cameraAt
import app.upmile.core.preview3d.indoorAt
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.poseAt
import app.upmile.core.preview3d.signalAt
import app.upmile.core.preview3d.times
import app.upmile.core.preview3d.unit
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.Fence
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.degrees
import io.github.sceneview.Scene
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Transform
import io.github.sceneview.model.Model
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.MeshNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tan

/** Everything one scene puts into Filament: the static scene, the other cars, the hood and the headlight. */
internal class SceneNodes(val spec: SceneSpec, private val res: Preview3dResources) {
    /** When this scene was built (in composition); its first drawn frame is timed from here. */
    val builtAt = SystemClock.elapsedRealtime()
    val nodes = mutableListOf<Node>()
    /** Lit-lamp nodes by lamp; shown when the signal lights that lamp. */
    val lampNodes = mutableMapOf<Lamp, MutableList<Node>>()
    private val buffers = mutableListOf<Pair<VertexBuffer, IndexBuffer>>()
    /** One glTF asset per car (its own material instances are not drawn: see [Preview3dResources.carPaint]). */
    private val assets = mutableListOf<Model>()
    private val cars = mutableListOf<Pair<Car, ModelNode>>()
    private lateinit var hood: Node
    private lateinit var headlight: LightNode
    private var fogK = Float.NaN

    init {
        try {
            MeshKit.build(spec).forEach { (mat, mesh) -> if (!mesh.isEmpty) nodes += node(mesh, res.mats.getValue(mat), cast = mat in CASTERS) }
            PropKit.build(spec.props).forEach { (batch, mesh) ->
                if (mesh.isEmpty) return@forEach
                val n = when (batch) {
                    is Batch.Of -> node(mesh, res.parts.getValue(batch.part), cast = batch.part !in GLOWS)
                    is Batch.Face -> node(mesh, res.face(batch.face), cast = false)
                }
                nodes += n
                (batch as? Batch.Of)?.part?.lamp()?.let { lampNodes.getOrPut(it) { mutableListOf() } += n }
            }
            hood = node(hoodMesh(), res.hoodPaint, cast = false).also { nodes += it }
            headlight = LightNode(
                res.engine,
                builder = LightManager.Builder(LightManager.Type.FOCUSED_SPOT).color(1f, .95f, .85f).intensity(HEADLIGHT_IDLE_LM).falloff(45f).spotLightCone(.2f, .45f).castShadows(false),
            ).also { nodes += it }
            val t0 = SystemClock.elapsedRealtime()
            spec.cars.forEach { car ->
                val instance = res.modelLoader.createModelInstance("preview3d/models/${car.model.file}.glb").also { assets += it.asset }
                // a car has nothing to do per frame (no animation, skin or async load), and leaving the screen SceneView
                // keeps drawing a few frames after this scene is destroyed: ModelNode.onFrame would read the freed asset (SIGSEGV)
                val n = object : ModelNode(instance, autoAnimate = false) { override fun onFrame(frameTimeNanos: Long) = Unit }
                    .apply { isShadowCaster = true; setMaterialInstance(res.carPaint(car.paint)) }
                nodes += n; cars += car to n
            }
            Log.i(Preview3dResources.TAG, "cars ${SystemClock.elapsedRealtime() - t0} ms for ${spec.cars.size}")
        } catch (e: Throwable) {
            destroy() // what was built so far
            throw e
        }
    }

    /** Uploads [mesh] straight from its arrays: per-vertex objects cost ~0.6 s per scene on the main thread. */
    private fun node(mesh: MeshData, mat: MaterialInstance, cast: Boolean): Node {
        val e = res.engine; val n = mesh.vertexCount
        val tangents = floats(4 * n)
        SurfaceOrientation.Builder().vertexCount(n).normals(floats(mesh.normals, 3 * n)).build().run { getQuatsAsFloat(tangents); destroy() }
        val streams = mutableListOf(
            Triple(VertexAttribute.POSITION, AttributeType.FLOAT3, floats(mesh.positions, 3 * n)),
            Triple(VertexAttribute.TANGENTS, AttributeType.FLOAT4, tangents),
            Triple(VertexAttribute.UV0, AttributeType.FLOAT2, floats(mesh.uvs, 2 * n)),
        )
        // the ubershader (glows, road text) also reads a vertex colour, multiplied in (so white), and a second uv set
        val required = mat.material.requiredAttributes
        if (VertexAttribute.COLOR in required) streams += Triple(VertexAttribute.COLOR, AttributeType.FLOAT4, floats(FloatArray(4 * n) { 1f }, 4 * n))
        if (VertexAttribute.UV1 in required) streams += Triple(VertexAttribute.UV1, AttributeType.FLOAT2, floats(2 * n))
        val vb = VertexBuffer.Builder().bufferCount(streams.size).vertexCount(n)
            .apply { streams.forEachIndexed { i, (a, type, _) -> attribute(a, i, type) } }
            .build(e)
        streams.forEachIndexed { i, (_, _, data) -> vb.setBufferAt(e, i, data) }
        val ib = IndexBuffer.Builder().indexCount(mesh.indexCount).bufferType(IndexBuffer.Builder.IndexType.UINT).build(e)
        ib.setBuffer(e, ByteBuffer.allocateDirect(4 * mesh.indexCount).order(ByteOrder.nativeOrder()).asIntBuffer().also { it.put(mesh.indices, 0, mesh.indexCount); (it as Buffer).rewind() })
        buffers += vb to ib
        return MeshNode(e, PrimitiveType.TRIANGLES, vb, ib, mesh.bounds(), mat) { castShadows(cast); receiveShadows(true) }
    }

    fun update(t: Float, camera: CameraNode, view: View, sun: LightNode) {
        val (eye, look) = spec.cameraAt(t)
        camera.position = Position(eye.x, eye.y, eye.z)
        camera.lookAt(Position(look.x, look.y, look.z))
        hood.transform = camera.transform // the camera has no parent: its local transform is its world transform
        cars.forEach { (car, n) -> val p = car.poseAt(t); n.transform = Transform(Position(p.at.x, p.at.y, p.at.z), carRotation(p.dir), CAR_SCALE) }
        val lit = spec.signalAt(t)
        lampNodes.forEach { (lamp, ns) -> ns.forEach { it.isVisible = lamp in lit } }
        val k = spec.indoorAt(t)
        // the environment is shared by every scene: set from the baseline each frame, so an outdoor scene (k = 0) undoes the garage's dimming
        sun.intensity = SUN_LUX * (1 - k)
        res.environment.indirectLight?.intensity = res.iblIntensity * (1 - .75f * k) // the skybox follows it (Skybox has no setIntensity in 1.68.2)
        headlight.apply {
            intensity = maxOf(k * HEADLIGHT_LM, HEADLIGHT_IDLE_LM)
            lightPosition = Position(eye.x, eye.y - .55f, eye.z)
            lightDirection = (look - eye).unit().let { Direction(it.x, it.y, it.z) }
        }
        if (fogK.isNaN() || abs(k - fogK) > .01f || (k != fogK && (k == 0f || k == 1f))) { view.fogOptions = fog(k); fogK = k }
    }

    fun destroy() {
        nodes.forEach { it.destroy() }
        assets.forEach(res.modelLoader::destroyModel)
        buffers.forEach { (v, i) -> res.engine.destroyVertexBuffer(v); res.engine.destroyIndexBuffer(i) }
    }

    private companion object {
        /** Kenney cars (1.5 m wide, 2.55..3.25 m long) to real proportions; z a little more, so the wheels stay round enough. */
        val CAR_SCALE = Scale(1.25f, 1.25f, 1.5f)
        /** Tuned on the emulator: at 150 000 lm the pool on the tunnel walls was invisible beyond a few metres, at 1.5 M it glared. */
        const val HEADLIGHT_LM = 600_000f
        /**
         * Never 0, in every scene: Filament leaves a zero-intensity light out, and lit materials switch to their
         * dynamic-lighting shader variants once a spot light shines. Compiled as the tunnel begins, that was a ~6 s
         * GPU stall mid-drive on the emulator; this way they compile with the first scene's first frame, behind the Canvas.
         */
        const val HEADLIGHT_IDLE_LM = 1f
        val CASTERS = setOf(Mat.BARRIER, Mat.STEEL, Mat.NOISE_WALL, Mat.WALL, Mat.CEILING, Mat.KERB)
        val GLOWS = setOf(Part.LAMP_WARM, Part.RED_ON, Part.YELLOW_ON, Part.ARROW_ON, Part.GREEN_ON)
        /** The sky at the horizon outdoors; dark grey in the tunnel. */
        val OUTDOOR_FOG = SKY_HORIZON
        val INDOOR_FOG = floatArrayOf(.07f, .07f, .08f)

        fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

        /**
         * Outdoor haze at k = 0, the dark tunnel at k = 1, blended between. A fixed colour, not fogColorFromIbl: a colour
         * sampled from the sky cannot blend into the tunnel's grey (indoors it turns the ceiling navy). Outdoors it starts at
         * 150 m: the scenes' near tall buildings (up to ~150 m) keep their colour, the ones beyond melt into the sky.
         */
        fun fog(k: Float) = View.FogOptions().apply {
            enabled = true
            distance = lerp(150f, 8f, k); density = lerp(.006f, .03f, k); maximumOpacity = lerp(.9f, .95f, k)
            heightFalloff = 0f      // even density: the garage floor is 5 m below y = 0, where Filament's default falloff of 1/m would thicken it 150x
            cutOffDistance = 500f   // the skybox (at the far plane) keeps its gradient
            color = FloatArray(3) { lerp(OUTDOOR_FOG[it], INDOOR_FOG[it], k) }
        }
    }
}

/**
 * Kenney cars face +Z: yaw to the heading, then pitch the nose by the climb. A yaw alone would leave a car level on
 * the garage's 12% ramp, nose or tail ~.25 m into the floor.
 */
internal fun carRotation(dir: Vec3): Quaternion =
    Quaternion.fromAxisAngle(Float3(y = 1f), degrees(atan2(dir.x, dir.z))) *
        Quaternion.fromAxisAngle(Float3(x = 1f), -degrees(atan2(dir.y, hypot(dir.x, dir.z))))

/**
 * The ego car's bonnet in camera space (-Z ahead, +Y up): 1.9 m wide, 0.9..2.6 m ahead, falling from .38 to .55 m
 * below the eye and crowned 3 cm in the middle. A 7 x 4 vertex grid with normals from the grid, facing up, plus a lip
 * down from its near edge: that edge is 23 degrees below the eye and the view's bottom 30, so the road showed under it.
 */
internal fun hoodMesh(): MeshData {
    val nx = 7; val nz = 4
    val p = List(nz) { j ->
        val f = j / (nz - 1f)
        List(nx) { i -> val x = -.95f + 1.9f * i / (nx - 1); Vec3(x, -.38f - .17f * f + .03f * (1 - (x / .95f) * (x / .95f)), -.9f - 1.7f * f) }
    }
    val m = MeshData()
    for (j in 0 until nz) for (i in 0 until nx) {
        val n = cross(p[j][minOf(i + 1, nx - 1)] - p[j][maxOf(i - 1, 0)], p[minOf(j + 1, nz - 1)][i] - p[maxOf(j - 1, 0)][i])
        m.vertex(p[j][i], (if (n.y < 0f) n * -1f else n).unit(), i / (nx - 1f), j / (nz - 1f))
    }
    val lip = m.vertexCount
    for (i in 0 until nx) m.vertex(p[0][i].copy(y = -.62f, z = -.72f), Vec3(m.normals[3 * i], m.normals[3 * i + 1], m.normals[3 * i + 2]), i / (nx - 1f), 0f)
    // x grows with i and the grid runs away (-z) with j: (i, j), (i + 1, j), (i + 1, j + 1) winds counter-clockwise seen from above
    fun quad(a: Int, b: Int, c: Int, d: Int) { m.triangle(a, b, c); m.triangle(a, c, d) }
    for (j in 0 until nz - 1) for (i in 0 until nx - 1) (j * nx + i).let { quad(it, it + 1, it + 1 + nx, it + nx) }
    for (i in 0 until nx - 1) quad(lip + i, lip + i + 1, i + 1, i)
    return m
}

private fun floats(size: Int): FloatBuffer = ByteBuffer.allocateDirect(4 * size).order(ByteOrder.nativeOrder()).asFloatBuffer()
// (as Buffer): FloatBuffer.rewind() only returns FloatBuffer from API 34; minSdk is 28
private fun floats(a: FloatArray, size: Int): FloatBuffer = floats(size).also { it.put(a, 0, size); (it as Buffer).rewind() }

private fun MeshData.bounds(): Box {
    val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
    for (i in 0 until 3 * vertexCount) { val k = i % 3; lo[k] = minOf(lo[k], positions[i]); hi[k] = maxOf(hi[k], positions[i]) }
    return Box((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2, (hi[0] - lo[0]) / 2, (hi[1] - lo[1]) / 2, (hi[2] - lo[2]) / 2)
}

/** The 3D view of [built] at [progress]; calls [onFirstFrame] once a frame has reached the screen. Null: 3D stopped, nothing to draw or update. */
@Composable
internal fun FilamentPreview(built: SceneNodes?, progress: Float, res: Preview3dResources, onFirstFrame: () -> Unit, modifier: Modifier) {
    val engine = res.engine
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)
    // SceneView re-applies a lens projection from these persisted values on every resize: a one-off setProjection would be lost
    val camera = rememberCameraNode(engine) { near = NEAR_M; far = FAR_M; focalLength = FOCAL_MM }
    val sun = rememberMainLightNode(engine) {
        lightDirection = Direction(.35f, -.77f, -.53f); intensity = SUN_LUX; isShadowCaster = true
    }
    val t by rememberUpdatedState(progress)
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val probe = remember { FrameProbe() }
    DisposableEffect(Unit) { onDispose { probe.fence?.let(engine::destroyFence) } }
    Scene(
        modifier = modifier,
        engine = engine, modelLoader = res.modelLoader, materialLoader = res.materialLoader, environmentLoader = res.environmentLoader,
        view = view, renderer = renderer, scene = scene, environment = res.environment,
        mainLightNode = sun, cameraNode = camera, childNodes = built?.nodes.orEmpty(), cameraManipulator = null,
        onViewCreated = {
            view.setShadowingEnabled(true)
            view.antiAliasing = View.AntiAliasing.FXAA
            view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = .08f }
        },
        onFrame = {
            if (built != null) try {
                built.update(t, camera, view, sun)
                if (BuildConfig.DEBUG) probe.fps(renderer.shouldRenderFrame())
                if (probe.drawn(built, engine)) firstFrame()
            } catch (e: Throwable) { // the view is emptied on the next composition and parked; until then the frames stay quiet
                if (!Preview3dResources.failed) Preview3dResources.markFailed("frame: $e")
            }
        },
    )
}

/** Per scene, a fence queued after its first frame's commands: it signals once the GPU has drawn that frame (shader compiles take seconds). */
private class FrameProbe {
    private var scene: SceneNodes? = null
    private var frames = 0
    private var firstOnFrame = 0L
    var fence: Fence? = null
        private set

    /** True on the frame at which [built]'s first frame is known to be on screen. */
    fun drawn(built: SceneNodes, engine: Engine): Boolean {
        if (built !== scene) { fence?.let(engine::destroyFence); fence = null; scene = built; frames = 0 }
        when (++frames) {
            1 -> firstOnFrame = SystemClock.elapsedRealtime()
            2 -> fence = engine.createFence()
            else -> fence?.takeIf { it.wait(Fence.Mode.DONT_FLUSH, 0) == Fence.FenceStatus.CONDITION_SATISFIED }?.let {
                engine.destroyFence(it); fence = null
                val now = SystemClock.elapsedRealtime()
                Log.i(Preview3dResources.TAG, "first frame drawn ${now - firstOnFrame} ms after the first onFrame")
                if (BuildConfig.DEBUG) Log.i(Preview3dResources.TAG, "scene firstFrameMs=${now - built.builtAt} (from its build)")
                return true
            }
        }
        return false
    }

    private var fpsFrom = 0L
    private var rendered = 0
    private var calls = 0

    /** Debug: every 2 s, frames per second the renderer drew (it skips frames while the GPU is behind) and onFrame calls per second. */
    fun fps(renders: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (fpsFrom == 0L) fpsFrom = now
        calls++; if (renders) rendered++
        if (now - fpsFrom >= 2000) {
            Log.i(Preview3dResources.TAG, "fps=${rendered * 1000 / (now - fpsFrom)} (onFrame ${calls * 1000 / (now - fpsFrom)}/s)")
            fpsFrom = now; rendered = 0; calls = 0
        }
    }
}

private const val SUN_LUX = 100_000f
private const val NEAR_M = .1f
private const val FAR_M = 600f
/** Filament's lens projection uses a 24 mm sensor height: tan(30 deg) = 12 / f gives a 60 degree vertical field of view. */
private val FOCAL_MM = 12.0 / tan(PI / 6)
