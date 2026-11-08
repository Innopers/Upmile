package app.upmile.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.VehicleSignals
import app.upmile.core.vehicle.VehicleSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The emulator reports the steering angle with the opposite sign of the Virtual Console. */
const val STEERING_SIGN = -1f

/** Measured on Pleos_Connect_v2.1.0 (see README 개발 메모). */
const val DEFAULT_INVERT_SEAT = true

internal fun mapGear(g: VehicleGear?): Gear = when (g) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
    VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.D
    else -> Gear.UNKNOWN
}

/** Door position 0 = closed. */
internal fun doorsClosed(positions: Collection<Int>): Boolean = positions.all { it == 0 }

internal fun driverSeated(occupied: Boolean, invert: Boolean): Boolean = occupied xor invert

internal fun steeringDeg(raw: Float): Float = raw * STEERING_SIGN

/** The SDK range getter reports metres (AAOS RANGE_REMAINING). */
internal fun rangeKm(meters: Float): Int = (meters / 1000f).roundToInt()

internal fun energyPercent(evSoc: Int?, fuel: Int?): Int? = evSoc ?: fuel

/** (SDK type name, on/blink) -> console-style ids ("SEAT_BELT" -> "SeatBelt"); null input = list unavailable. */
internal fun activeTelltaleIds(entries: List<Pair<String, Boolean>>?): List<String>? =
    entries?.filter { it.second }?.map { (name, _) ->
        name.split('_').joinToString("") { it.lowercase().replaceFirstChar(Char::titlecase) }
    }

// Pleos Gear/Odometer range are the brief's sources; registerCallback because the device's car service lacks subscribePropertyEvents (NoSuchMethodError).
@Suppress("DEPRECATION")
class PleosVehicleSignals(private val context: Context, private val invertSeatOccupancy: Boolean) : VehicleSignals {
    private val state = MutableStateFlow(
        VehicleSnapshot(
            gear = Gear.UNKNOWN, speedMps = 0f, brakePressed = false, steeringDeg = 0f, doorsClosed = true,
            activeTelltales = null, energyPercent = null, rangeKm = null, beltBuckled = null, driverSeated = false,
            wiperOn = false, headlightsOn = false, outsideTempC = null, timestampMs = SystemClock.elapsedRealtime(),
        )
    )
    override val snapshots: StateFlow<VehicleSnapshot> = state.asStateFlow()

    private val undo = CopyOnWriteArrayList<() -> Unit>()
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val doorPositions = ConcurrentHashMap<DoorValue, Int>()
    @Volatile private var evSoc: Int? = null
    @Volatile private var fuel: Int? = null
    @Volatile private var started = false
    private var vehicle: Vehicle? = null
    private var car: Car? = null

    private fun edit(f: VehicleSnapshot.() -> VehicleSnapshot) =
        state.update { it.f().copy(timestampMs = SystemClock.elapsedRealtime()) }

    private fun warn(field: String, e: Throwable) {
        if (logged.add(field)) Log.w(TAG, "$field unavailable", e)
    }

    /** Runs [block]; failures (incl. LinkageError such as NoSuchMethodError, never VM errors) are logged once and returned to [onFail]. */
    private inline fun guard(field: String, onFail: (Throwable) -> Unit = {}, block: () -> Unit) {
        try { block() } catch (e: Exception) { warn(field, e); onFail(e) } catch (e: LinkageError) { warn(field, e); onFail(e) }
    }

    /** Getter first (listeners get no initial callback), then the listener; [onFail] runs when the field turns out to be unreadable. */
    private fun <T : Any> field(
        name: String,
        get: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
        register: () -> Unit,
        unregister: () -> Unit,
        onFail: () -> Unit = {},
        apply: (T) -> Unit,
    ) {
        val nonNull = { v: T? -> if (v != null) apply(v) }
        val fail = { e: Exception -> warn(name, e); onFail() }
        guard(name, { onFail() }) { get(nonNull, fail) }
        guard(name, { onFail() }) {
            register()
            undo += { guard(name) { unregister() } }
        }
    }

    override fun start() {
        if (started) return
        started = true
        var v: Vehicle? = null
        guard("vehicle", { guard("vehicle") { v?.release() }; v = null }) {
            v = Vehicle(context.applicationContext).also { it.initialize() }
        }
        vehicle = v
        v?.let(::startPleos)
        guard("car") { startCarProperties() }
    }

    private fun startPleos(v: Vehicle) {
        guard("gearBlock") {
            v.getGear().let { g ->
                val l = object : GearSelectionListener {
                    override fun onSelected(gear: VehicleGear?) = edit { copy(gear = mapGear(gear)) }
                    override fun onFailed(e: Exception) = warn("gear", e)
                }
                field<VehicleGear>("gear", g::getCurrentGearSelectionState, { g.registerGearSelection(l) }, { g.unregisterGearSelection(l) }) {
                    edit { copy(gear = mapGear(it)) }
                }
            }
        }
        guard("brakeBlock") {
            v.getBrake().let { b ->
                val l = object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(status: Boolean?) { status?.let { edit { copy(brakePressed = it) } } }
                    override fun onFailed(e: Exception) = warn("brake", e)
                }
                field<Boolean>("brake", b::getCurrentBrakePedalStatus, { b.registerBrakePedalStatus(l) }, { b.unregisterBrakePedalStatus(l) }) {
                    edit { copy(brakePressed = it) }
                }
            }
        }
        guard("steeringwheelBlock") {
            v.getSteeringWheel().let { s ->
                val l = object : SteeringWheelAngleListener {
                    override fun onSteeringWheelAngleUpdated(angle: Float?) { angle?.let { edit { copy(steeringDeg = steeringDeg(it)) } } }
                    override fun onFailed(e: Exception) = warn("steering", e)
                }
                field<Float>("steering", s::getCurrentSteeringWheelAngle, { s.registerSteeringWheelAngle(l) }, { s.unregisterSteeringWheelAngle(l) }) {
                    edit { copy(steeringDeg = steeringDeg(it)) }
                }
            }
        }
        guard("doorBlock") {
            v.getDoor().let { d ->
                listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT).forEach { dv ->
                    val area = DoorArea(dv)
                    val set = { p: Int -> doorPositions[dv] = p; edit { copy(doorsClosed = doorsClosed(doorPositions.values)) } }
                    val l = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) { pos?.let(set) }
                        override fun onFailed(e: Exception) = warn("door", e)
                    }
                    field<Int>("door", { ok, fail -> d.getCurrentDoorPosition(area, ok, fail) },
                        { d.registerDoorPosition(area, l) }, { d.unregisterDoorPosition(area, l) }, apply = set)
                }
            }
        }
        guard("safetyBlock") {
            v.getSafety().let { s ->
                val set = { t: Telltales ->
                    edit { copy(activeTelltales = activeTelltaleIds(t.telltales.map { it.type.name to (it.state != TelltaleState.OFF) })) }
                }
                val unavailable = { edit { copy(activeTelltales = null) } }
                val l = object : TelltaleInfoListener {
                    override fun onTelltaleInfoUpdated(telltales: Telltales?) { telltales?.let(set) }
                    override fun onFailed(e: Exception) { warn("telltales", e); unavailable() }
                }
                field<Telltales>("telltales", s::getTelltalesInfo, { s.registerTelltales(l) }, { s.unregisterTelltales(l) }, unavailable, set)
            }
        }
        guard("evbatteryBlock") {
            v.getEvBattery().let { b ->
                val set = { p: Int -> evSoc = p; edit { copy(energyPercent = energyPercent(evSoc, fuel)) } }
                val l = object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(value: Int?) { value?.let(set) }
                    override fun onFailed(e: Exception) = warn("evSoc", e)
                }
                field<Int>("evSoc", b::getEvBatteryStateOfCharge, { b.registerEvBatteryStateOfCharge(l) }, { b.unregisterEvBatteryStateOfCharge(l) }, apply = set)
            }
        }
        guard("fuelBlock") {
            v.getFuel().let { f ->
                val set = { p: Int -> fuel = p; edit { copy(energyPercent = energyPercent(evSoc, fuel)) } }
                val l = object : FuelPercentListener {
                    override fun onFuelPercentUpdated(value: Int?) { value?.let(set) }
                    override fun onFailed(e: Exception) = warn("fuel", e)
                }
                field<Int>("fuel", f::getFuelPercent, { f.registerFuelPercent(l) }, { f.unregisterFuelPercent(l) }, apply = set)
            }
        }
        guard("odometerBlock") {
            v.getOdometer().let { o ->
                val set = { p: Float -> edit { copy(rangeKm = rangeKm(p)) } }
                val l = object : RangeRemainingListener {
                    override fun onRangeRemainingChanged(rangeRemaining: Float?) { rangeRemaining?.let(set) }
                    override fun onFailed(e: Exception) = warn("range", e)
                }
                field<Float>("range", o::getRangeRemainingDistance, { o.registerRangeRemainingListener(l) }, { o.unregisterRangeRemainingListener(l) }, apply = set)
            }
        }
        guard("seatBlock") {
            v.getSeat().let { s ->
                val area = SeatArea(SeatValue.ROW_1_LEFT)
                val set = { p: Boolean -> edit { copy(driverSeated = driverSeated(p, invertSeatOccupancy)) } }
                val l = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupancy: Boolean?) { occupancy?.let(set) }
                    override fun onFailed(e: Exception) = warn("seat", e)
                }
                field<Boolean>("seat", { ok, fail -> s.getCurrentSeatOccupancy(area, ok, fail) },
                    { s.registerSeatOccupancy(area, l) }, { s.unregisterSeatOccupancy(area, l) }, apply = set)
                val setBelt = { p: Boolean -> edit { copy(beltBuckled = p) } }
                val bl = object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(state: Boolean?) { state?.let(setBelt) }
                    override fun onFailed(e: Exception) = warn("belt", e)
                }
                field<Boolean>("belt", { ok, fail -> s.getCurrentSeatBeltBuckledState(area, ok, fail) },
                    { s.registerSeatBeltBuckledState(area, bl) }, { s.unregisterSeatBeltBuckledState(area, bl) },
                    onFail = { edit { copy(beltBuckled = null) } }, apply = setBelt)
            }
        }
        guard("wiperBlock") {
            v.getWiper().let { w ->
                val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
                val set = { p: WiperState -> edit { copy(wiperOn = p == WiperState.ON) } }
                val l = object : WiperStateListener {
                    override fun onWiperStateUpdated(state: WiperState?) { state?.let(set) }
                    override fun onFailed(e: Exception) = warn("wiper", e)
                }
                field<WiperState>("wiper", { ok, fail -> w.getCurrentWiperState(area, ok, fail) },
                    { w.registerWiperState(area, l) }, { w.unregisterWiperState(area, l) }, apply = set)
            }
        }
        guard("lightBlock") {
            v.getLight().let { li ->
                val set = { p: LightState -> edit { copy(headlightsOn = p == LightState.ON) } }
                val l = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) { state?.let(set) }
                    override fun onFailed(e: Exception) = warn("headlights", e)
                }
                field<LightState>("headlights", li::getCurrentLowBeamLightState, { li.registerLowBeamLightState(l) }, { li.unregisterLowBeamLightState(l) }, apply = set)
            }
        }
    }

    /** Speed and outside temperature come from the standard AAOS property service. */
    private fun startCarProperties() {
        val c = try { Car.createCar(context.applicationContext) } catch (e: Exception) { warn("car", e); null }
        if (c == null || !c.isConnected) { if (c != null) warn("car", IllegalStateException("not connected")); c?.disconnect(); return }
        car = c
        val mgr = try { c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager } catch (e: Exception) { warn("car", e); return }
        property(mgr, "speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI) { v -> edit { copy(speedMps = v) } }
        property(mgr, "outsideTemp", VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_ONCHANGE) { v -> edit { copy(outsideTempC = v) } }
    }

    private fun property(mgr: CarPropertyManager, name: String, id: Int, rate: Float, apply: (Float) -> Unit) {
        guard(name) { apply(mgr.getFloatProperty(id, 0)) }
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) { (value.value as? Float)?.let(apply) }
            override fun onErrorEvent(propertyId: Int, zone: Int) = warn(name, IllegalStateException("error event $propertyId"))
        }
        guard(name) {
            mgr.registerCallback(cb, id, rate)
            undo += { guard(name) { mgr.unregisterCallback(cb) } }
        }
    }

    override fun stop() {
        undo.forEach { it() }
        undo.clear()
        guard("release") { vehicle?.release() }
        vehicle = null
        started = false
        guard("car") { car?.disconnect() }
        car = null
    }

    private companion object { const val TAG = "PleosVehicleSignals" }
}
