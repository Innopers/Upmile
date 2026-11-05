package app.upmile.core.drive

/**
 * [canDrive] does not wait for the service: pressing 출발 is what starts it. [serviceRunning] only matters once
 * [serviceStartAttempted] is true, and then a stopped service is a warning, not a block.
 */
data class ReadyStatus(
    val voiceReady: Boolean, val locationPermitted: Boolean, val speedPermitted: Boolean, val serviceRunning: Boolean,
    val serviceStartAttempted: Boolean = false,
) {
    val canDrive: Boolean get() = locationPermitted
    val warnings: List<String>
        get() = buildList {
            if (!locationPermitted) add("위치 권한이 없어 주행 중 음성 안내를 할 수 없어요.")
            if (serviceStartAttempted && !serviceRunning) add("주행 안내가 아직 켜지지 않았어요. 앱을 다시 열어 주세요.")
            if (!voiceReady) add("음성 안내가 아직 준비되지 않았어요. 준비되면 이어서 안내할게요.")
            if (!speedPermitted) add("속도 정보를 읽을 수 없어 급제동 감지가 제한돼요.")
        }
}

object ReadyCheck {
    /** [demoMode]: scenario sources need no permissions, so they count as granted. */
    fun evaluate(
        voiceReady: Boolean, locationPermitted: Boolean, speedPermitted: Boolean, serviceRunning: Boolean,
        demoMode: Boolean = false, serviceStartAttempted: Boolean = false,
    ) = ReadyStatus(voiceReady, locationPermitted || demoMode, speedPermitted || demoMode, serviceRunning, serviceStartAttempted)
}
