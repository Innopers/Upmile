package app.upmile.preview3d

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.TemplateScene
import app.upmile.core.preview3d.arcFraction
import kotlinx.coroutines.delay

/**
 * The rehearsal view: the 3D scene under the Canvas preview. The Canvas shows at once and fades out after the first
 * 3D frame; if 3D cannot start (or shows nothing within 10 s) the Canvas simply stays.
 * SurfaceView ignores Compose clipping, so [cornerColor] paints the outside of the rounded corners on top.
 */
@Composable
internal fun Preview3d(spec: SceneSpec, flat: TemplateScene, progress: Float, cornerColor: Color, corner: Dp, modifier: Modifier) {
    val context = LocalContext.current
    var res by remember { mutableStateOf<Preview3dResources?>(null) }
    var started by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        withFrameNanos {} // the Canvas reaches the screen before the engine, sky and textures block the main thread
        started = SystemClock.elapsedRealtime()
        res = Preview3dResources.get(context)
    }
    val built = remember(spec, res) {
        res?.let { r ->
            try {
                val t0 = SystemClock.elapsedRealtime()
                SceneNodes(spec, r).also { Log.i(Preview3dResources.TAG, "scene ${SystemClock.elapsedRealtime() - t0} ms, ${it.nodes.size} nodes") }
            } catch (t: Throwable) {
                Preview3dResources.markFailed("scene: $t"); null
            }
        }
    }
    DisposableEffect(built) { onDispose { built?.destroy() } }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(res) { // the first-frame clock starts with 3D
        if (res == null) return@LaunchedEffect
        delay(FIRST_FRAME_TIMEOUT_MS)
        if (!ready) Preview3dResources.markFailed("no first frame in $FIRST_FRAME_TIMEOUT_MS ms")
    }
    val alive = built != null && !Preview3dResources.failed
    val canvasAlpha by animateFloatAsState(if (ready && alive) 0f else 1f, tween(300), label = "canvasFade")
    Box(modifier) {
        if (alive) FilamentPreview(built!!, progress, res!!, onFirstFrame = {
            ready = true
            Log.i(Preview3dResources.TAG, "first 3D frame ${SystemClock.elapsedRealtime() - started} ms after the first Canvas frame")
        }, Modifier.fillMaxSize())
        if (canvasAlpha > 0f) CanvasPreview(flat, spec.arcFraction(progress), Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha })
        Spacer(Modifier.fillMaxSize().drawBehind {
            val box = Rect(Offset.Zero, size)
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(box); addRoundRect(RoundRect(box, CornerRadius(corner.toPx()))) }, cornerColor)
        })
    }
}

private const val FIRST_FRAME_TIMEOUT_MS = 10_000L
