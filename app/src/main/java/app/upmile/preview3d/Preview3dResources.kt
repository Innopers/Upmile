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
import com.google.android.filament.Skybox
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
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

/** Filament objects every preview shares: built once on the main thread, kept for the process. */
internal class Preview3dResources private constructor(private val context: Context) {
    private val egl = SceneView.createEglContext()
    val engine: Engine = SceneView.createEngine(egl)
    val modelLoader = ModelLoader(engine, context)
    val materialLoader = MaterialLoader(engine, context)
    val environmentLoader = EnvironmentLoader(engine, context)
    /**
     * The photo lights the scene but is not shown: the background is [gradientSky]. Its sun is capped: the directional
     * light is the sun, and the photo's (72 000, two thirds of the IBL's light) would be a second, unshadowed one.
     */
    private val hdr = environmentLoader.createHDREnvironment(
        ByteBuffer.wrap(capRgbe(context.assets.open("preview3d/sky.hdr").use { it.readBytes() }, SKY_MAX_EXPONENT)),
        createSkybox = false,
    ) ?: error("sky.hdr did not load")
    /** The skybox and reflections intensity the HDR loader set; indoor dimming scales from these. */
    val iblIntensity = hdr.indirectLight?.intensity ?: 30_000f
    val environment: Environment = hdr.copy(skybox = gradientSky(iblIntensity * SUNNY_16))

    /**
     * [SKY_HORIZON] at and below the horizon, rising to [SKY_TOP] at [SKY_TOP_SIN], on a 32 px cubemap (faces +X -X +Y
     * -Y +Z -Z; on a side face row 0 is the top). The skybox shader multiplies its texels by the IBL's luminance
     * (intensity x exposure): they are divided by it here, and so the sky still dims with the garage's indirect light.
     */
    private fun gradientSky(iblLuminance: Float): Skybox {
        val n = 32
        val px = FloatArray(6 * n * n * 3)
        var i = 0
        for (face in 0 until 6) for (row in 0 until n) for (col in 0 until n) {
            val s = 2 * (col + .5f) / n - 1; val t = 1 - 2 * (row + .5f) / n
            val up = when (face) { 2 -> 1f; 3 -> -1f; else -> t }
            val k = (up / sqrt(1 + s * s + t * t) / SKY_TOP_SIN).coerceIn(0f, 1f)
            for (c in 0 until 3) px[i++] = (SKY_HORIZON[c] + (SKY_TOP[c] - SKY_HORIZON[c]) * k) / iblLuminance
        }
        val bytes = ByteBuffer.allocateDirect(4 * px.size).order(ByteOrder.nativeOrder()).also { it.asFloatBuffer().put(px) }
        val cube = Texture.Builder().width(n).height(n).levels(1).sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.RGB16F).build(engine)
        cube.setImage(engine, 0, 0, 0, 0, n, n, 6, Texture.PixelBufferDescriptor(bytes, Texture.Format.RGB, Texture.Type.FLOAT))
        return Skybox.Builder().environment(cube).build(engine)
    }

    /**
     * Filament's materials read v as 1 - v (OpenGL, bottom-left origin) while PropKit puts v = 0 at the top of a bitmap,
     * so every bitmap is uploaded upside down. The tiling ground textures do not care.
     */
    private fun texture(bitmap: Bitmap): Texture =
        ImageTexture.Builder().bitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { preScale(1f, -1f) }, false)).build(engine)
    private fun color(r: Float, g: Float, b: Float, metallic: Float = 0f, roughness: Float = .8f) =
        materialLoader.createColorInstance(Color(r, g, b), metallic = metallic, roughness = roughness)
    /**
     * A flat sRGB colour (the loader linearises it). The photo's blue sky light lifts and cools every surface: asphalt,
     * grass and the walls are set darker or warmer than the colour wanted on screen (tuned on emulator captures).
     */
    private fun matte(argb: Long, roughness: Float = .9f) = materialLoader.createColorInstance(Color(argb), metallic = 0f, roughness = roughness)
    private fun textured(t: Texture, roughness: Float) = materialLoader.createTextureInstance(t, roughness = roughness)
    private fun glow(r: Float, g: Float, b: Float): MaterialInstance =
        materialLoader.createUbershaderInstance(MaterialProvider.MaterialKey().apply { unlit = true })?.apply { setBaseColorFactor(colorOf(r, g, b, 1f)) }
            ?: color(r, g, b, roughness = 1f)

    private val concrete = matte(0xFFC9CBCC, .85f)
    private val hazardTex = texture(Bitmaps.hazard())
    val mats: Map<Mat, MaterialInstance> = mapOf(
        Mat.ASPHALT to matte(0xFF4A4E4C, .92f), Mat.GRASS to matte(0xFF8DA375, 1f),
        Mat.PAVING to matte(0xFFD9DADB, .85f), Mat.CONCRETE to concrete, Mat.EPOXY to matte(0xFF3D6E50, .35f), // epoxy.jpg's mean colour
        Mat.KERB to matte(0xFFC2C3C4, .8f), Mat.WHITE_PAINT to color(.92f, .93f, .94f, roughness = .6f), Mat.YELLOW_PAINT to color(.95f, .72f, .12f, roughness = .6f),
        Mat.BARRIER to concrete, Mat.STEEL to color(.72f, .74f, .76f, metallic = .8f, roughness = .35f),
        Mat.NOISE_WALL to matte(0xFFB9C7BD, .85f), Mat.WALL to concrete, Mat.CEILING to color(.5f, .5f, .52f, roughness = .9f),
        Mat.HAZARD to textured(hazardTex, .6f),
    )
    private val warmWall = matte(0xFFDAC29A)
    val parts: Map<Part, MaterialInstance> = mapOf(
        Part.POLE to color(.55f, .57f, .6f, metallic = .6f, roughness = .45f), Part.DARK to color(.06f, .06f, .07f, roughness = .5f),
        Part.BACK to color(.42f, .43f, .45f, metallic = .5f, roughness = .5f), Part.LAMP_WARM to glow(1f, .95f, .85f),
        Part.LAMP_OFF to color(.1f, .1f, .1f, roughness = .3f), Part.RED_ON to glow(1f, .12f, .08f), Part.YELLOW_ON to glow(1f, .7f, .05f),
        Part.ARROW_ON to glow(.1f, .95f, .55f), Part.GREEN_ON to glow(.1f, .95f, .55f),
        Part.FOLIAGE to matte(0xFF7FA16E), Part.BARK to matte(0xFF8A7A68, .95f),
        Part.OFFICE_FACADE to matte(0xFFA2A098), Part.APT_FACADE to warmWall, Part.SHOP_FACADE to warmWall,
        Part.ROOF to matte(0xFFBFC3C7), Part.MIRROR to color(.85f, .88f, .9f, metallic = 1f, roughness = .05f),
        Part.MIRROR_RIM to color(.95f, .45f, .1f, roughness = .5f), Part.HAZARD to textured(hazardTex, .6f),
        Part.CONCRETE to concrete, Part.STEEL to color(.72f, .74f, .76f, metallic = .8f, roughness = .35f),
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

/** 1 / (1.2 x 2^EV100) at the camera's default sunny-16 exposure (f/16, 1/125 s, ISO 100): EV100 = log2(16² x 125). */
private const val SUNNY_16 = 1f / 38_400f
/** Linear and exposed, before the Filmic tone map: on screen about #E3ECF2. The outdoor fog is this colour, so far things melt into the sky. */
internal val SKY_HORIZON = floatArrayOf(.854f, 1.194f, 1.62f)
/** On screen about #9EC3E6. */
private val SKY_TOP = floatArrayOf(.228f, .406f, .944f)
/** sin 20 degrees: the top colour shows below the top of the view. */
private const val SKY_TOP_SIN = .35f

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
