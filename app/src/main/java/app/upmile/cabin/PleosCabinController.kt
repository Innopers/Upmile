package app.upmile.cabin

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.upmile.core.cabin.CabinController
import app.upmile.guarded
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Ambient (mood) light and HVAC fan through the Pleos Vehicle SDK. Owns its Vehicle. No SDK call runs on the caller's
 * thread: one cabin thread does them all, and [ambient] only leaves the newest request for it (latest wins).
 */
class PleosCabinController(context: Context) : CabinController {
    private val app = context.applicationContext
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val io = Executors.newSingleThreadExecutor { Thread(it, "cabin").apply { isDaemon = true } }
    private val slot = LatestSlot<Pair<Int, Int>>()
    private var vehicle: Vehicle? = null // cabin thread only

    @Volatile private var ambientArea: SeatArea? = null // set once the capability check passed
    @Volatile private var fanAreas: Map<SeatArea, AreaBounds<Int>>? = null
    @Volatile private var lastWriteOk = true
    @Volatile private var released = false

    // Cabin thread, except the failure callbacks that forget what was written so the next frame retries.
    private val ambientOriginal = Remembered<String, Any>()
    private val fanOriginal = Remembered<SeatArea, Int>()
    private val ambientState = AmbientState()

    init { background { initVehicle() } } // no SDK call on the caller's (UI) thread

    private fun initVehicle() {
        var v: Vehicle? = null
        guarded(TAG, logged, "vehicle", { v?.let { h -> guarded(TAG, logged, "vehicleRelease") { h.release() } }; v = null }) {
            v = Vehicle(app).also { it.initialize() }
        }
        vehicle = v
        if (v != null) checkCapabilities()
    }

    private fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun checkCapabilities() {
        val v = vehicle ?: return
        if (granted(LIGHTS)) guarded(TAG, logged, "ambientCapability") {
            v.getLight().checkAmbientLightBrightnessCapabilities(
                { cfg, areas -> if (cfg.accessType == AccessType.READ_WRITE) ambientArea = areas.keys.firstOrNull() },
                { e -> warn("ambientCapability", e) },
            )
        } else warn("ambientCapability", SecurityException(LIGHTS))
        if (granted(CLIMATE)) guarded(TAG, logged, "fanCapability") {
            v.getHVAC().checkHvacFanSpeedCapabilities(
                { cfg, areas ->
                    val front = areas.filterKeys { a -> a.values.isNotEmpty() && a.values.all { it == SeatValue.ROW_1_LEFT || it == SeatValue.ROW_1_RIGHT } }
                    if (cfg.accessType == AccessType.READ_WRITE && front.isNotEmpty()) fanAreas = front
                },
                { e -> warn("fanCapability", e) },
            )
        } else warn("fanCapability", SecurityException(CLIMATE))
    }

    override fun ambient(brightness: Int, argb: Int): Boolean {
        if (released || ambientArea == null) return false
        if (slot.offer(brightness to argb)) background { drain() }
        return lastWriteOk
    }

    override fun quietFan(): Boolean {
        val areas = fanAreas
        if (released || areas == null) return false
        background { quietNow(areas) }
        return true
    }

    override fun restore() {
        slot.clear() // anything not yet applied must not undo the restore
        background { restoreNow() }
    }

    /** Stops the cabin thread (after putting things back) and releases the Vehicle. */
    fun release() {
        if (released) return
        released = true
        slot.clear()
        background { restoreNow(); vehicle?.let { v -> guarded(TAG, logged, "vehicleRelease") { v.release() } }; vehicle = null }
        io.shutdown()
    }

    private fun background(task: () -> Unit) {
        try { io.execute { guarded(TAG, logged, "task") { task() } } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    private fun warn(name: String, e: Throwable) { if (logged.add(name)) Log.w(TAG, "$name failed", e) }

    /** Runs a getter and waits for its answer (cabin thread only); null if it failed or took over a second. */
    private fun <T : Any> read(name: String, call: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): T? {
        val box = AtomicReference<T?>()
        val done = CountDownLatch(1)
        guarded(TAG, logged, name) {
            call({ box.set(it); done.countDown() }, { e -> warn(name, e); done.countDown() })
            done.await(1, TimeUnit.SECONDS)
        }
        return box.get()
    }

    /** Fire a setter; its result lands in [lastWriteOk], and a failure runs [forget]. */
    private fun write(name: String, forget: () -> Unit = {}, done: CountDownLatch? = null, call: (ok: () -> Unit, fail: (Exception) -> Unit) -> Unit) {
        val failed = { e: Throwable -> warn(name, e); lastWriteOk = false; forget(); if (done != null) done.countDown() }
        guarded(TAG, logged, name, onFail = failed) { call({ lastWriteOk = true; if (done != null) done.countDown() }, { e -> failed(e) }) }
    }

    private fun drain() {
        val v = vehicle ?: return
        val area = ambientArea ?: return
        while (true) {
            val (brightness, argb) = slot.take() ?: return
            val light = v.getLight()
            val applied = ambientState.apply(brightness, argb, { readOriginals(light, area) }) { w ->
                when (w) {
                    AmbientWrite.SwitchOn -> write("ambientSwitch", { ambientState.failed(w) }) { ok, fail -> light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> ok() }, fail) }
                    is AmbientWrite.Brightness -> write("ambientBrightness", { ambientState.failed(w) }) { ok, fail -> light.setAmbientLightBrightness(area, w.value, { _, _ -> ok() }, fail) }
                    is AmbientWrite.Color -> write("ambientColor", { ambientState.failed(w) }) { ok, fail -> light.setAmbientLightBrightnessColor(area, w.value, { _, _ -> ok() }, fail) }
                }
            }
            if (!applied) { warn("ambientOriginal", IllegalStateException("original switch unreadable, light left alone")); lastWriteOk = false }
        }
    }

    /** Reads and remembers what restore will put back; a property that can't be read is never written. */
    private fun readOriginals(light: ai.pleos.playground.vehicle.api.Light, area: SeatArea): Known {
        val sw = read<LightSwitch>("ambientSwitchGet") { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) }?.also { ambientOriginal.remember("switch", it) }
        if (sw == null) return Known(false, false, false)
        val b = read<Int>("ambientBrightnessGet") { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) }?.also { ambientOriginal.remember("brightness", it) }
        val c = read<Int>("ambientColorGet") { ok, fail -> light.getAmbientLightBrightnessColor(area, ok, fail) }?.also { ambientOriginal.remember("color", it) }
        return Known(true, b != null, c != null)
    }

    private fun quietNow(areas: Map<SeatArea, AreaBounds<Int>>) {
        val hvac = vehicle?.getHVAC() ?: return
        for ((area, bounds) in areas) {
            read<Int>("fanGet") { ok, fail -> hvac.getHvacFanSpeed(area, ok, fail) }?.let { fanOriginal.remember(area, it) }
            val speed = QUIET_FAN.coerceIn(bounds.minValue, bounds.maxValue)
            write("fanSet") { ok, fail -> hvac.setHvacFanSpeed(area, speed, { _, _ -> ok() }, fail) }
        }
    }

    /** Puts back what was remembered and waits (briefly) for the car to confirm, so a following release() can't drop it. */
    private fun restoreNow() {
        val v = vehicle ?: return
        val original = ambientOriginal.take()
        ambientState.reset()
        val fan = fanOriginal.take()
        val area = ambientArea
        val steps = mutableListOf<(CountDownLatch) -> Unit>()
        if (area != null) guarded(TAG, logged, "ambientRestore") {
            val light = v.getLight()
            // Switch last: the values go back while the light is still on.
            (original["brightness"] as? Int)?.let { b -> steps += { d -> write("ambientBrightness", done = d) { ok, fail -> light.setAmbientLightBrightness(area, b, { _, _ -> ok() }, fail) } } }
            (original["color"] as? Int)?.let { c -> steps += { d -> write("ambientColor", done = d) { ok, fail -> light.setAmbientLightBrightnessColor(area, c, { _, _ -> ok() }, fail) } } }
            (original["switch"] as? LightSwitch)?.let { sw -> steps += { d -> write("ambientSwitch", done = d) { ok, fail -> light.setAmbientLightSwitch(area, sw, { _, _ -> ok() }, fail) } } }
        }
        guarded(TAG, logged, "fanRestore") {
            val hvac = v.getHVAC()
            for ((a, s) in fan) steps += { d -> write("fanSet", done = d) { ok, fail -> hvac.setHvacFanSpeed(a, s, { _, _ -> ok() }, fail) } }
        }
        val done = CountDownLatch(steps.size)
        steps.forEach { it(done) }
        guarded(TAG, logged, "restoreWait") { done.await(1, TimeUnit.SECONDS) }
        lastWriteOk = true
    }

    private companion object {
        const val TAG = "PleosCabin"
        const val LIGHTS = "pleos.car.permission.CONTROL_CAR_LIGHTS"
        const val CLIMATE = "pleos.car.permission.CONTROL_CAR_CLIMATE"
        const val QUIET_FAN = 1
    }
}
