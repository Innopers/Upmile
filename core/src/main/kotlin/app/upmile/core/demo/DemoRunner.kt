package app.upmile.core.demo

import app.upmile.core.drive.DriveLoop
import app.upmile.core.drive.DriveTracker
import app.upmile.core.model.GeoPoint
import app.upmile.core.record.Record
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.route.AssetRouteRepository
import app.upmile.core.route.SegmentFinder
import app.upmile.core.vehicle.ScenarioFrame
import app.upmile.core.vehicle.ScenarioJson
import app.upmile.core.vehicle.ScenarioVehicleSignals
import app.upmile.core.voice.QueuedVoice
import app.upmile.core.voice.SpeechEngine
import app.upmile.core.voice.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** Always ready, completes immediately: for headless runs with no TTS. */
object SilentEngine : SpeechEngine {
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    override fun speak(text: String, onDone: () -> Unit) = onDone()
    override fun stop() {}
}

/** The destination [runDemo] replays; the app shows it on the 체험 모드 cover. */
const val DEMO_DESTINATION = "commute"

/**
 * The one 체험 모드 runner, shared by the app and headless tests. Replays the recorded commute drive through the real
 * pipeline in [stepMs] replay steps and returns the resulting record. Each step reads the scenario frame ONCE and
 * hands that frame's snapshot and position together to the loop, so they can never come from different frames.
 * [pace] runs after every step with [stepMs] (the app passes `{ delay((it / timeScale).toLong()) }`); [onFrame] lets
 * the app follow the replayed vehicle (e.g. show the gear-D cover).
 * Callers must pass a [store] backed by its own file (`record_demo.json`) so the demo never touches the real record.
 * [rehearsedIds] are the segments the user previewed; null counts all commute segments as rehearsed (headless runs).
 * [voice] takes the lines (the app passes its one queue, so they never fight a second one); null queues them on [speech].
 */
suspend fun runDemo(
    readAsset: (String) -> String,
    store: RecordStore,
    speech: SpeechEngine = SilentEngine,
    stepMs: Long = 200,
    pace: suspend (Long) -> Unit = {},
    onFrame: (ScenarioFrame) -> Unit = {},
    voice: Voice? = null,
    rehearsedIds: Set<String>? = null,
): Record {
    val routes = AssetRouteRepository(readAsset)
    val route = routes.route(DEMO_DESTINATION)
    val segments = SegmentFinder.find(route, routes.destinations().first { it.id == DEMO_DESTINATION }.hasGarage)
    val frames = ScenarioJson.decode(readAsset("scenarios/commute_drive.json"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
        var now = 0L
        val signals = ScenarioVehicleSignals(frames, clock = { now })
        val loop = DriveLoop(
            DriveTracker(route, segments), voice ?: QueuedVoice(speech, scope = scope), store,
            RegionLocator(readAsset("regions/sigungu.geojson")), "demo-commute", rehearsedIds ?: segments.map { it.id }.toSet(),
            System::currentTimeMillis,
        )
        signals.start()
        val end = frames.last().snapshot.timestampMs
        while (now <= end) {
            signals.tick()
            val f = signals.frame.value
            onFrame(f)
            loop.tick(f.snapshot, GeoPoint(f.lat, f.lon))
            pace(stepMs)
            now += stepMs
        }
    } finally { scope.cancel() }
    return store.records.value
}

/** [runDemo] without pacing, for tests and tools. */
fun runDemoHeadless(
    readAsset: (String) -> String,
    store: RecordStore,
    speech: SpeechEngine = SilentEngine,
): Record = runBlocking { runDemo(readAsset, store, speech) }
