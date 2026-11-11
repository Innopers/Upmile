package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType

/** Picks the scene generator for a hard segment. */
object SceneBuilder {
    fun build(segment: HardSegment): SceneSpec = when (segment.type) {
        SegmentType.HIGHWAY_MERGE -> MergeScene.build(segment)
        SegmentType.MULTILANE_LEFT, SegmentType.GARAGE_RAMP, SegmentType.ROUNDABOUT,
        SegmentType.SHARP_CURVE, SegmentType.ACCIDENT_SPOT -> PlainScene.build(segment)
    }
}
