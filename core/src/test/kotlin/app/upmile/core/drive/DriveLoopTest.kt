package app.upmile.core.drive

import app.upmile.core.location.ScenarioLocationSource
import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.record.Condition
import app.upmile.core.record.ExperienceType
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.route.SegmentFinder
import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.ScenarioGenerator
import app.upmile.core.vehicle.ScenarioJson
import app.upmile.core.vehicle.ScenarioVehicleSignals
import app.upmile.core.vehicle.VehicleSnapshot
import app.upmile.core.voice.QueuedVoice
import app.upmile.core.voice.SpeechEngine
import app.upmile.core.voice.Voice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class RecordingVoice : Voice {
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    val said = mutableListOf<Pair<String, Int>>()
    override fun say(text: String, priority: Int) { said += text to priority }
    override fun stop() {}
}

private class InstantEngine : SpeechEngine {
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    val spoken = mutableListOf<String>()
    override fun speak(text: String, onDone: () -> Unit) { spoken += text; onDone() }
    override fun stop() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriveLoopTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun readAsset(path: String) = javaClass.classLoader.getResource(path)!!.readText()
    private val square = """{"features":[{"properties":{"sgg":"11110","sggnm":"테스트구"},"geometry":{"type":"Polygon",
        "coordinates":[[[126.0,36.0],[128.0,36.0],[128.0,38.0],[126.0,38.0],[126.0,36.0]]]}}]}"""

    private val mPerDeg = 111_195.0
    private fun at(m: Double) = GeoPoint(37.0 + m / mPerDeg, 127.0)
    private val route = Route("r", "r", listOf(at(0.0), at(5000.0)), emptyList(), 5000, 600)
    private val merge = HardSegment("r:highway_merge:1", SegmentType.HIGHWAY_MERGE, "고속도로 합류", "양재IC", at(2000.0), 2000, emptyMap())
    private val left = HardSegment("r:multilane_left:2", SegmentType.MULTILANE_LEFT, "다차선 좌회전", "판교역", at(3000.0), 3000, emptyMap())

    private fun snap(t: Long, gear: Gear = Gear.D, wiper: Boolean = false) =
        VehicleSnapshot(gear, 10f, false, 0f, true, emptyList(), null, null, true, true, wiper, false, null, t)

    private fun loop(voice: Voice, store: RecordStore, segs: List<HardSegment> = listOf(merge), rehearsed: Set<String> = setOf(merge.id)) =
        DriveLoop(DriveTracker(route, segs), voice, store, RegionLocator(square), "s1", rehearsed) { 42L }

    @Test fun approachThenPass_oneSpokenLine_oneStamp_andArrivalSetsUnseen() {
        val store = RecordStore(tmp.newFile("record.json")); val voice = RecordingVoice()
        val l = loop(voice, store)
        l.tick(snap(0), at(700.0)); l.tick(snap(200), at(805.0)); l.tick(snap(400), at(900.0))
        assertEquals(listOf("1.2km 앞, 아까 연습한 고속도로 합류예요." to 10), voice.said)
        l.tick(snap(600, wiper = true), at(2200.0)); l.tick(snap(800), at(2300.0))
        assertEquals(1, voice.said.size)
        l.tick(snap(1000, Gear.P), at(4000.0)) // 3.3 km net displacement, P, seated -> arrival
        val r = store.load()
        assertEquals("s1", r.unseenSessionId)
        assertEquals(1, r.stamps.size)
        assertTrue(r.cards.any { it.type == ExperienceType.FIRST_DRIVE } && r.cards.any { it.type == ExperienceType.RAIN })
        assertEquals(listOf("도착했어요. 오늘 기록을 정리했어요." to 5), voice.said.drop(1))
    }

    @Test fun unrehearsedSegment_neutralLine_cardButNoStamp() {
        val store = RecordStore(tmp.newFile("record.json")); val voice = RecordingVoice()
        val l = loop(voice, store, listOf(merge, left), rehearsed = setOf(merge.id))
        var t = 0L
        for (m in listOf(700.0, 805.0, 1900.0, 2200.0, 3200.0)) { l.tick(snap(t), at(m)); t += 200 }
        assertEquals(listOf("1.2km 앞, 아까 연습한 고속도로 합류예요.", "1.1km 앞, 다차선 좌회전이에요."), voice.said.map { it.first })
        l.tick(snap(t, Gear.P), at(4000.0))
        val r = store.load()
        assertEquals(listOf(merge.id), r.stamps.map { it.segmentId })
        assertEquals("양재IC", r.stamps.single().place)
        assertTrue(r.cards.any { it.type == ExperienceType.MULTILANE_LEFT && it.place == "판교역" })
    }

    @Test fun failedSave_keepsSessionPending_andRetriesNextTick() {
        val dir = java.io.File(tmp.root, "later") // missing parent dir: the first save throws
        val store = RecordStore(java.io.File(dir, "record.json")); val voice = RecordingVoice()
        val l = loop(voice, store)
        l.tick(snap(0), at(0.0)); l.tick(snap(200), at(2200.0)); l.tick(snap(400, Gear.P), at(4000.0))
        assertTrue(l.lastError != null)
        assertTrue(voice.said.none { "도착" in it.first })
        dir.mkdirs()
        l.tick(snap(600, Gear.P), at(4000.0))
        assertEquals("s1", store.load().unseenSessionId)
        assertEquals(1, store.load().stamps.size)
        assertEquals(1, voice.said.count { "도착" in it.first })
        l.tick(snap(800, Gear.P), at(4000.0))
        assertEquals(1, voice.said.count { "도착" in it.first })
    }

    @Test fun nullPosition_isNotTracked_butConditionsAreNoted() {
        val store = RecordStore(tmp.newFile("record.json")); val voice = RecordingVoice()
        val l = loop(voice, store)
        l.tick(snap(0, wiper = true), null)
        l.tick(snap(200), at(0.0)); l.tick(snap(400, Gear.P), at(4000.0))
        assertTrue(store.load().cards.any { it.type == ExperienceType.RAIN })
    }

    @Test fun distanceText_roundsToHundred() {
        val l = loop(RecordingVoice(), RecordStore(tmp.newFile("r.json")))
        assertEquals("800m", l.distanceText(804)); assertEquals("1.2km", l.distanceText(1195)); assertEquals("1km", l.distanceText(950))
        assertEquals("바로", l.distanceText(30))
    }

    @Test fun withCopula_picksByFinalConsonant() {
        assertEquals("고속도로 합류예요", withCopula("고속도로 합류"))
        assertEquals("판교역 사거리 좌회전이에요", withCopula("판교역 사거리 좌회전"))
        assertEquals("IC이에요", withCopula("IC"))
    }

    @Test fun commuteScenario_endToEnd() = runTest(UnconfinedTestDispatcher()) {
        val route = ScenarioGenerator.load(::readAsset)
        val segs = SegmentFinder.find(route, true)
        var now = 0L
        val signals = ScenarioVehicleSignals(ScenarioJson.decode(readAsset("scenarios/commute_drive.json")), clock = { now })
        val location = ScenarioLocationSource(signals, backgroundScope)
        val engine = InstantEngine()
        val store = RecordStore(tmp.newFile("record.json"))
        val loop = DriveLoop(DriveTracker(route, segs), QueuedVoice(engine, scope = backgroundScope), store,
            RegionLocator(readAsset("regions/sigungu.geojson")), "commute-1", segs.map { it.id }.toSet()) { now }
        signals.start()
        while (now <= 150_000) { signals.tick(); loop.tick(signals.snapshots.value, location.location.value); now += 200 }

        val r = store.load()
        val types = r.cards.map { it.type }.toSet()
        assertTrue(types.toString(), types.containsAll(listOf(ExperienceType.FIRST_DRIVE, ExperienceType.HIGHWAY_MERGE,
            ExperienceType.MULTILANE_LEFT, ExperienceType.GARAGE_RAMP, ExperienceType.RAIN)))
        assertEquals(listOf(segs.first { it.type == SegmentType.MULTILANE_LEFT }.id), r.revisitSegments)
        assertEquals(segs.map { it.id }.toSet(), r.stamps.map { it.segmentId }.toSet())
        assertEquals("commute-1", r.unseenSessionId)
        assertEquals("41135", r.regions.single().code)
        val approach = engine.spoken.filter { "아까 연습한" in it }
        // merge, left turn and garage ramp are each announced exactly once
        assertEquals(engine.spoken.toString(), 3, approach.size)
        assertTrue(segs.filter { it.type != SegmentType.GARAGE_RAMP }.all { s -> approach.any { s.title in it } })
        assertTrue(Condition.RAIN in r.cards.first { it.type == ExperienceType.RAIN }.conditions)
    }
}
