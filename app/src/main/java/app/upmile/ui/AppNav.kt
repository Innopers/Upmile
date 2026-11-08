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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
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
fun AppNav() {
    var screen by rememberSaveable { mutableStateOf(Screen.ROUTINE) }
    var reached by rememberSaveable { mutableIntStateOf(1) }
    var beforeRecord by rememberSaveable { mutableStateOf(Screen.ROUTINE) }
    var mood by rememberSaveable { mutableStateOf(Mood.A_LITTLE) }
    // Today's destination; 13b's Preview sets it.
    var destinationId by rememberSaveable { mutableStateOf(Graph.routes.destinations().first().id) }
    val destination = Graph.routes.destinations().first { it.id == destinationId }

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

    LaunchedEffect(cover) { Log.i(TAG, "driving cover ${if (cover) "on" else "off"} (gear=$gear restricted=$restricted, screen=$screen)") }
    LaunchedEffect(gear == Gear.P, unseen) { if (opensRecord(gear, unseen)) go(Screen.RECORD) }

    Box(Modifier.fillMaxSize().background(Palette.surface)) {
        // Replaces the screens rather than covering them, so music, cabin light and check speech stop while driving.
        if (cover) DrivingCover(destination.name)
        else Column(Modifier.fillMaxSize()) {
            Header(screen.step, reached, onStep = { s -> go(Screen.entries.first { it.step == s }) }, onRecord = { go(Screen.RECORD) })
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 32.dp)) {
                when (screen) {
                    Screen.ROUTINE -> RoutineScreen(mood, { mood = it }, destination, onNext = { go(Screen.CHECK) })
                    Screen.CHECK -> CheckScreen(planFor(mood), destination, onBack = { go(Screen.ROUTINE) }, onNext = { go(Screen.PREVIEW) })
                    Screen.PREVIEW -> StubScreen(Screen.PREVIEW.title) { go(Screen.CHECK) }
                    Screen.READY -> StubScreen(Screen.READY.title) { go(Screen.PREVIEW) }
                    Screen.RECORD -> StubScreen(Screen.RECORD.title) { go(beforeRecord) }
                }
            }
        }
    }
}

private val LogoIcon = strokeIcon("M7 21c0-6 10-6 10-12V3", "M12 21v-2", width = 2.6f)
private val StarIcon = strokeIcon("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z", width = 2f)

@Composable
private fun Header(currentStep: Int?, reached: Int, onStep: (Int) -> Unit, onRecord: () -> Unit) {
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
        Row(
            Modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.surface2)
                .border(1.dp, Palette.line, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onRecord).padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(StarIcon, null, Modifier.size(26.dp), tint = Palette.warn)
            Text("기록", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
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

// shortcut: placeholder for PREVIEW / READY / RECORD until Task 13b replaces it with the real screens.
@Composable
private fun StubScreen(title: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        UpButton("이전", onBack, Modifier.width(240.dp))
    }
}
