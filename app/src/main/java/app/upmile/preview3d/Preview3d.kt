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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.TemplateScene
import app.upmile.core.preview3d.arcFraction
import com.google.android.filament.Fence
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
        val r = Preview3dResources.get(context) ?: return@LaunchedEffect
        // SceneView blocks the main thread on a shared engine's backend when its surface is sized: let the GPU finish the sky first
        val idle = r.engine.createFence()
        try { while (idle.wait(Fence.Mode.FLUSH, 0) != Fence.FenceStatus.CONDITION_SATISFIED) withFrameNanos {} } finally { r.engine.destroyFence(idle) }
        Log.i(Preview3dResources.TAG, "backend idle ${SystemClock.elapsedRealtime() - started} ms after 3D start")
        res = r
    }
    var ready by remember { mutableStateOf(false) }
    /** No first frame in time: this screen keeps the Canvas, for every segment and replay (other screens may try again). */
    var timedOut by remember { mutableStateOf(false) }
    val built = remember(spec, res) {
        res?.takeUnless { Preview3dResources.failed || timedOut }?.let { r ->
            try {
                val t0 = SystemClock.elapsedRealtime()
                SceneNodes(spec, r).also { Log.i(Preview3dResources.TAG, "scene ${SystemClock.elapsedRealtime() - t0} ms, ${it.nodes.size} nodes") }
            } catch (t: Throwable) {
                Preview3dResources.markFailed("scene: $t"); null
            }
        }
    }
    DisposableEffect(built) { onDispose { built?.destroy() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(res, lifecycle) { // the clock starts with 3D and runs only while the screen is resumed (from 0 again on each resume)
        if (res == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (ready) return@repeatOnLifecycle
            delay(FIRST_FRAME_TIMEOUT_MS)
            if (!ready) { timedOut = true; Log.w(Preview3dResources.TAG, "no first 3D frame in $FIRST_FRAME_TIMEOUT_MS ms: this screen keeps the Canvas") }
        }
    }
    val alive = built != null && !Preview3dResources.failed && !timedOut
    val canvasAlpha by animateFloatAsState(if (ready && alive) 0f else 1f, tween(300), label = "canvasFade")
    Box(modifier) {
        if (alive) FilamentPreview(built!!, progress, res!!, onFirstFrame = {
            ready = true
            Log.i(Preview3dResources.TAG, "first 3D frame ${SystemClock.elapsedRealtime() - started} ms after 3D start")
        }, Modifier.fillMaxSize())
        if (canvasAlpha > 0f) CanvasPreview(flat, spec.arcFraction(progress), Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha })
        Spacer(Modifier.fillMaxSize().drawBehind {
            val box = Rect(Offset.Zero, size)
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(box); addRoundRect(RoundRect(box, CornerRadius(corner.toPx()))) }, cornerColor)
        })
    }
}

private const val FIRST_FRAME_TIMEOUT_MS = 10_000L
