package app.upmile.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
import app.upmile.core.demo.DEMO_DESTINATION
import app.upmile.core.routine.Mood
import app.upmile.core.routine.planFor
import app.upmile.core.vehicle.Gear
import app.upmile.ui.theme.Palette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [step] is the header tab (1–4); RECORD has none. */
enum class Screen(val step: Int?, val title: String) {
    ROUTINE(1, "긴장 풀기"), CHECK(2, "차량 점검"), PREVIEW(3, "길 미리보기"), READY(4, "출발"), RECORD(null, "기록"),
}

/** Every screen hides while driving. An unknown gear counts as driving (fail safe). */
internal fun coverShown(restricted: Boolean, gear: Gear) = restricted || gear != Gear.P

/** A preview counts for the next departure only: the cover coming on (the drive has its ids by then) clears it. */
internal fun rehearsalAfterCover(rehearsed: Set<String>, coverOn: Boolean) = if (coverOn) emptySet() else rehearsed

/** A finished drive the user has not looked at yet opens the record once the car is in P. */
internal fun opensRecord(gear: Gear, unseenSessionId: String?) = gear == Gear.P && unseenSessionId != null

internal enum class TabState { ACTIVE, DONE, UPCOMING }

/** As in the mockup: moving ahead marks every step before the furthest one [reached] done; going back keeps them. */
internal fun tabState(step: Int, current: Int?, reached: Int) = when {
    step == current -> TabState.ACTIVE
    step < reached -> TabState.DONE
    else -> TabState.UPCOMING
}

private const val TAG = "AppNav"

@Composable
fun AppNav(gleoRoute: String?, onGleoRouteHandled: () -> Unit) {
    val demo by Graph.demo.collectAsStateWithLifecycle()
    // Switching 체험 모드 starts over on ROUTINE with fresh state, reading Graph's swapped vehicle and record.
    key(demo) { Nav(demo, gleoRoute, onGleoRouteHandled) }
}

@Composable
private fun Nav(demo: Boolean, gleoRoute: String?, onGleoRouteHandled: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.ROUTINE) }
    var reached by rememberSaveable { mutableIntStateOf(1) }
    var beforeRecord by rememberSaveable { mutableStateOf(Screen.ROUTINE) }
    var mood by rememberSaveable { mutableStateOf(Mood.A_LITTLE) }
    // What the check has said this departure; kept across screens, cleared when the cover comes on.
    val checkMemory = remember { mutableStateOf(CheckMemory()) }
    // Today's destination; Preview changes it.
    var destinationId by rememberSaveable { mutableStateOf(Graph.routes.destinations().first().id) }
    // 체험 모드 always replays the commute, so every screen shows that destination.
    val destination = Graph.routes.destinations().first { it.id == if (demo) DEMO_DESTINATION else destinationId }
    // Segment ids whose preview ran to the end this departure; Ready counts them and the drive stamps them.
    var rehearsed by remember { mutableStateOf(emptySet<String>()) }
    var routineVisited by rememberSaveable { mutableStateOf(false) }
    var startFailed by remember { mutableStateOf(false) } // the last 안내 시작 could not start the drive service

    val gear by remember { Graph.signals.snapshots.map { it.gear }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(Graph.signals.snapshots.value.gear)
    val restricted by Graph.restricted.collectAsStateWithLifecycle()
    val unseen by remember { Graph.store.records.map { it.unseenSessionId }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(Graph.store.records.value.unseenSessionId)
    val cover = coverShown(restricted, gear)

    fun go(to: Screen) {
        if (to == Screen.RECORD && screen != Screen.RECORD) beforeRecord = screen
        to.step?.let { if (it > reached) reached = it }
        screen = to
    }

    LaunchedEffect(cover) {
        Log.i(TAG, "driving cover ${if (cover) "on" else "off"} (gear=$gear restricted=$restricted, screen=$screen)")
        checkMemory.value = checkMemory.value.afterCover(cover)
        rehearsed = rehearsalAfterCover(rehearsed, cover)
    }
    LaunchedEffect(gear == Gear.P, unseen) { if (opensRecord(gear, unseen)) go(Screen.RECORD) }
    // A Gleo request (already checked for P by the activity) opens its screen once.
    LaunchedEffect(gleoRoute) {
        val r = gleoRoute ?: return@LaunchedEffect
        when {
            r == "routine" -> go(Screen.ROUTINE)
            r == "record" -> go(Screen.RECORD)
            r == "preview" -> go(Screen.PREVIEW)
            r.startsWith("preview/") -> {
                val id = r.removePrefix("preview/")
                if (!demo && Graph.routes.destinations().any { it.id == id }) destinationId = id
                go(Screen.PREVIEW)
            }
        }
        onGleoRouteHandled()
    }
    LaunchedEffect(screen) { if (screen == Screen.ROUTINE) routineVisited = true }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        // no 끝내기 to tap while the real car requires distraction optimization
        if (demo) DemoBanner(onExit = if (restricted) null else { { Graph.setDemoMode(false) } })
        // Replaces the screens rather than covering them, so music, cabin light and check speech stop while driving.
        if (cover) { if (demo) DemoCover() else DrivingCover(destination.name) }
        else Column(Modifier.fillMaxSize()) {
            Header(
                screen.step, reached, onStep = { s -> go(Screen.entries.first { it.step == s }) }, onRecord = { go(Screen.RECORD) },
                onDemo = if (demo) null else { { Graph.setDemoMode(true) } },
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 32.dp)) {
                when (screen) {
                    Screen.ROUTINE -> RoutineScreen(mood, { mood = it }, destination, onNext = { go(Screen.CHECK) })
                    Screen.CHECK -> CheckScreen(planFor(mood), destination, checkMemory, onBack = { go(Screen.ROUTINE) }, onNext = { go(Screen.PREVIEW) })
                    Screen.PREVIEW -> PreviewScreen(
                        destination, if (demo) null else { { destinationId = it.id } }, rehearsed, { rehearsed = rehearsed + it },
                        onBack = { go(Screen.CHECK) }, onNext = { go(Screen.READY) },
                    )
                    Screen.READY -> ReadyScreen(
                        destination, rehearsed, routineVisited, checkMemory.value.states, startFailed,
                        { ok ->
                            startFailed = !ok
                            // The drive has its ids; the app may stay stopped behind the navigation, so the cover never sees it.
                            if (ok) { rehearsed = emptySet(); checkMemory.value = CheckMemory() }
                        },
                        onBack = { go(Screen.PREVIEW) }, demo = demo,
                    )
                    Screen.RECORD -> RecordScreen(onBack = { go(beforeRecord) }, onHome = { go(Screen.ROUTINE) })
                }
            }
        }
    }
}

private val LogoIcon = strokeIcon("M7 21c0-6 10-6 10-12V3", "M12 21v-2", width = 2.6f)
private val StarIcon = strokeIcon("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z", width = 2f)
private val PlayIcon = strokeIcon("M8 5l11 7-11 7z", width = 2.4f)

/** [onDemo] (체험 모드로 둘러보기) is null while already in 체험 모드: the banner then offers the way out. */
@Composable
private fun Header(currentStep: Int?, reached: Int, onStep: (Int) -> Unit, onRecord: () -> Unit, onDemo: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(112.dp).padding(start = 36.dp, end = 36.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2E9BFF), Palette.mint))),
            contentAlignment = Alignment.Center,
        ) { Icon(LogoIcon, null, Modifier.size(30.dp), tint = Palette.onAccent) }
        Column(Modifier.padding(start = 16.dp)) {
            Text("Upmile", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp)
            Text("초보 운전 출발 루틴", color = Palette.sub, fontSize = 18.sp)
        }
        Row(
            Modifier.padding(start = 36.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface2).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Screen.entries.forEach { s -> s.step?.let { StepTab(it, s.title, tabState(it, currentStep, reached)) { onStep(it) } } }
        }
        Spacer(Modifier.weight(1f))
        if (onDemo != null) HeaderButton(PlayIcon, Palette.mint, "체험 모드", onDemo, Modifier.padding(end = 16.dp))
        HeaderButton(StarIcon, Palette.warn, "기록", onRecord)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, tint: Color, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.surface2)
            .border(1.dp, Palette.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = tint)
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepTab(n: Int, title: String, state: TabState, onClick: () -> Unit) {
    val active = state == TabState.ACTIVE
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) Palette.surface3 else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (ring, fill, digit) = when (state) {
            TabState.ACTIVE -> Triple(Palette.accent, Color.Transparent, Palette.accent)
            TabState.DONE -> Triple(Palette.ok, Palette.ok, Color(0xFF06210F))
            TabState.UPCOMING -> Triple(Color(0xFF3A4452), Color.Transparent, Palette.sub)
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = digit, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = if (active) Palette.text else Palette.sub, fontSize = 21.sp)
    }
}
