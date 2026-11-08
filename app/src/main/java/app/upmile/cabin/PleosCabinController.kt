package app.upmile.cabin

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
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
    private var vehicle: Vehicle? = null

    @Volatile private var ambientArea: SeatArea? = null // set once the capability check passed
    @Volatile private var fanAreas: Map<SeatArea, AreaBounds<Int>>? = null
    @Volatile private var lastWriteOk = true
    @Volatile private var released = false

    // Cabin thread, except the failure callbacks that forget what was written so the next frame retries.
    private val ambientOriginal = Remembered<String, Any>()
    private val fanOriginal = Remembered<SeatArea, Int>()
    @Volatile private var ambientOn = false
    @Volatile private var lastBrightness: Int? = null
    @Volatile private var lastColor: Int? = null

    init {
        guarded(TAG, logged, "vehicle", { vehicle?.let { v -> guarded(TAG, logged, "vehicleRelease") { v.release() } }; vehicle = null }) {
            vehicle = Vehicle(app).also { it.initialize() }
        }
        if (vehicle != null) background { checkCapabilities() }
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
                { cfg, areas -> if (cfg.accessType == AccessType.READ_WRITE && areas.isNotEmpty()) fanAreas = areas },
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
    private fun write(name: String, forget: () -> Unit = {}, call: (ok: () -> Unit, fail: (Exception) -> Unit) -> Unit) {
        val failed = { e: Throwable -> warn(name, e); lastWriteOk = false; forget() }
        guarded(TAG, logged, name, onFail = failed) { call({ lastWriteOk = true }, { e -> failed(e) }) }
    }

    private fun drain() {
        val v = vehicle ?: return
        val area = ambientArea ?: return
        while (true) {
            val (brightness, argb) = slot.take() ?: return
            val light = v.getLight()
            if (!ambientOn) {
                read<LightSwitch>("ambientSwitchGet") { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) }?.let { ambientOriginal.remember("switch", it) }
                read<Int>("ambientBrightnessGet") { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) }?.let { ambientOriginal.remember("brightness", it) }
                read<Int>("ambientColorGet") { ok, fail -> light.getAmbientLightBrightnessColor(area, ok, fail) }?.let { ambientOriginal.remember("color", it) }
                write("ambientSwitch", { ambientOn = false }) { ok, fail -> light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> ok() }, fail) }
                ambientOn = true
            }
            if (brightness != lastBrightness) {
                lastBrightness = brightness
                write("ambientBrightness", { lastBrightness = null }) { ok, fail -> light.setAmbientLightBrightness(area, brightness, { _, _ -> ok() }, fail) }
            }
            if (argb != lastColor) {
                lastColor = argb
                write("ambientColor", { lastColor = null }) { ok, fail -> light.setAmbientLightBrightnessColor(area, argb, { _, _ -> ok() }, fail) }
            }
        }
    }

    private fun quietNow(areas: Map<SeatArea, AreaBounds<Int>>) {
        val hvac = vehicle?.getHVAC() ?: return
        for ((area, bounds) in areas) {
            read<Int>("fanGet") { ok, fail -> hvac.getHvacFanSpeed(area, ok, fail) }?.let { fanOriginal.remember(area, it) }
            val speed = QUIET_FAN.coerceIn(bounds.minValue, bounds.maxValue)
            write("fanSet") { ok, fail -> hvac.setHvacFanSpeed(area, speed, { _, _ -> ok() }, fail) }
        }
    }

    private fun restoreNow() {
        val v = vehicle ?: return
        val original = ambientOriginal.take()
        ambientOn = false; lastBrightness = null; lastColor = null
        val area = ambientArea
        if (area != null) guarded(TAG, logged, "ambientRestore") {
            val light = v.getLight()
            // Switch last: the values go back while the light is still on.
            (original["brightness"] as? Int)?.let { b -> write("ambientBrightness") { ok, fail -> light.setAmbientLightBrightness(area, b, { _, _ -> ok() }, fail) } }
            (original["color"] as? Int)?.let { c -> write("ambientColor") { ok, fail -> light.setAmbientLightBrightnessColor(area, c, { _, _ -> ok() }, fail) } }
            (original["switch"] as? LightSwitch)?.let { s -> write("ambientSwitch") { ok, fail -> light.setAmbientLightSwitch(area, s, { _, _ -> ok() }, fail) } }
        }
        guarded(TAG, logged, "fanRestore") {
            val hvac = v.getHVAC()
            for ((a, s) in fanOriginal.take()) write("fanSet") { ok, fail -> hvac.setHvacFanSpeed(a, s, { _, _ -> ok() }, fail) }
        }
        lastWriteOk = true
    }

    private companion object {
        const val TAG = "PleosCabin"
        const val LIGHTS = "pleos.car.permission.CONTROL_CAR_LIGHTS"
        const val CLIMATE = "pleos.car.permission.CONTROL_CAR_CLIMATE"
        const val QUIET_FAN = 1
    }
}
