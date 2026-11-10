package app.upmile.ui

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import app.upmile.Graph
import app.upmile.core.route.Destination
import app.upmile.core.route.SegmentFinder
import app.upmile.core.routine.Breathing
import app.upmile.core.routine.Mood
import app.upmile.core.routine.Phase
import app.upmile.core.routine.planFor
import app.upmile.ui.theme.Palette

private const val MUSIC = "music/calm_start.m4a"
private const val INHALE_MS = Breathing.INHALE_S * 1_000L
private const val CYCLE_MS = (Breathing.INHALE_S + Breathing.EXHALE_S) * 1_000L
private const val COLOR_FADE_MS = 1_200L // mockup: transition background 1.2s

/** The breath colour: the phase's colour, faded in from the previous phase's over the first 1.2 s after each flip. */
internal fun breathColor(elapsedMs: Long): Color {
    val now = Breathing.frameAt(elapsedMs)
    val pos = Math.floorMod(elapsedMs, CYCLE_MS)
    val intoPhase = if (pos < INHALE_MS) pos else pos - INHALE_MS
    if (intoPhase >= COLOR_FADE_MS) return Color(now.argb)
    val before = Breathing.frameAt(elapsedMs - intoPhase - 1)
    return lerp(Color(before.argb), Color(now.argb), intoPhase / COLOR_FADE_MS.toFloat())
}

/** The line under the mood chips: calm and honest, never a grade. */
internal fun moodLine(mood: Mood, hardCount: Int): String = when (mood) {
    Mood.VERY_NERVOUS -> "괜찮아요, 처음엔 다 그래요. 출발곡과 함께 ${planFor(mood).breatheSeconds / 60}분 동안 숨부터 천천히 골라요."
    Mood.A_LITTLE -> if (hardCount > 0) "좋아요. 오늘 어려운 구간 ${hardCount}곳을 미리 같이 봐요."
    else "좋아요. 오늘 길엔 미리 볼 어려운 구간이 없어요. 점검만 같이 해요."
    Mood.FINE -> if (hardCount > 0) "좋아요. 짧게 숨 고르고, 어려운 구간 ${hardCount}곳만 가볍게 짚고 가요."
    else "좋아요. 짧게 숨 고르고 바로 점검해요."
}

private val Moods = listOf(
    Triple(Mood.VERY_NERVOUS, "😣", "많이 떨려요"),
    Triple(Mood.A_LITTLE, "😐", "조금 긴장돼요"),
    Triple(Mood.FINE, "🙂", "괜찮아요"),
)

/** Step 1, 긴장 풀기: breathe with the light, pick a mood. Never advances by itself. */
@Composable
fun RoutineScreen(mood: Mood, onMood: (Mood) -> Unit, destination: Destination, onNext: () -> Unit) {
    val plan = planFor(mood)
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var cabinLit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { now ->
            elapsedMs = now - start
            val f = Breathing.frameAt(elapsedMs)
            cabinLit = Graph.cabin.ambient(f.brightness, breathColor(elapsedMs).toArgb()) // the screen's colour; the cabin layer throttles
        }
    }
    // Stopped (Home, another app) counts as leaving: no music, light or fan behind the user's back. Returning restarts them.
    LifecycleStartEffect(plan.playMusic) {
        if (plan.playMusic) Graph.music.play(MUSIC)
        Graph.cabin.quietFan()
        onStopOrDispose { Graph.music.fadeOutAndStop(); Graph.cabin.restore() }
    }
    val hardCount = remember(destination) { SegmentFinder.find(Graph.routes.route(destination.id), destination.hasGarage).size }
    val breathed by remember(plan) { derivedStateOf { elapsedMs >= plan.breatheSeconds * 1000L } }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        BreathCard({ elapsedMs }, cabinLit, Modifier.weight(1.25f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (plan.playMusic) MusicCard({ elapsedMs }, plan.breatheSeconds)
            MoodCard(mood, onMood, moodLine(mood, hardCount))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpButton("건너뛰기", onNext, Modifier.weight(1f))
                UpButton("다음 · 차량 점검", onNext, Modifier.weight(1.6f), primary = true, beckon = breathed)
            }
        }
    }
}

/** The breathing circle. When the cabin light can't follow, the glow ring on screen carries the light instead. */
@Composable
private fun BreathCard(elapsedMs: () -> Long, cabinLit: Boolean, modifier: Modifier) {
    // Only the drawing reads every frame; the words change at most once a second.
    val cue by remember { derivedStateOf { Breathing.frameAt(elapsedMs()).let { it.phase to it.secondsLeftInPhase } } }
    Column(modifier.card(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(540.dp)) {
                val frame = Breathing.frameAt(elapsedMs())
                val color = breathColor(elapsedMs())
                val level = EaseInOutSine.transform(
                    (frame.brightness - Breathing.MIN_BRIGHTNESS) / (Breathing.MAX_BRIGHTNESS - Breathing.MIN_BRIGHTNESS).toFloat()
                )
                val r = 230.dp.toPx()
                if (cabinLit) drawCircle(Color(0xFF273140), r, style = Stroke(1.5.dp.toPx()))
                else {
                    val outer = size.minDimension / 2
                    drawCircle(
                        Brush.radialGradient(
                            (r - 14.dp.toPx()) / outer to Color.Transparent, r / outer to color.copy(alpha = .2f + .5f * level),
                            1f to Color.Transparent, center = center, radius = outer,
                        ),
                        outer,
                    )
                    drawCircle(color.copy(alpha = .4f + .6f * level), r, style = Stroke(4.dp.toPx()))
                }
                // mockup .blob: a 300 px sphere scaled .62–1, lit from the upper left
                val br = 205.dp.toPx() * (.62f + .38f * level)
                drawCircle(
                    Brush.radialGradient(
                        0f to lerp(color, Color.White, .15f), .6f to lerp(color, Palette.bg, .45f), .72f to Color.Transparent,
                        center = center + Offset(-.2f * br, -.3f * br), radius = 1.77f * br,
                    ),
                    br, alpha = .75f + .25f * level,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (cue.first == Phase.INHALE) "들이쉬기" else "내쉬기", fontSize = 46.sp, fontWeight = FontWeight.SemiBold)
                Text("${cue.second}", color = Color(0xFFD9E6F2).copy(alpha = .85f), fontSize = 26.sp)
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).drawBehind {
                    val color = breathColor(elapsedMs())
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .6f), Color.Transparent)), size.minDimension * 1.1f)
                    drawCircle(color)
                }
            )
            Text(
                if (cabinLit) "실내 무드조명이 호흡 속도에 맞춰 바뀌고 있어요" else "화면의 빛을 따라 천천히 숨 쉬어요",
                color = Palette.sub, fontSize = 21.sp,
            )
        }
    }
}

@Composable
private fun MusicCard(elapsedMs: () -> Long, totalS: Int) {
    val s by remember(totalS) { derivedStateOf { (elapsedMs() / 1000).toInt().coerceAtMost(totalS) } }
    Column(Modifier.fillMaxWidth().card()) {
        Label("오늘의 출발곡")
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(120.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF1E3A5F), Palette.accent, Color(0xFFB6F3FF))), RoundedCornerShape(20.dp),
                )
            )
            Column(Modifier.weight(1f)) {
                Text("천천히, 괜찮아", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Text("잔잔한 앰비언트 · 출발 루틴 전용", Modifier.padding(top = 4.dp), color = Palette.sub, fontSize = 19.sp)
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF262E39))) {
                    Box(Modifier.fillMaxWidth(s / totalS.toFloat()).fillMaxHeight().background(Palette.text, CircleShape))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(s), color = Palette.dim, fontSize = 18.sp)
                    Text(clock(totalS), color = Palette.dim, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun clock(s: Int) = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

@Composable
private fun MoodCard(mood: Mood, onMood: (Mood) -> Unit, line: String) {
    Column(Modifier.fillMaxWidth().card()) {
        Label("지금 마음은 어때요?")
        Row(Modifier.padding(top = 18.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Moods.forEach { (m, face, text) ->
                val selected = m == mood
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.weight(1f).height(104.dp).clip(shape)
                        .background(if (selected) Palette.accentSoft else Palette.surface3)
                        .border(2.dp, if (selected) Palette.accent else Color.Transparent, shape)
                        .selectable(selected, role = Role.RadioButton) { onMood(m) },
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(face, fontSize = 30.sp)
                    Text(text, fontSize = 21.sp)
                }
            }
        }
        Text(line, Modifier.padding(top = 18.dp), color = Palette.mint, fontSize = 21.sp)
    }
}
