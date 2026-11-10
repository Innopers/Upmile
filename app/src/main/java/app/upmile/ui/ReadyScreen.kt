package app.upmile.ui

import android.car.Car
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
import app.upmile.core.check.CheckItem
import app.upmile.core.check.CheckState
import app.upmile.core.model.HardSegment
import app.upmile.core.record.ExperienceType
import app.upmile.core.record.Record
import app.upmile.core.route.Destination
import app.upmile.core.route.SegmentFinder
import app.upmile.drive.DriveService
import app.upmile.ui.theme.Palette

/** "회사로", "서울역으로": 으로 after a final consonant other than ㄹ. A name not ending in Hangul takes 로. */
internal fun withRo(name: String): String {
    val c = name.lastOrNull() ?: return name
    val coda = if (c in '가'..'힣') (c - '가') % 28 else 0
    return name + if (coda == 0 || coda == 8) "로" else "으로"
}

/** The start button: in 체험 모드 it replays the recorded drive instead of guiding to [destinationName]. */
internal fun startLabel(destinationName: String, demo: Boolean, running: Boolean) = when {
    demo -> if (running) "체험 주행 중" else "체험 주행 시작"
    else -> "${withRo(destinationName)} ${if (running) "안내 중" else "안내 시작"}"
}

private val NaviIcon = strokeIcon("M3 11l18-8-8 18-2-8z", width = 2.6f)
private val WarnIcon = strokeIcon("M12 3l9 16H3z", "M12 10v4M12 17v.5")

/**
 * Step 4, 출발: what was done, what still needs attention, and the button that starts the drive and sends the
 * destination to the car's navigation. [check] is what the check last showed per item; [onStarted] gets whether the
 * drive service started.
 */
@Composable
fun ReadyScreen(
    destination: Destination, rehearsed: Set<String>, routineVisited: Boolean, check: Map<CheckItem, CheckState>,
    startFailed: Boolean, onStarted: (Boolean) -> Unit, onBack: () -> Unit, demo: Boolean,
) {
    val context = LocalContext.current
    val segments = remember(destination) { SegmentFinder.find(Graph.routes.route(destination.id), destination.hasGarage) }
    val seen = segments.map { it.id }.filter { it in rehearsed }
    val record by Graph.store.records.collectAsStateWithLifecycle()
    val voiceReady by Graph.voice.ready.collectAsStateWithLifecycle()
    val serviceRunning by DriveService.running.collectAsStateWithLifecycle()
    val demoDriving by Graph.demoDriving.collectAsStateWithLifecycle()
    val running = if (demo) demoDriving else serviceRunning
    var recheck by remember { mutableIntStateOf(0) } // permissions have no flow: re-read on resume and after the dialog
    LifecycleResumeEffect(Unit) { recheck++; onPauseOrDispose {} }
    val status = remember(voiceReady, running, recheck, startFailed) { Graph.readyStatus(startFailed) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { recheck++ }
    // `running` turns true only once the service runs its onStartCommand; this covers the taps in between.
    var starting by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (running) starting = false }

    fun start() {
        if (starting || running) return // a second tap in the same frame, before the button disables
        if (demo) { Graph.startDemoDrive(seen.toSet()); return } // no service, no navigation; Graph ignores a second start
        val ok = DriveService.start(context, destination.id, seen)
        onStarted(ok)
        if (!ok) return
        starting = true
        Graph.sendToNavi(destination)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            Text("출발 준비 완료", color = Palette.mint, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("오늘도 천천히,\n잘 다녀와요.", fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 66.sp, letterSpacing = (-1).sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(if (routineVisited) "✓" else "–", "긴장 풀기", Modifier.weight(1f))
                Stat(if (check.isEmpty()) "–" else "${check.count { it.value != CheckState.WARN }}/${check.size}", "차량 점검", Modifier.weight(1f))
                Stat("${seen.size}/${segments.size}", "미리 본 구간", Modifier.weight(1f))
            }
            status.warnings.forEach { Warning(it) }
            if (!status.locationPermitted || !status.speedPermitted) {
                UpButton("권한 허용", { ask.launch(arrayOf(Graph.PERMISSION_FUSED_LOCATION, Car.PERMISSION_SPEED)) }, Modifier.width(320.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // While a drive runs (until it arrives), this is the only way to end it from the car's screen.
                if (running) UpButton("안내 끝내기", { if (demo) Graph.stopDemoDrive() else DriveService.stop(context) }, Modifier.weight(1.4f).height(96.dp))
                else UpButton("이전", onBack, Modifier.weight(1f).height(96.dp))
                UpButton(
                    startLabel(destination.name, demo, running), ::start, Modifier.weight(3.4f).height(96.dp),
                    primary = true, icon = NaviIcon, enabled = status.canDrive && !starting && !running,
                )
            }
            Text(
                if (demo) "기록해 둔 ${destination.name} 가는 길을 약 1분 30초 동안 재생해요. 주행 중에는 화면을 숨기고 음성으로만 안내해요."
                else "목적지는 차량 내비게이션으로 보내요. 기어를 D로 바꾸면 화면을 숨기고 음성으로만 안내해요.",
                color = Palette.sub, fontSize = 19.sp,
            )
        }
        Collection(record, segments, Modifier.width(560.dp).fillMaxHeight())
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier) {
    Column(modifier.card(24.dp)) {
        Text(value, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Text(caption, Modifier.padding(top = 2.dp), color = Palette.sub, fontSize = 18.sp)
    }
}

@Composable
private fun Warning(text: String) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().background(Palette.warn.copy(alpha = .10f), shape).border(1.dp, Palette.warn.copy(alpha = .55f), shape)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WarnIcon, null, Modifier.size(28.dp), tint = Palette.warn)
        Text(text, color = Palette.warn, fontSize = 20.sp)
    }
}

/** The mockup's 초보 탈출 도감: earned first, then today's challenges, then the rest. */
@Composable
private fun Collection(record: Record, segments: List<HardSegment>, modifier: Modifier) {
    val earned = conquered(record)
    val today = todayChallenges(segments, earned)
    val order = ExperienceType.entries.sortedBy { if (it in earned) 0 else if (it in today) 1 else 2 }
    Column(modifier.card(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("초보 탈출 도감")
            Spacer(Modifier.weight(1f))
            Text("${earned.size} / ${ExperienceType.entries.size} 정복", color = Palette.sub, fontSize = 18.sp)
        }
        order.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { Badge(it, it in earned, it in today, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("도착하면 연습했던 구간을 실제로 지나갔는지 확인해서 도감에 채워 드려요.", color = Palette.sub, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

/** Mockup `.badge`: a gold medal when earned, a dashed accent outline for today's challenge, dim and locked otherwise. */
@Composable
private fun Badge(type: ExperienceType, got: Boolean, challenge: Boolean, modifier: Modifier) {
    val name = type.look.first
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(172.dp).background(Palette.surface3, shape).drawBehind {
            if (challenge) drawRoundRect(
                Palette.accent, cornerRadius = CornerRadius(22.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        },
    ) {
        if (challenge) Text(
            "오늘 도전", Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
            color = Palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
        Column(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 30.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Medal(type, got, challenge)
            Text(name, color = if (got || challenge) Palette.text else Palette.sub, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
    }
}
