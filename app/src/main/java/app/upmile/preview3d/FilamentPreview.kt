package app.upmile.preview3d

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import app.upmile.core.preview3d.Lamp
import app.upmile.core.preview3d.Mat
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.cameraAt
import app.upmile.core.preview3d.indoorAt
import app.upmile.core.preview3d.signalAt
import com.google.android.filament.Box
import com.google.android.filament.Fence
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import io.github.sceneview.Scene
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.MeshNode
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
import kotlin.math.tan

/** Everything one scene puts into Filament. Task 10 adds cars, lamps, hood, headlight and indoor dimming to [update]. */
internal class SceneNodes(val spec: SceneSpec, private val res: Preview3dResources) {
    val nodes = mutableListOf<Node>()
    /** Lit-lamp nodes by lamp; shown when the signal lights that lamp. */
    val lampNodes = mutableMapOf<Lamp, MutableList<Node>>()
    private val buffers = mutableListOf<Pair<VertexBuffer, IndexBuffer>>()
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
        } catch (t: Throwable) {
            destroy() // what was built so far
            throw t
        }
    }

    /** Uploads [mesh] straight from its arrays: per-vertex objects cost ~0.6 s per scene on the main thread. */
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

    fun update(t: Float, camera: CameraNode, view: View) {
        val (eye, look) = spec.cameraAt(t)
        camera.position = Position(eye.x, eye.y, eye.z)
        camera.lookAt(Position(look.x, look.y, look.z))
        val lit = spec.signalAt(t)
        lampNodes.forEach { (lamp, ns) -> ns.forEach { it.isVisible = lamp in lit } }
        val k = spec.indoorAt(t)
        if (fogK.isNaN() || abs(k - fogK) > .01f || (k != fogK && (k == 0f || k == 1f))) { view.fogOptions = fog(k); fogK = k }
    }

    fun destroy() {
        nodes.forEach { it.destroy() }
        buffers.forEach { (v, i) -> res.engine.destroyVertexBuffer(v); res.engine.destroyIndexBuffer(i) }
    }

    private companion object {
        val CASTERS = setOf(Mat.BARRIER, Mat.STEEL, Mat.NOISE_WALL, Mat.WALL, Mat.CEILING, Mat.KERB)
        val GLOWS = setOf(Part.LAMP_WARM, Part.RED_ON, Part.YELLOW_ON, Part.ARROW_ON, Part.GREEN_ON)
        /** The sky at the horizon (sky.hdr 0..5 degrees, at the sunny-16 exposure) outdoors; dark grey in the tunnel. */
        val OUTDOOR_FOG = floatArrayOf(.34f, .37f, .45f)
        val INDOOR_FOG = floatArrayOf(.07f, .07f, .08f)

        fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

        /**
         * Outdoor haze at k = 0, the dark tunnel at k = 1, blended between. A fixed colour, not fogColorFromIbl: a colour
         * sampled from the sky cannot blend into the tunnel's grey (indoors it turns the ceiling navy).
         */
        fun fog(k: Float) = View.FogOptions().apply {
            enabled = true
            distance = lerp(40f, 8f, k); density = lerp(.004f, .03f, k); maximumOpacity = lerp(.85f, .95f, k)
            heightFalloff = 0f      // even density: the garage floor is 5 m below y = 0, where Filament's default falloff of 1/m would thicken it 150x
            cutOffDistance = 500f   // the skybox (at the far plane) keeps its clouds
            color = FloatArray(3) { lerp(OUTDOOR_FOG[it], INDOOR_FOG[it], k) }
        }
    }
}

private fun floats(size: Int): FloatBuffer = ByteBuffer.allocateDirect(4 * size).order(ByteOrder.nativeOrder()).asFloatBuffer()
// (as Buffer): FloatBuffer.rewind() only returns FloatBuffer from API 34; minSdk is 28
private fun floats(a: FloatArray, size: Int): FloatBuffer = floats(size).also { it.put(a, 0, size); (it as Buffer).rewind() }

private fun MeshData.bounds(): Box {
    val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
    for (i in 0 until 3 * vertexCount) { val k = i % 3; lo[k] = minOf(lo[k], positions[i]); hi[k] = maxOf(hi[k], positions[i]) }
    return Box((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2, (hi[0] - lo[0]) / 2, (hi[1] - lo[1]) / 2, (hi[2] - lo[2]) / 2)
}

/** The 3D view of [built] at [progress]; calls [onFirstFrame] once a frame has reached the screen. */
@Composable
internal fun FilamentPreview(built: SceneNodes, progress: Float, res: Preview3dResources, onFirstFrame: () -> Unit, modifier: Modifier) {
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
    val frames = remember { intArrayOf(0) }
    val firstOnFrame = remember { longArrayOf(0L) }
    // queued after the first frame's commands: it signals once the GPU has drawn that frame (shader compiles take seconds)
    val fence = remember { arrayOfNulls<Fence>(1) }
    DisposableEffect(Unit) { onDispose { fence[0]?.let(engine::destroyFence) } }
    Scene(
        modifier = modifier,
        engine = engine, modelLoader = res.modelLoader, materialLoader = res.materialLoader, environmentLoader = res.environmentLoader,
        view = view, renderer = renderer, scene = scene, environment = res.environment,
        mainLightNode = sun, cameraNode = camera, childNodes = built.nodes, cameraManipulator = null,
        onViewCreated = {
            view.setShadowingEnabled(true)
            view.antiAliasing = View.AntiAliasing.FXAA
            view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = .08f }
        },
        onFrame = {
            try {
                built.update(t, camera, view)
                when (++frames[0]) {
                    1 -> firstOnFrame[0] = SystemClock.elapsedRealtime()
                    2 -> fence[0] = engine.createFence()
                    else -> fence[0]?.takeIf { it.wait(Fence.Mode.DONT_FLUSH, 0) == Fence.FenceStatus.CONDITION_SATISFIED }?.let {
                        engine.destroyFence(it); fence[0] = null
                        Log.i(Preview3dResources.TAG, "first frame drawn ${SystemClock.elapsedRealtime() - firstOnFrame[0]} ms after the first onFrame")
                        firstFrame()
                    }
                }
            } catch (t: Throwable) { // the 3D view leaves on the next composition; until then the frames stay quiet
                if (!Preview3dResources.failed) Preview3dResources.markFailed("frame: $t")
            }
        },
    )
}

private const val SUN_LUX = 100_000f
private const val NEAR_M = .1f
private const val FAR_M = 600f
/** Filament's lens projection uses a 24 mm sensor height: tan(30 deg) = 12 / f gives a 60 degree vertical field of view. */
private val FOCAL_MM = 12.0 / tan(PI / 6)
