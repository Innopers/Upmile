package app.upmile.preview3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.upmile.core.preview3d.Mat
import app.upmile.core.preview3d.Paint
import app.upmile.core.preview3d.PropKind
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.MaterialProvider
import io.github.sceneview.SceneView
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.material.setBaseColorFactor
import io.github.sceneview.material.setBaseColorIndex
import io.github.sceneview.material.setBaseColorMap
import io.github.sceneview.material.setMetallicFactor
import io.github.sceneview.material.setRoughnessFactor
import io.github.sceneview.math.colorOf
import io.github.sceneview.texture.ImageTexture
import java.nio.ByteBuffer
import kotlin.math.pow

/** Filament objects every preview shares: built once on the main thread, kept for the process. */
internal class Preview3dResources private constructor(private val context: Context) {
    private val egl = SceneView.createEglContext()
    val engine: Engine = SceneView.createEngine(egl)
    val modelLoader = ModelLoader(engine, context)
    val materialLoader = MaterialLoader(engine, context)
    val environmentLoader = EnvironmentLoader(engine, context)
    /** The photo's sun is capped: the directional light is the sun, and the photo's (72 000, two thirds of the IBL's light) would be a second, unshadowed one. */
    val environment: Environment = environmentLoader.createHDREnvironment(
        ByteBuffer.wrap(capRgbe(context.assets.open("preview3d/sky.hdr").use { it.readBytes() }, SKY_MAX_EXPONENT)),
    ) ?: error("sky.hdr did not load")
    /** The skybox and reflections intensity the HDR loader set; indoor dimming scales from these. */
    val iblIntensity = environment.indirectLight?.intensity ?: 30_000f

    /**
     * Filament's materials read v as 1 - v (OpenGL, bottom-left origin) while PropKit puts v = 0 at the top of a bitmap,
     * so every bitmap is uploaded upside down. The tiling ground textures do not care.
     */
    private fun texture(bitmap: Bitmap): Texture =
        ImageTexture.Builder().bitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { preScale(1f, -1f) }, false)).build(engine)
    private fun asset(name: String) = texture(context.assets.open("preview3d/textures/$name.jpg").use(BitmapFactory::decodeStream))
    private fun color(r: Float, g: Float, b: Float, metallic: Float = 0f, roughness: Float = .8f) =
        materialLoader.createColorInstance(Color(r, g, b), metallic = metallic, roughness = roughness)
    private fun textured(t: Texture, roughness: Float, metallic: Float = 0f) = materialLoader.createTextureInstance(t, roughness = roughness, metallic = metallic)
    private fun glow(r: Float, g: Float, b: Float): MaterialInstance =
        materialLoader.createUbershaderInstance(MaterialProvider.MaterialKey().apply { unlit = true })?.apply { setBaseColorFactor(colorOf(r, g, b, 1f)) }
            ?: color(r, g, b, roughness = 1f)

    private val concrete = asset("concrete")
    private val hazardTex = texture(Bitmaps.hazard())
    val mats: Map<Mat, MaterialInstance> = mapOf(
        Mat.ASPHALT to textured(asset("asphalt"), .92f), Mat.GRASS to textured(asset("grass"), 1f),
        Mat.PAVING to textured(asset("paving"), .85f), Mat.CONCRETE to textured(concrete, .8f), Mat.EPOXY to textured(asset("epoxy"), .35f),
        Mat.KERB to color(.62f, .62f, .6f), Mat.WHITE_PAINT to color(.92f, .93f, .94f, roughness = .6f), Mat.YELLOW_PAINT to color(.95f, .72f, .12f, roughness = .6f),
        Mat.BARRIER to textured(concrete, .8f), Mat.STEEL to color(.72f, .74f, .76f, metallic = .8f, roughness = .35f),
        Mat.NOISE_WALL to color(.55f, .62f, .58f, roughness = .7f), Mat.WALL to textured(concrete, .85f), Mat.CEILING to color(.5f, .5f, .52f, roughness = .9f),
        Mat.HAZARD to textured(hazardTex, .6f),
    )
    val parts: Map<Part, MaterialInstance> = mapOf(
        Part.POLE to color(.55f, .57f, .6f, metallic = .6f, roughness = .45f), Part.DARK to color(.06f, .06f, .07f, roughness = .5f),
        Part.BACK to color(.42f, .43f, .45f, metallic = .5f, roughness = .5f), Part.LAMP_WARM to glow(1f, .95f, .85f),
        Part.LAMP_OFF to color(.1f, .1f, .1f, roughness = .3f), Part.RED_ON to glow(1f, .12f, .08f), Part.YELLOW_ON to glow(1f, .7f, .05f),
        Part.ARROW_ON to glow(.1f, .95f, .55f), Part.GREEN_ON to glow(.1f, .95f, .55f),
        Part.FOLIAGE to color(.2f, .33f, .16f, roughness = .9f), Part.BARK to color(.3f, .22f, .15f, roughness = .95f),
        Part.GLASS_FACADE to textured(texture(Bitmaps.facade(Part.GLASS_FACADE)), .2f, metallic = .3f),
        Part.APT_FACADE to textured(texture(Bitmaps.facade(Part.APT_FACADE)), .8f),
        Part.SHOP_FACADE to textured(texture(Bitmaps.facade(Part.SHOP_FACADE)), .6f),
        Part.ROOF to color(.45f, .45f, .47f), Part.MIRROR to color(.85f, .88f, .9f, metallic = 1f, roughness = .05f),
        Part.MIRROR_RIM to color(.95f, .45f, .1f, roughness = .5f), Part.HAZARD to textured(hazardTex, .6f),
        Part.CONCRETE to textured(concrete, .8f), Part.STEEL to color(.72f, .74f, .76f, metallic = .8f, roughness = .35f),
    )
    private val faces = HashMap<SignFace, MaterialInstance>()
    /** One material per distinct sign; road text is see-through around its letters. */
    fun face(f: SignFace): MaterialInstance = faces.getOrPut(f) {
        val t = texture(Bitmaps.sign(context, f))
        if (f.kind == PropKind.ROAD_TEXT) masked(t) else materialLoader.createTextureInstance(t, roughness = .5f)
    }

    /**
     * Alpha-tested, not blended: Filament's transparent blending keeps the specular where alpha is 0, so a see-through
     * quad lays a second sky sheen over the floor at grazing angles.
     */
    private fun masked(t: Texture): MaterialInstance =
        materialLoader.createUbershaderInstance(MaterialProvider.MaterialKey().apply { hasBaseColorTexture = true; alphaMode = ALPHA_MASK })?.apply {
            setBaseColorMap(t); setBaseColorIndex(0)
            setParameter("baseColorUvMatrix", MaterialInstance.FloatElement.MAT3, FLIP_V, 0, 1) // glTF materials read v as is: undo the upload flip
            setBaseColorFactor(colorOf(1f, 1f, 1f, 1f)); setMetallicFactor(0f); setRoughnessFactor(.6f); setMaskThreshold(.5f)
        } ?: materialLoader.createTextureInstance(t, isOpaque = false, roughness = .5f)
    val hoodPaint: MaterialInstance = color(.05f, .06f, .07f, metallic = .6f, roughness = .25f)

    private val carColormap by lazy { context.assets.open("preview3d/models/Textures/colormap.png").use(BitmapFactory::decodeStream) }
    private val carPaints = HashMap<Paint, MaterialInstance>()
    /**
     * A car body in [p]: the Kenney colour map (body cells near-white, glass and trim dark) times the paint, in the
     * scene's own textured material. The cars' glTF ubershader would add 2.5–3.4 s of shader compiles to the first
     * 3D frame on the emulator. The map is 8-bit sRGB, so each linear multiplier is applied as k^(1/2.2).
     */
    fun carPaint(p: Paint): MaterialInstance = carPaints.getOrPut(p) {
        val src = carColormap
        val tinted = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val gamma = { k: Float -> k.pow(1 / 2.2f) }
        Canvas(tinted).drawBitmap(src, 0f, 0f, android.graphics.Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(gamma(p.r), gamma(p.g), gamma(p.b), 1f) })
        })
        textured(texture(tinted), roughness = .5f)
    }

    companion object {
        const val TAG = "Preview3d"
        private var shared: Preview3dResources? = null

        /** True once anything failed: the 3D view leaves and the Canvas preview stays. Compose-observable. */
        var failed by mutableStateOf(false)
            private set

        /** The shared resources, or null once anything failed (the Canvas preview stays). Main thread only. */
        fun get(context: Context): Preview3dResources? {
            if (failed) return null
            return shared ?: try {
                val start = SystemClock.elapsedRealtime()
                Preview3dResources(context.applicationContext).also {
                    shared = it
                    Log.i(TAG, "resources ${SystemClock.elapsedRealtime() - start} ms")
                }
            } catch (t: Throwable) { // UnsatisfiedLinkError, GL errors, missing assets: fall back, never crash the screen
                markFailed("resources: $t"); null
            }
        }

        fun markFailed(reason: String) { failed = true; Log.w(TAG, "3D preview off, Canvas stays: $reason") }
    }
}

/** gltfio's AlphaMode.MASK. */
private const val ALPHA_MASK = 1
/** uv' = (u, 1 - v), column-major. */
private val FLIP_V = floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 1f, 1f)

/** RGBE exponent 133 = values below 32; the sky and clouds stay under 10, only the sun disc is above. */
private const val SKY_MAX_EXPONENT = 133

/**
 * Caps every pixel of a run-length encoded Radiance .hdr at [maxExponent] in place: only the exponent channel's bytes
 * change, so the runs stay valid and the hue is kept. Returns [hdr].
 */
internal fun capRgbe(hdr: ByteArray, maxExponent: Int): ByteArray {
    var p = 0
    fun line(): String { val s = p; while (hdr[p] != '\n'.code.toByte()) p++; return String(hdr, s, p++ - s, Charsets.US_ASCII) }
    var res = line()
    while (!res.startsWith("-Y")) res = line()
    val (h, w) = res.split(' ').let { it[1].toInt() to it[3].toInt() }
    fun cap(i: Int) { if ((hdr[i].toInt() and 0xFF) > maxExponent) hdr[i] = maxExponent.toByte() }
    repeat(h) {
        check(hdr[p].toInt() == 2 && hdr[p + 1].toInt() == 2) { "not a run-length encoded scanline at $p" }
        p += 4
        for (channel in 0 until 4) {
            var x = 0
            while (x < w) {
                var n = hdr[p++].toInt() and 0xFF
                if (n > 128) { n -= 128; if (channel == 3) cap(p); p++ }
                else { if (channel == 3) for (i in p until p + n) cap(i); p += n }
                x += n
            }
        }
    }
    return hdr
}
