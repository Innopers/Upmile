package app.upmile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upmile.core.model.HardSegment
import app.upmile.core.record.Condition
import app.upmile.core.record.ExperienceRules
import app.upmile.core.record.ExperienceType
import app.upmile.core.record.Record
import app.upmile.ui.theme.Palette

/** Korean name and icon of each experience: the one list Ready's 도감 and the record share. */
internal val ExperienceType.look: Pair<String, String>
    get() = when (this) {
        ExperienceType.FIRST_DRIVE -> "첫 주행" to "🚗"
        ExperienceType.HIGHWAY_MERGE -> "첫 고속도로 합류" to "🛣️"
        ExperienceType.MULTILANE_LEFT -> "첫 다차로 좌회전" to "⬅️"
        ExperienceType.ROUNDABOUT -> "첫 회전교차로" to "🔄"
        ExperienceType.GARAGE_RAMP -> "첫 지하주차장" to "🅿️"
        ExperienceType.RAIN -> "첫 빗길" to "🌧️"
        ExperienceType.NIGHT -> "첫 야간 운전" to "🌙"
        ExperienceType.FREEZING -> "첫 영하 운전" to "❄️"
        ExperienceType.KM_100_TRIP -> "첫 100km" to "💯"
        ExperienceType.NEW_CITY -> "처음 가는 도시" to "🏙️"
        ExperienceType.NEW_PROVINCE -> "처음 가는 도" to "🗺️"
    }

internal val Condition.label: String
    get() = when (this) {
        Condition.RAIN -> "비"
        Condition.NIGHT -> "밤"
        Condition.FREEZING -> "영하"
    }

/** Experiences earned so far, each type once. */
internal fun conquered(record: Record): Set<ExperienceType> = record.cards.mapTo(mutableSetOf()) { it.type }

/** Today's segment types not earned yet: the 도감's "오늘 도전" tiles. */
internal fun todayChallenges(segments: List<HardSegment>, earned: Set<ExperienceType>): Set<ExperienceType> =
    segments.mapNotNullTo(mutableSetOf()) { ExperienceRules.segmentCard[it.type] } - earned

/** Mockup `.medal`: gold when [got], accent-soft for today's [challenge], dark with a lock otherwise. */
@Composable
internal fun Medal(type: ExperienceType, got: Boolean, challenge: Boolean = false, size: Dp = 66.dp) {
    Box(
        Modifier.size(size).drawBehind {
            when {
                got -> drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFFFF3C4), .6f to Palette.warn, 1f to Color(0xFFB86B12),
                        center = Offset(this.size.width * .35f, this.size.height * .3f), radius = this.size.width * .8f,
                    )
                )
                challenge -> drawCircle(Palette.accentSoft)
                else -> drawCircle(Color(0xFF232A33))
            }
        },
        contentAlignment = Alignment.Center,
    ) { Text(if (got || challenge) type.look.second else "🔒", fontSize = (size.value * .42f).sp) }
}
