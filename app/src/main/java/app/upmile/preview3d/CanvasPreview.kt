package app.upmile.preview3d

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import app.upmile.core.preview3d.TemplateScene
import app.upmile.ui.theme.Palette

/** The road seen from the driver's seat at [progress] (arclength fraction 0..1 along [TemplateScene.cameraPath]). */
@Composable
fun CanvasPreview(scene: TemplateScene, progress: Float, modifier: Modifier) {
    // shortcut: an empty road-coloured surface; Task 12 replaces this body with the perspective renderer.
    Box(modifier.background(Color(0xFF1B1F24)), contentAlignment = Alignment.Center) {
        Text("3D 미리보기를 준비하고 있어요", color = Palette.sub, fontSize = 22.sp)
    }
}
