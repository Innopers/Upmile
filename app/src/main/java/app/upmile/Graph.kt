package app.upmile

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.upmile.core.cabin.CabinController
import app.upmile.core.demo.runDemo
import app.upmile.core.drive.ReadyCheck
import app.upmile.core.drive.ReadyStatus
import app.upmile.core.location.LocationSource
import app.upmile.core.model.GeoPoint
import app.upmile.core.record.Record
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.route.AssetRouteRepository
import app.upmile.core.route.Destination
import app.upmile.core.route.RouteRepository
import app.upmile.core.vehicle.ScenarioFrame
import app.upmile.core.vehicle.ScenarioJson
import app.upmile.core.vehicle.VehicleSignals
import app.upmile.core.vehicle.VehicleSnapshot
import app.upmile.core.voice.QueuedVoice
import app.upmile.core.voice.Voice
import app.upmile.drive.DriveService
import app.upmile.location.PleosLocationSource
import app.upmile.vehicle.DEFAULT_INVERT_SEAT
import app.upmile.vehicle.PleosVehicleSignals
import app.upmile.voice.LineTracker
import app.upmile.voice.MusicPlayer
import app.upmile.voice.PleosVoice
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide dependencies, built once in [UpmileApp]. 체험 모드 ([setDemoMode]) swaps vehicle, location and record for the recorded commute. */
object Graph {
    /** Last resort only: 8 s past the longest line PleosVoice can hold before it reports done itself. */
    const val VOICE_TIMEOUT_MS = LineTracker.MAX_TOTAL_MS + 8_000

    private const val TAG = "Graph"
    const val PERMISSION_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

    private lateinit var app: Context
    private var scope: CoroutineScope? = null
    private var engine: PleosVoice? = null
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private var navi: NaviHelper? = null
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _restricted = MutableStateFlow(false)

    private val _demo = MutableStateFlow(false)
    /** 체험 모드: [signals] and [location] follow the replay [startDemoDrive] runs, and [store] is record_demo.json. */
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    private var demoCar: DemoCar? = null
    private var demoDrive: Job? = null
    private var switching: Job? = null // a running setDemoMode
    private val _demoDriving = MutableStateFlow(false)
    val demoDriving: StateFlow<Boolean> = _demoDriving.asStateFlow()
    lateinit var signals: VehicleSignals; private set
    lateinit var location: LocationSource; private set
    lateinit var voice: Voice; private set
    /** The routine's 출발곡; one player for the app, so leaving the screen can still fade it out. */
    lateinit var music: MusicPlayer; private set
    lateinit var cabin: CabinController; private set
    lateinit var routes: RouteRepository; private set
    lateinit var store: RecordStore; private set
    /** Lazy: the boundary file is large; first read when a drive starts (off the main thread), not at app start. */
    val regions: RegionLocator by lazy { RegionLocator(read("regions/sigungu.geojson")) }
    /** True while the car requires distraction optimization (driving); the driving cover (Task 13) follows it. */
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private fun read(path: String): String = app.assets.open(path).bufferedReader().use { it.readText() }
    private fun demoFile() = File(app.filesDir, "record_demo.json")

    /** Main thread. Calling it again tears the previous graph down first. */
    fun init(context: Context, demoMode: Boolean) {
        if (scope != null) close()
        app = context.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        use(load(demoMode))
        val e = PleosVoice(app).also { engine = it }
        voice = QueuedVoice(e, VOICE_TIMEOUT_MS, s)
        music = MusicPlayer(app)
        cabin = NoCabin
        routes = AssetRouteRepository(::read)
        watchRestrictions()
    }

    /** What a mode swaps in, read from files (any thread); [use] applies it. */
    private class Mode(val demo: Boolean, val car: DemoCar?, val store: RecordStore)

    /** Either way the demo record goes: entering starts it empty, leaving (or a fresh start) drops it. Never touches record.json. */
    private fun load(demo: Boolean): Mode {
        demoFile().delete()
        val car = if (demo) DemoCar(ScenarioJson.decode(read("scenarios/commute_drive.json")).first()) else null
        return Mode(demo, car, RecordStore(if (demo) demoFile() else File(app.filesDir, "record.json")))
    }

    /** Main thread (the Pleos adapters are built here). [demo] flips last, so whoever follows it sees the new members. */
    private fun use(m: Mode) {
        demoCar = m.car
        signals = m.car ?: PleosVehicleSignals(app, DEFAULT_INVERT_SEAT)
        location = m.car ?: PleosLocationSource(app)
        store = m.store
        _demo.value = m.demo
    }

    /**
     * Main thread. Switches 체험 모드 on or off, swapping only what the mode changes ([signals], [location], [store]):
     * the voice keeps its loaded TTS model. A running real drive stops first (it holds the sources this stops), and a
     * running replay is cancelled and joined before the demo record is deleted, so its last tick can't write it back.
     * Taps while a switch runs are ignored.
     */
    fun setDemoMode(on: Boolean) {
        val s = scope ?: return
        if (on == _demo.value || switching?.isActive == true) return
        switching = s.launch(Dispatchers.Main.immediate) {
            if (on) DriveService.stop(app)
            demoDrive?.cancelAndJoin()
            val m = withContext(Dispatchers.IO) { load(on) }
            location.stop()
            signals.stop()
            use(m)
            signals.start() // the activity starts them only once, in onCreate
            Log.i(TAG, "demo mode ${if (on) "on" else "off"}")
        }
    }

    /**
     * Main thread. Replays the recorded commute through the real pipeline at real pace, the lines through [voice], the
     * screens following its frames. Ignored outside 체험 모드 or while a replay still runs (or is still winding down).
     */
    fun startDemoDrive() {
        val car = demoCar ?: return
        val s = scope ?: return
        if (demoDrive?.isCompleted == false || switching?.isActive == true) return
        val st = store
        _demoDriving.value = true
        demoDrive = s.launch {
            var finished = false
            try {
                st.save(Record.EMPTY) // fresh each run: the fixed demo session id would otherwise be skipped as processed
                Log.i(TAG, "demo drive started")
                runDemo(::read, st, pace = { delay(it) }, onFrame = car::follow, voice = voice)
                finished = true
                Log.i(TAG, "demo drive finished")
            } catch (e: CancellationException) {
                Log.i(TAG, "demo drive cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "demo drive failed", e)
            } finally {
                if (!finished) { // ended early: drop the queued approach lines (as DriveService does), back to P so the cover lifts
                    voice.stop()
                    car.park()
                }
                _demoDriving.value = false
            }
        }
    }

    /** Ends the replay early (안내 끝내기). */
    fun stopDemoDrive() { demoDrive?.cancel() }

    fun readyStatus(serviceStartAttempted: Boolean): ReadyStatus = ReadyCheck.evaluate(
        voiceReady = voice.ready.value,
        locationPermitted = granted(PERMISSION_FUSED_LOCATION),
        speedPermitted = granted(Car.PERMISSION_SPEED),
        serviceRunning = DriveService.running.value,
        demoMode = _demo.value,
        serviceStartAttempted = serviceStartAttempted,
    )

    /** Hands today's destination to the car's navigation. A failure is only logged: the drive still guides by voice. Main thread. */
    fun sendToNavi(d: Destination) = guarded(TAG, logged, "naviRoute") {
        // Kept for the app's life: calls made before the service binds wait in the SDK's queue.
        val n = navi ?: NaviHelper(app).also {
            it.initialize({ Log.i(TAG, "navi connected") }, { e -> Log.w(TAG, "navi connect failed", e) })
            navi = it
        }
        // 3.0.5 reordered RouteInfo's fields; named arguments keep latitude and longitude apart.
        // Our destinations have no TMAP POI, so the navigation routes to the coordinates.
        n.requestRoute(RouteInfo(latitude = d.lat, longitude = d.lon, poiName = d.name, poiId = "", poiSubId = "", address = ""))
        Log.i(TAG, "navi route requested: ${d.name} (${d.lat}, ${d.lon})")
    }

    private fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Register once, unregister in [close]: the official CarUxRestrictionsManager pattern. */
    private fun watchRestrictions() = guarded(TAG, logged, "uxRestrictions") {
        val c = Car.createCar(app) ?: return@guarded
        car = c
        val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        m.registerListener { setRestricted(it.isRequiresDistractionOptimization) }
        ux = m
        setRestricted(m.currentCarUxRestrictions.isRequiresDistractionOptimization)
    }

    private fun setRestricted(r: Boolean) {
        if (_restricted.value != r) Log.i(TAG, "driving restriction ${if (r) "on" else "off"}")
        _restricted.value = r
    }

    fun close() {
        guarded(TAG, logged, "uxUnregister") { ux?.unregisterListener() }
        guarded(TAG, logged, "carDisconnect") { car?.disconnect() }
        guarded(TAG, logged, "naviRelease") { navi?.release() }
        ux = null; car = null; navi = null
        location.stop()
        signals.stop()
        engine?.release(); engine = null
        music.release()
        scope?.cancel(); scope = null
    }

    /** 체험 모드's car: parked at the scenario's first frame until [follow] gets the replay's frames, [park] puts it back. No start or stop. */
    private class DemoCar(private val first: ScenarioFrame) : VehicleSignals, LocationSource {
        private val snapshot = MutableStateFlow(first.snapshot)
        private val position = MutableStateFlow<GeoPoint?>(GeoPoint(first.lat, first.lon))
        override val snapshots: StateFlow<VehicleSnapshot> = snapshot.asStateFlow()
        override val location: StateFlow<GeoPoint?> = position.asStateFlow()

        fun follow(f: ScenarioFrame) {
            snapshot.value = f.snapshot
            position.value = GeoPoint(f.lat, f.lon)
        }

        fun park() = follow(first)

        override fun start() {}
        override fun stop() {}
    }

    // shortcut: applies nothing (every call reports "not applied"), until Task 6 adds PleosCabinController.
    private object NoCabin : CabinController {
        override fun ambient(brightness: Int, argb: Int) = false
        override fun quietFan() = false
        override fun restore() {}
    }
}
