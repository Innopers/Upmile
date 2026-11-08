package app.upmile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upmile.Graph
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.preview3d.RoadTemplates
import app.upmile.core.preview3d.arcLengths
import app.upmile.core.route.Destination
import app.upmile.core.route.SegmentFinder
import app.upmile.drive.DriveService
import app.upmile.preview3d.CanvasPreview
import app.upmile.ui.theme.Palette
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Preview pace, about 43 km/h: slow enough to take in each cue before the next one. */
private const val PREVIEW_MPS = 12f
/** The shortest template still gets time to speak its cues; the longest never drags past 40 s. */
private const val MIN_RUN_MS = 12_000L
private const val MAX_RUN_MS = 40_000L
private const val SLOW_RATE = .5f
/** A longer frame gap (paused app, jank) is not played through in one jump. */
private const val MAX_FRAME_MS = 100L

/** How long one run takes at full speed for a camera path of [pathM] metres. */
internal fun runMs(pathM: Float): Long = (pathM / PREVIEW_MPS * 1000f).toLong().coerceIn(MIN_RUN_MS, MAX_RUN_MS)

/** One frame's outcome: the new playback, cue lines that just became active, and whether this frame finished the run. */
internal class Step(val playback: Playback, val say: List<String>, val rehearsed: Boolean)

/** Playback of one segment. [cue] is the last cue reached (-1 before the first); restarting resets it, so cues speak again. */
internal data class Playback(val progress: Float = 0f, val playing: Boolean = true, val slow: Boolean = false, val cue: Int = -1) {
    val finished get() = progress >= 1f

    /** Plays [dtMs] of a [runMs] run and stops at the end. [cues] are (progress, text), in order. */
    fun step(dtMs: Long, runMs: Long, cues: List<Pair<Float, String>>): Step {
        if (!playing) return Step(this, emptyList(), false)
        val p = (progress + dtMs * (if (slow) SLOW_RATE else 1f) / runMs).coerceAtMost(1f)
        val next = copy(progress = p, playing = p < 1f, cue = cues.indexOfLast { it.first <= p })
        return Step(next, cues.subList(cue + 1, maxOf(cue, next.cue) + 1).map { it.second }, next.finished && !finished)
    }

    /** Pause or resume; play at the end starts over, as in the mockup. */
    fun toggled() = if (finished) restarted() else copy(playing = !playing)
    fun restarted() = Playback(slow = slow)
    fun slowToggled() = copy(slow = !slow)
}

internal fun nextSegment(index: Int, count: Int): Int? = (index + 1).takeIf { it < count }

/** Drops queued cue lines, unless a drive (or the 체험 모드 replay) is running: its lines share the queue. */
private fun quiet() { if (!DriveService.running.value && !Graph.demoDriving.value) Graph.voice.stop() }

/** Row hint and difficulty bars (of 3) per segment type. A hint, not a grade. */
private fun hint(s: HardSegment): Pair<String, Int> = when (s.type) {
    SegmentType.HIGHWAY_MERGE -> "가속 차로에서 속도 맞추기" to 3
    SegmentType.MULTILANE_LEFT -> "좌회전 차로 ${s.params["turnLanes"]?.toInt() ?: 2}개" to 2
    SegmentType.ROUNDABOUT -> "들어가기 전에 양보" to 2
    SegmentType.GARAGE_RAMP -> "급경사 내리막 · 좁은 회전" to 2
    SegmentType.ACCIDENT_SPOT -> "속도를 줄여 지나가기" to 2
    SegmentType.SHARP_CURVE -> "들어가기 전에 감속" to 1
}

private val PauseIcon = strokeIcon("M9 6v12", "M15 6v12", width = 3f)
private val PlayIcon = strokeIcon("M8 5l11 7-11 7z", width = 2.6f)
private val ReplayIcon = strokeIcon("M4 12a8 8 0 1 0 2.4-5.7", "M4 4v4.5h4.5")
private val ArrowIcon = strokeIcon("M12 20V5", "M6 11l6-6 6 6", width = 3f)
private val SpeakerIcon = strokeIcon("M11 5L6 9H3v6h3l5 4z", "M16 9a4 4 0 0 1 0 6", width = 2f)

private val RowShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(18.dp)
private val Glass = Color(0xB806090D) // mockup rgba(6,9,13,.72)

/** Step 3, 길 미리보기: today's destination, its hard segments, and a rehearsal of the selected one. */
@Composable
fun PreviewScreen(
    destination: Destination, onDestination: (Destination) -> Unit,
    rehearsed: Set<String>, onRehearsed: (String) -> Unit,
    onBack: () -> Unit, onNext: () -> Unit,
) {
    val route = remember(destination) { Graph.routes.route(destination.id) }
    val segments = remember(destination) { SegmentFinder.find(route, destination.hasGarage) }
    // starts on the first segment not yet rehearsed
    var selected by remember(destination) { mutableIntStateOf(segments.indexOfFirst { it.id !in rehearsed }.coerceAtLeast(0)) }
    DisposableEffect(Unit) { onDispose { quiet() } }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(480.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DestinationCard(destination, route, segments.size, onDestination)
            segments.forEachIndexed { i, s -> SegmentRow(i, s, i == selected, s.id in rehearsed) { selected = i } }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SpeakerIcon, null, Modifier.size(24.dp), tint = Palette.sub)
                Text("실제로 이 구간에 가까워지면 음성으로 다시 알려드려요", color = Palette.sub, fontSize = 18.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpButton("이전", onBack, Modifier.weight(1f))
                UpButton("다음 · 출발", onNext, Modifier.weight(1.6f), primary = true)
            }
        }
        val segment = segments.getOrNull(selected)
        if (segment == null) {
            Box(Modifier.weight(1f).fillMaxHeight().card(), contentAlignment = Alignment.Center) {
                Text("오늘 길엔 미리 볼 어려운 구간이 없어요", color = Palette.sub, fontSize = 24.sp)
            }
        } else {
            val next = nextSegment(selected, segments.size)
            Rehearsal(
                segment, selected, segments.size, onRehearsed, next?.let { { selected = it } },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DestinationCard(destination: Destination, route: Route, hardCount: Int, onDestination: (Destination) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button) { open = !open }.card(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("오늘 목적지")
            Spacer(Modifier.weight(1f))
            Text(if (open) "닫기" else "바꾸기", color = Palette.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(destination.name, Modifier.padding(top = 6.dp), fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(meta(route), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                buildAnnotatedString {
                    append("어려운 구간 ")
                    withStyle(SpanStyle(color = Palette.warn, fontWeight = FontWeight.Bold)) { append("${hardCount}곳") }
                },
                color = Palette.sub, fontSize = 20.sp,
            )
        }
    }
    if (open) {
        val choices = remember { Graph.routes.destinations().map { it to Graph.routes.route(it.id) } }
        choices.forEach { (d, r) ->
            val chosen = d.id == destination.id
            Row(
                Modifier.fillMaxWidth().clip(RowShape)
                    .background(if (chosen) Palette.accentSoft else Palette.surface3)
                    .border(1.5.dp, if (chosen) Palette.accent else Color.Transparent, RowShape)
                    .selectable(chosen, role = Role.RadioButton) { onDestination(d); open = false }
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.name, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(meta(r), color = Palette.sub, fontSize = 18.sp)
            }
        }
    }
}

private fun meta(r: Route) = "${(r.totalTimeS / 60.0).roundToInt()}분   ${String.format(Locale.US, "%.1f", r.totalDistanceM / 1000.0)}km"

@Composable
private fun SegmentRow(i: Int, s: HardSegment, selected: Boolean, seen: Boolean, onClick: () -> Unit) {
    val (text, level) = hint(s)
    Row(
        Modifier.fillMaxWidth().clip(RowShape)
            .background(
                if (selected) Brush.verticalGradient(listOf(Palette.accent.copy(alpha = .10f), Palette.accent.copy(alpha = .03f)))
                else SolidColor(Palette.surface2)
            )
            .border(1.5.dp, if (selected) Palette.accent else Palette.line, RowShape)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(if (selected) Palette.accent else Palette.surface3, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${i + 1}", color = if (selected) Palette.onAccent else Palette.text, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${s.place} · $text", Modifier.padding(top = 4.dp), color = Palette.sub, fontSize = 18.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { k -> Box(Modifier.size(22.dp, 7.dp).background(if (k < level) Palette.warn else Color(0xFF2A323D), CircleShape)) }
            }
        }
        if (seen) Text("✓ 봤어요", color = Palette.ok, fontSize = 18.sp)
    }
}

/** The 3D rehearsal with its HUD. Plays from a frame loop; each cue is spoken once as it comes up. */
@Composable
private fun Rehearsal(
    segment: HardSegment, index: Int, count: Int, onRehearsed: (String) -> Unit, onNextSegment: (() -> Unit)?, modifier: Modifier,
) {
    val scene = remember(segment) { RoadTemplates.build(segment) }
    val pathM = remember(scene) { arcLengths(scene.cameraPath).last() }
    var play by remember(segment) { mutableStateOf(Playback()) }
    val rehearsedNow by rememberUpdatedState(onRehearsed)
    LaunchedEffect(segment) {
        quiet() // the last segment's queued cues would only confuse this one
        var last = withFrameMillis { it }
        while (true) {
            if (!play.playing) { // paused or at the end: no frames until it plays again
                snapshotFlow { play.playing }.first { it }
                last = withFrameMillis { it }
            }
            withFrameMillis { now ->
                val s = play.step((now - last).coerceAtMost(MAX_FRAME_MS), runMs(pathM), scene.cues)
                last = now
                s.say.forEach { Graph.voice.say(it) }
                if (s.rehearsed) rehearsedNow(segment.id)
                play = s.playback
            }
        }
    }

    val shape = RoundedCornerShape(26.dp)
    Box(modifier.clip(shape).border(1.dp, Palette.line, shape)) {
        CanvasPreview(scene, play.progress, Modifier.fillMaxSize())
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("구간 ${index + 1}/$count") }
                    append(" · ${segment.title}")
                },
                Modifier.background(Glass, PillShape).border(1.dp, Color.White.copy(alpha = .08f), PillShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                fontSize = 21.sp,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.background(Glass, PillShape).border(1.dp, Color.White.copy(alpha = .08f), PillShape)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("${(pathM * (1 - play.progress)).roundToInt()}m", fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
                    Text("남은 거리", color = Palette.sub, fontSize = 18.sp)
                }
                if (play.playing) Row(
                    Modifier.background(Glass, PillShape).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceWave(listOf(8, 16, 11, 18, 9).map { (it * 1.3f).dp }, barWidth = 4.dp)
                    Text("음성 안내 중", color = Color(0xFFCFEEFF), fontSize = 18.sp)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val at = play.cue.coerceAtLeast(0)
            scene.cues.getOrNull(at)?.let { (_, text) -> Guide(text, scene.cues.getOrNull(at + 1)?.second) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlButton(onClick = { play = play.toggled() }, icon = if (play.playing) PauseIcon else PlayIcon, description = if (play.playing) "일시 정지" else "재생")
                ControlButton("다시 보기", { quiet(); play = play.restarted() }, icon = ReplayIcon)
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(play.progress).fillMaxHeight().background(Palette.accent, CircleShape))
                }
                ControlButton("천천히", { play = play.slowToggled() }, on = play.slow)
                if (onNextSegment != null) ControlButton("다음 구간 ›", onNextSegment)
            }
        }
    }
}

@Composable
private fun Guide(cue: String, then: String?) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC06090D), shape).border(1.dp, Palette.accent.copy(alpha = .35f), shape)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).background(Palette.accent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(ArrowIcon, null, Modifier.size(32.dp), tint = Palette.onAccent)
        }
        Column {
            Text(cue, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            if (then != null) Text("이어서 · $then", color = Palette.sub, fontSize = 18.sp)
        }
    }
}

/** Mockup `.cbtn`; [on] marks a toggle that is active. An icon-only button is named by [description]. */
@Composable
private fun ControlButton(
    text: String? = null, onClick: () -> Unit, icon: ImageVector? = null, on: Boolean = false, description: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.height(60.dp).clip(shape).background(Color(0xE6161B22))
            .border(if (on) 1.5.dp else 1.dp, if (on) Palette.accent else Palette.line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = if (on) Palette.accent else Palette.text
        if (icon != null) Icon(icon, description, Modifier.size(24.dp), tint = tone)
        if (text != null) Text(text, color = tone, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
