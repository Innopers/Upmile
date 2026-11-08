package app.upmile.ui

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class RevisitLabelTest {
    private val segments = listOf(
        HardSegment("commute:multilane_left:2", SegmentType.MULTILANE_LEFT, "다차선 좌회전", "판교역 사거리에서 좌회전", GeoPoint(0.0, 0.0), 0, emptyMap()),
        HardSegment("commute:garage_ramp:end", SegmentType.GARAGE_RAMP, "주차장 경사로", "회사", GeoPoint(0.0, 0.0), 0, emptyMap()),
    )

    @Test fun knownId_showsTitleAndPlace() = assertEquals(
        listOf("다차선 좌회전 · 판교역 사거리에서 좌회전", "주차장 경사로 · 회사"),
        revisitLabels(listOf("commute:multilane_left:2", "commute:garage_ramp:end"), segments),
    )

    @Test fun unknownId_fallsBackToTheId() =
        assertEquals(listOf("gone:roundabout:9"), revisitLabels(listOf("gone:roundabout:9"), segments))
}
