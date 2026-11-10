package app.upmile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.route.alongRouteM
import app.upmile.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * The cover's next-segment line for position [p]: "1.2km 앞 · 고속도로 합류" for the nearest segment still ahead, rounded to
 * 100 m like the approach voice ("300m 앞", "바로 앞" under 50 m). Null without a position or once the last one is reached.
 */
internal fun nextSegmentLine(route: Route, segments: List<HardSegment>, p: GeoPoint?): String? {
    val along = alongRouteM(route, p ?: return null)
    val next = segments.filter { it.distanceFromStartM > along }.minByOrNull { it.distanceFromStartM } ?: return null
    val m = next.distanceFromStartM - along
    val r = (m / 100).roundToInt() * 100
    val ahead = when {
        m < 50 -> "바로"
        r >= 1000 -> if (r % 1000 == 0) "${r / 1000}km" else "${r / 1000}.${r % 1000 / 100}km"
        else -> "${r}m"
    }
    return "$ahead 앞 · ${next.title}"
}

private val NaviIcon = strokeIcon("M3 11l18-8-8 18-2-8z", width = 2.6f)
private val CoverWave = listOf(22, 44, 30, 56, 26, 40, 20).map { (it * 1.4f).dp }

/** Shown instead of every screen while driving: voice only, nothing to tap. [nextSegment] is the next rehearsed segment, if known. */
@Composable
fun DrivingCover(destinationName: String, nextSegment: String? = null, waitingForSignal: Boolean = false) {
    Column(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(0f to Color(0xFF0F1A24), .7f to Color(0xFF0A0E13), center = Offset(size.width / 2, size.height * .4f), radius = size.maxDimension * .6f))
        },
        verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VoiceWave(CoverWave, barWidth = 8.dp)
        if (waitingForSignal) {
            Text("차량 신호를 기다리고 있어요", fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("기어 상태를 확인하면 화면이 열려요.", color = Palette.sub, fontSize = 23.sp)
        } else {
            Text("주행 중에는 음성으로만 안내할게요", fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("화면은 잠시 쉬어요. 앞을 봐 주세요.", color = Palette.sub, fontSize = 23.sp)
        }
        Row(
            Modifier.background(Palette.surface2, RoundedCornerShape(26.dp))
                .border(1.dp, Palette.accent.copy(alpha = .35f), RoundedCornerShape(26.dp))
                .padding(horizontal = 34.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(68.dp).background(Palette.accent, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(NaviIcon, null, Modifier.size(36.dp), tint = Palette.onAccent)
            }
            Column {
                Text("오늘 목적지 · $destinationName", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                if (nextSegment != null) Text(nextSegment, color = Palette.sub, fontSize = 20.sp)
            }
        }
    }
}
