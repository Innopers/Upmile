package app.upmile.core.demo

import app.upmile.core.drive.DriveLoop
import app.upmile.core.drive.DriveTracker
import app.upmile.core.location.ScenarioLocationSource
import app.upmile.core.record.Record
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.route.SegmentFinder
import app.upmile.core.route.TmapRouteParser
import app.upmile.core.vehicle.ScenarioJson
import app.upmile.core.vehicle.ScenarioVehicleSignals
import app.upmile.core.voice.QueuedVoice
import app.upmile.core.voice.SpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Always ready, completes immediately: for headless runs with no TTS. */
object SilentEngine : SpeechEngine {
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    override fun speak(text: String, onDone: () -> Unit) = onDone()
    override fun stop() {}
}

/**
 * Replays the recorded commute drive through the real pipeline and returns the resulting record.
 * Callers must pass a [store] backed by its own file (`record_demo.json`) so the demo never touches the real record.
 * Headless: steps every 200 ms frame without sleeping, so [timeScale] only matters to UI pacing (unused here).
 */
@Suppress("UNUSED_PARAMETER")
fun runDemoHeadless(
    readAsset: (String) -> String,
    timeScale: Float,
    store: RecordStore,
    speech: SpeechEngine = SilentEngine,
): Record {
    val route = TmapRouteParser.parse("commute", "commute", readAsset("routes/commute.json"))
    val frames = ScenarioJson.decode(readAsset("scenarios/commute_drive.json"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
        var now = 0L
        val signals = ScenarioVehicleSignals(frames, clock = { now })
        val location = ScenarioLocationSource(signals, scope)
        val loop = DriveLoop(
            DriveTracker(route, SegmentFinder.find(route, destinationHasGarage = true)),
            QueuedVoice(speech, scope = scope), store, RegionLocator(readAsset("regions/sigungu.geojson")), "demo-commute",
        ) { now }
        signals.start()
        val end = frames.last().snapshot.timestampMs
        while (now <= end) {
            signals.tick()
            loop.tick(signals.snapshots.value, location.location.value)
            now += 200
        }
    } finally { scope.cancel() }
    return store.load()
}
