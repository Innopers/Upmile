package app.upmile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upmile.Graph
import app.upmile.core.demo.DEMO_DESTINATION
import app.upmile.core.route.SegmentFinder
import app.upmile.ui.theme.Palette

private val ExitShape = RoundedCornerShape(18.dp)

/** 체험 모드's strip above every screen, the cover included: the drive is a replay, and [onExit] (끝내기) leaves the mode; null hides it. */
@Composable
fun DemoBanner(onExit: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(84.dp).background(Palette.mint.copy(alpha = .10f))
            .drawBehind { drawLine(Palette.mint.copy(alpha = .35f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(Palette.mint, CircleShape))
        Text("체험 모드 · 기록된 주행을 재생하는 중", color = Palette.mint, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (onExit != null) Box(
            Modifier.height(64.dp).clip(ExitShape).border(1.5.dp, Palette.mint.copy(alpha = .6f), ExitShape)
                .clickable(role = Role.Button, onClick = onExit).padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center,
        ) { Text("끝내기", color = Palette.mint, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

/** The cover during the replay: its destination and, from the replayed position, the next segment ahead. */
@Composable
fun DemoCover() {
    val destination = remember { Graph.routes.destinations().first { it.id == DEMO_DESTINATION } }
    val route = remember { Graph.routes.route(destination.id) }
    val segments = remember { SegmentFinder.find(route, destination.hasGarage) }
    val position by Graph.location.location.collectAsStateWithLifecycle()
    DrivingCover(destination.name, nextSegmentLine(route, segments, position))
}
