package app.upmile.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
import app.upmile.core.model.HardSegment
import app.upmile.core.record.ExperienceCard
import app.upmile.core.record.ExperienceType
import app.upmile.core.record.LevelCalculator
import app.upmile.core.route.SegmentFinder
import app.upmile.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RecordScreen"
private const val ATTRIBUTION = "시·군·구 경계: 통계청 SGIS 행정구역 경계(공공누리 1유형)를 가공한 vuski/admdongkor (CC BY 4.0)"

/** Revisit ids ("<routeId>:<type>:<guideIndex>") as "title · place", found among [segments]; an unknown id shows as is. */
internal fun revisitLabels(ids: List<String>, segments: List<HardSegment>): List<String> {
    val byId = segments.associateBy { it.id }
    return ids.map { id -> byId[id]?.let { "${it.title} · ${it.place}" } ?: id }
}

private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { "${it.monthValue}월 ${it.dayOfMonth}일" }

/** 기록: level, the experience cards, stamps, regions and places to rehearse again. No scores, no ranks. */
@Composable
fun RecordScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val record by Graph.store.records.collectAsStateWithLifecycle()
    // Shown means seen: clear the unseen drive on appearance (or when one lands while shown), off the main thread.
    LaunchedEffect(record.unseenSessionId) {
        if (record.unseenSessionId == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try { Graph.store.update { it.copy(unseenSessionId = null) }; Log.i(TAG, "unseen drive cleared") }
            catch (e: Exception) { Log.w(TAG, "cannot clear the unseen drive", e) }
        }
    }
    val cards = record.cards.associateBy { it.type }
    val n = record.cards.size
    val next = LevelCalculator.thresholds.firstOrNull { it > n }
    val from = LevelCalculator.thresholds.last { it <= n }
    val revisit = remember(record.revisitSegments) {
        if (record.revisitSegments.isEmpty()) emptyList()
        else revisitLabels(record.revisitSegments, Graph.routes.destinations().flatMap { SegmentFinder.find(Graph.routes.route(it.id), it.hasGarage) })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("기록", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "Lv.${LevelCalculator.level(record)}", Modifier.background(Palette.accentSoft, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                color = Palette.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Column(Modifier.width(380.dp)) {
                Text(
                    "경험 카드 ${n}장 · " + if (next == null) "모든 레벨을 채웠어요" else "다음 레벨까지 ${next - n}장",
                    color = Palette.sub, fontSize = 19.sp,
                )
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFF262E39))) {
                    Box(Modifier.fillMaxWidth(if (next == null) 1f else (n - from) / (next - from).toFloat()).fillMaxHeight().background(Palette.accent, CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            UpButton("이전", onBack, Modifier.width(200.dp))
            UpButton("처음으로", onHome, Modifier.width(260.dp), primary = true)
        }
        if (LevelCalculator.graduated(record)) Graduation()
        if (record.cards.isEmpty()) Text("아직 기록이 없어요. 첫 주행을 다녀오면 경험 카드가 여기에 하나씩 쌓여요.", color = Palette.mint, fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row {
                    Label("경험 카드")
                    Spacer(Modifier.weight(1f))
                    Text("${cards.size} / ${ExperienceType.entries.size}", color = Palette.sub, fontSize = 18.sp)
                }
                ExperienceType.entries.chunked(4).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { ExperienceTile(it, cards[it], Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("구간 도장") {
                    if (record.stamps.isEmpty()) Note("미리 본 구간을 실제로 지나가면 도장이 찍혀요.")
                    // one row per segment: the same commute stamps the same segments every day
                    record.stamps.groupBy { it.segmentId }.values.sortedByDescending { g -> g.maxOf { it.atMs } }.forEach { g ->
                        val s = g.maxBy { it.atMs }
                        Column {
                            Text("${s.title} · ${s.place}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val times = if (g.size > 1) listOf("${g.size}번 지나감") else emptyList()
                            Text((g.flatMap { it.conditions }.distinct().map { it.label } + day(s.atMs) + times).joinToString(" · "), color = Palette.sub, fontSize = 18.sp)
                        }
                    }
                }
                Section("지역 지도") { Regions(record.regions.map { it.name }) }
                Section("다음에 한 번 더 미리 달려 볼 곳") {
                    if (revisit.isEmpty()) Note("지금은 없어요.")
                    revisit.forEach { Text(it, fontSize = 20.sp) }
                }
            }
        }
    }
}

@Composable
private fun Graduation() {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF12324A), Color(0xFF0F2A24))), shape)
            .border(1.dp, Palette.mint.copy(alpha = .35f), shape).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎓", fontSize = 60.sp)
        Column {
            Text("초보 탈출, 축하해요", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("열한 가지 첫 경험을 모두 해냈어요. 이제 어느 길이든 오늘처럼 천천히 다녀오면 돼요.", Modifier.padding(top = 6.dp), color = Palette.sub, fontSize = 21.sp)
        }
    }
}

@Composable
private fun ExperienceTile(type: ExperienceType, card: ExperienceCard?, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val name = type.look.first
    Column(
        modifier.fillMaxHeight().heightIn(min = 190.dp).background(if (card != null) Palette.surface2 else Palette.surface, shape)
            .border(1.dp, Palette.line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medal(type, got = card != null, size = 52.dp)
            Spacer(Modifier.weight(1f))
            if (card != null) Text("★".repeat(card.stars), color = Palette.warn, fontSize = 22.sp)
        }
        Text(name, color = if (card != null) Palette.text else Palette.dim, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        if (card == null) return@Column Text("아직 비어 있어요", color = Palette.dim, fontSize = 18.sp)
        if (card.place.isNotBlank()) Text(card.place, color = Palette.sub, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
        Text(day(card.firstAtMs), color = Palette.dim, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { card.conditions.forEach { Chip(it.label) } }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(title)
        content()
    }
}

@Composable
private fun Note(text: String) = Text(text, color = Palette.sub, fontSize = 19.sp)

@Composable
private fun Chip(text: String) =
    Text(text, Modifier.background(Palette.accentSoft, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp), color = Palette.accent, fontSize = 18.sp)

/** Region chips and, always under them, the boundary data's attribution (plan: shown in the app). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Regions(names: List<String>) {
    if (names.isEmpty()) Note("도착한 시·군·구가 여기에 모여요.")
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { Chip(it) }
    }
    Text(ATTRIBUTION, color = Palette.dim, fontSize = 18.sp, lineHeight = 25.sp)
}
