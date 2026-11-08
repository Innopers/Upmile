package app.upmile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upmile.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos

private val CardShape = RoundedCornerShape(26.dp)
private val ButtonShape = RoundedCornerShape(20.dp)

/** Mockup `.card`: surface2 with a thin line border. */
fun Modifier.card(padding: Dp = 28.dp): Modifier =
    background(Palette.surface2, CardShape).border(1.dp, Palette.line, CardShape).padding(padding)

/** Mockup `.label`. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = Palette.sub, fontSize = 18.sp, letterSpacing = .4.sp)

/** Mockup `.btn`: accent when [primary], else ghost. [beckon] pulses a ring around it to draw the eye. */
@Composable
fun UpButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, beckon: Boolean = false) {
    val ring = if (beckon) {
        rememberInfiniteTransition("beckon").animateFloat(.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "ring")
    } else null
    Box(
        modifier
            .height(76.dp)
            .drawBehind {
                val a = ring?.value ?: return@drawBehind
                val out = 9.dp.toPx()
                drawRoundRect(
                    Palette.accent.copy(alpha = a), Offset(-out, -out), Size(size.width + 2 * out, size.height + 2 * out),
                    CornerRadius(29.dp.toPx()), style = Stroke(4.dp.toPx()),
                )
            }
            .clip(ButtonShape)
            .background(if (primary) Palette.accent else Palette.surface3)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Palette.onAccent else Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** A 24-unit stroke icon from SVG path data, so the mockup's icons carry over as-is. Tint it with `Icon`. */
fun strokeIcon(vararg paths: String, width: Float = 2.4f): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach {
            addPath(
                addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Mockup `.wave`: accent bars rising and falling while Upmile speaks. */
@Composable
fun VoiceWave(heights: List<Dp>, barWidth: Dp, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition("wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), "t")
    Canvas(modifier.size(barWidth * (2 * heights.size - 1), heights.max())) {
        val w = barWidth.toPx()
        heights.forEachIndexed { i, h ->
            val k = 1f - .65f * (.5f - .5f * cos(2 * PI * (t + i * .37f)).toFloat()) // CSS keyframe: scaleY .35 at mid-cycle
            val bh = h.toPx() * k
            drawRoundRect(Palette.accent, Offset(i * 2 * w, (size.height - bh) / 2), Size(w, bh), CornerRadius(w / 2))
        }
    }
}
