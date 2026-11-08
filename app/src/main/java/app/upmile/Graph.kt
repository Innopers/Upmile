package app.upmile

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import app.upmile.core.cabin.CabinController
import app.upmile.core.drive.ReadyCheck
import app.upmile.core.drive.ReadyStatus
import app.upmile.core.location.LocationSource
import app.upmile.core.location.ScenarioLocationSource
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.route.AssetRouteRepository
import app.upmile.core.route.RouteRepository
import app.upmile.core.vehicle.ScenarioJson
import app.upmile.core.vehicle.ScenarioVehicleSignals
import app.upmile.core.vehicle.VehicleSignals
import app.upmile.core.voice.QueuedVoice
import app.upmile.core.voice.Voice
import app.upmile.drive.DriveService
import app.upmile.location.PleosLocationSource
import app.upmile.vehicle.DEFAULT_INVERT_SEAT
import app.upmile.vehicle.PleosVehicleSignals
import app.upmile.voice.LineTracker
import app.upmile.voice.PleosVoice
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide dependencies, built once in [UpmileApp]. 체험 모드 ([init] demoMode) swaps vehicle and location for the recorded commute. */
object Graph {
    /** Last resort only: 8 s past the longest line PleosVoice can hold before it reports done itself. */
    const val VOICE_TIMEOUT_MS = LineTracker.MAX_TOTAL_MS + 8_000

    private const val TAG = "Graph"
    private const val PERMISSION_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

    private lateinit var app: Context
    private var scope: CoroutineScope? = null
    private var engine: PleosVoice? = null
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _restricted = MutableStateFlow(false)

    private var demoMode = false
    lateinit var signals: VehicleSignals; private set
    lateinit var location: LocationSource; private set
    lateinit var voice: Voice; private set
    lateinit var cabin: CabinController; private set
    lateinit var routes: RouteRepository; private set
    lateinit var store: RecordStore; private set
    /** Lazy: the boundary file is large; first read when a drive starts (off the main thread), not at app start. */
    val regions: RegionLocator by lazy { RegionLocator(read("regions/sigungu.geojson")) }
    /** True while the car requires distraction optimization (driving); the driving cover (Task 13) follows it. */
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private fun read(path: String): String = app.assets.open(path).bufferedReader().use { it.readText() }

    /** Main thread. Calling it again tears the previous graph down first. */
    fun init(context: Context, demoMode: Boolean) {
        if (scope != null) close()
        app = context.applicationContext
        this.demoMode = demoMode
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        if (demoMode) {
            val replay = ScenarioVehicleSignals(ScenarioJson.decode(read("scenarios/commute_drive.json")), SystemClock::elapsedRealtime, s)
            signals = replay
            location = ScenarioLocationSource(replay, s)
        } else {
            signals = PleosVehicleSignals(app, DEFAULT_INVERT_SEAT)
            location = PleosLocationSource(app)
        }
        val e = PleosVoice(app).also { engine = it }
        voice = QueuedVoice(e, VOICE_TIMEOUT_MS, s)
        cabin = NoCabin
        routes = AssetRouteRepository(::read)
        // the demo never touches the real record (see runDemo)
        store = RecordStore(File(app.filesDir, if (demoMode) "record_demo.json" else "record.json"))
        watchRestrictions()
    }

    fun readyStatus(serviceStartAttempted: Boolean): ReadyStatus = ReadyCheck.evaluate(
        voiceReady = voice.ready.value,
        locationPermitted = granted(PERMISSION_FUSED_LOCATION),
        speedPermitted = granted(Car.PERMISSION_SPEED),
        serviceRunning = DriveService.running.value,
        demoMode = demoMode,
        serviceStartAttempted = serviceStartAttempted,
    )

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
        ux = null; car = null
        location.stop()
        signals.stop()
        engine?.release(); engine = null
        scope?.cancel(); scope = null
    }

    // shortcut: applies nothing (every call reports "not applied"), until Task 6 adds PleosCabinController.
    private object NoCabin : CabinController {
        override fun ambient(brightness: Int, argb: Int) = false
        override fun quietFan() = false
        override fun restore() {}
    }
}
