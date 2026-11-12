package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType

/** Picks the scene generator for a hard segment. */
object SceneBuilder {
    fun build(segment: HardSegment): SceneSpec = when (segment.type) {
        SegmentType.HIGHWAY_MERGE -> MergeScene.build(segment)
        SegmentType.MULTILANE_LEFT -> LeftTurnScene.build(segment)
        SegmentType.GARAGE_RAMP -> RampScene.build(segment)
        SegmentType.ROUNDABOUT -> RoundaboutScene.build(segment)
        SegmentType.SHARP_CURVE -> CurveScene.build(segment)
        SegmentType.ACCIDENT_SPOT -> PlainScene.build(segment)
    }
}
