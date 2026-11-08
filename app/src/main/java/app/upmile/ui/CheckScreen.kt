package app.upmile.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
import app.upmile.core.check.CheckItem
import app.upmile.core.check.CheckResult
import app.upmile.core.check.CheckState
import app.upmile.core.check.VehicleCheck
import app.upmile.core.route.Destination
import app.upmile.core.routine.RoutinePlan
import app.upmile.core.routine.RoutineStep
import app.upmile.ui.theme.Palette
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val REVEAL_MS = 350L

/** What to say now: each WARN when it appears or turns WARN, and the seat-mirror tip once. Unchanged results stay silent. */
internal fun toSpeak(before: List<CheckResult>, now: List<CheckResult>): List<CheckResult> = now.filter { r ->
    (r.state == CheckState.WARN || r.item == CheckItem.SEAT_MIRROR) && before.none { it.item == r.item && it.state == r.state }
}

private val DoorIcon = strokeIcon("M4 4h12l4 6v10H4z", "M14 13h2")
private val TelltaleIcon = strokeIcon("M12 3l9 16H3z", "M12 10v4M12 17v.5")
private val EnergyIcon = strokeIcon("M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16", "M3 21h14M15 9l3 2v7a2 2 0 0 0 4 0V9l-3-3")
private val BeltIcon = strokeIcon("M14.4 5a2.4 2.4 0 1 1-4.8 0a2.4 2.4 0 1 1 4.8 0z", "M8 21l2-11h4l2 11M6 8l12 10")
private val MirrorIcon = strokeIcon("M5 6h5a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M12 9h4l4-3v10l-4-3")

/** Icon and the short name shown under each result's message. */
private fun look(item: CheckItem, routeKm: Double): Pair<ImageVector, String> = when (item) {
    CheckItem.DOORS -> DoorIcon to "도어 4개"
    CheckItem.TELLTALES -> TelltaleIcon to "계기판 경고등"
    CheckItem.ENERGY -> EnergyIcon to "연료·배터리 · 오늘 왕복 ${(routeKm * 2).roundToInt()}km"
    CheckItem.BELT -> BeltIcon to "운전석 안전벨트"
    CheckItem.SEAT_MIRROR -> MirrorIcon to "시트 · 미러"
}

/** Step 2, 차량 점검: results light up one by one, then follow the car live. Each WARN is spoken once. */
@Composable
fun CheckScreen(plan: RoutinePlan, destination: Destination, onBack: () -> Unit, onNext: () -> Unit) {
    val snapshot by Graph.signals.snapshots.collectAsStateWithLifecycle()
    val routeKm = remember(destination) { Graph.routes.route(destination.id).totalDistanceM / 1000.0 }
    val withMirror = RoutineStep.SEAT_MIRROR in plan.steps
    val results = VehicleCheck.run(snapshot, routeKm).filter { withMirror || it.item != CheckItem.SEAT_MIRROR }

    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(results.size) { delay(REVEAL_MS); shown++ } }
    val visible = results.take(shown)
    var said by remember { mutableStateOf(emptyList<CheckResult>()) }
    var lastLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        toSpeak(said, visible).forEach { Graph.voice.say(it.message); lastLine = it.message }
        said = visible
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        CarCard({ item -> visible.firstOrNull { it.item == item }?.state }, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            results.forEachIndexed { i, r ->
                val (icon, name) = look(r.item, routeKm)
                CheckRow(r, revealed = i < shown, icon, name)
            }
            lastLine?.let { VoiceLine(it) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpButton("이전", onBack, Modifier.weight(1f))
                UpButton("다음 · 길 미리보기", onNext, Modifier.weight(1.6f), primary = true)
            }
        }
    }
}

@Composable
private fun CheckRow(r: CheckResult, revealed: Boolean, icon: ImageVector, name: String) {
    val warn = revealed && r.state == CheckState.WARN
    val (tone, label) = when {
        !revealed -> Palette.dim to "확인 중…"
        r.state == CheckState.OK -> Palette.ok to "✓ 정상"
        r.state == CheckState.WARN -> Palette.warn to "확인해 주세요"
        else -> Palette.accent to "안내"
    }
    val alpha by animateFloatAsState(if (revealed) 1f else .35f, tween(400), label = "reveal")
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
            .background(Palette.surface2, shape)
            .border(if (warn) 1.5.dp else 1.dp, if (warn) Palette.warn.copy(alpha = .55f) else Palette.line, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).background(if (warn) Palette.warn.copy(alpha = .12f) else Palette.surface3, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(30.dp), tint = tone) }
        Column(Modifier.weight(1f)) {
            Text(r.message, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(name, Modifier.padding(top = 4.dp), color = Palette.sub, fontSize = 18.sp)
        }
        Text(label, color = tone, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** The line Upmile spoke last, as in the mockup's voice strip. */
@Composable
private fun VoiceLine(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Palette.accentSoft, RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceWave(listOf(8, 16, 11, 18, 9).map { (it * 1.4f).dp }, barWidth = 4.dp)
        Text("“$text”", color = Color(0xFFCFEEFF), fontSize = 21.sp)
    }
}

private val CarLine = Color(0xFF3A4554)

private fun carTone(s: CheckState?) = when (s) {
    CheckState.OK -> Palette.ok
    CheckState.WARN -> Palette.warn
    else -> CarLine
}

/** Top view in the mockup's 300-unit viewBox. The car reports doors only as all closed or not, so the four share one colour. */
@Composable
private fun CarCard(stateOf: (CheckItem) -> CheckState?, modifier: Modifier) {
    val doors = carTone(stateOf(CheckItem.DOORS))
    val telltales = carTone(stateOf(CheckItem.TELLTALES))
    val beltWarn = stateOf(CheckItem.BELT) == CheckState.WARN
    val pulse by rememberInfiniteTransition("belt").animateFloat(20f, 26f, infiniteRepeatable(tween(700), RepeatMode.Reverse), "r")
    Column(modifier.card(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxHeight(.8f).aspectRatio(300f / 400f)) {
            scale(size.width / 300f, pivot = Offset.Zero) {
                translate(top = -10f) {
                    drawRoundRect(Color(0xFF1A2029), Offset(60f, 20f), Size(180f, 380f), CornerRadius(76f))
                    drawRoundRect(CarLine, Offset(60f, 20f), Size(180f, 380f), CornerRadius(76f), style = Stroke(2f))
                    drawRoundRect(Color(0xFF0D1116), Offset(84f, 108f), Size(132f, 76f), CornerRadius(20f))
                    drawRoundRect(Color(0xFF0D1116), Offset(88f, 282f), Size(124f, 58f), CornerRadius(16f))
                    for (x in listOf(122f, 178f)) {
                        drawCircle(Color(0xFF2A3340), 15f, Offset(x, 210f))
                        drawCircle(Color(0xFF232A33), 13f, Offset(x, 262f))
                    }
                    for (x in listOf(60f, 240f)) for ((y0, y1) in listOf(150f to 220f, 235f to 297f)) {
                        drawLine(doors, Offset(x, y0), Offset(x, y1), 5f, StrokeCap.Round)
                    }
                    // warning lamp on the windshield
                    drawPath(Path().apply { moveTo(150f, 133f); lineTo(163f, 157f); lineTo(137f, 157f); close() }, telltales, style = Stroke(2.5f, join = StrokeJoin.Round))
                    drawLine(telltales, Offset(150f, 142f), Offset(150f, 149f), 2.5f, StrokeCap.Round)
                    drawCircle(telltales, 1.5f, Offset(150f, 153f))
                    if (beltWarn) {
                        drawCircle(Palette.warn, pulse, Offset(122f, 210f), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                    }
                }
            }
        }
        Text("도어 · 좌석 · 경고등", Modifier.padding(top = 20.dp), color = Palette.sub, fontSize = 19.sp)
    }
}
