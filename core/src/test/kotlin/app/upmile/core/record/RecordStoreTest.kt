package app.upmile.core.record

import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.concurrent.thread

class RecordStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val rec = Record(
        listOf(ExperienceCard(ExperienceType.RAIN, 2, 5L, "강남구", setOf(Condition.RAIN))),
        listOf(SegmentStamp("commute:highway_merge:1", "고속도로 합류", "양재IC", SegmentType.HIGHWAY_MERGE, setOf(Condition.NIGHT), 7L)),
        listOf(Region("11680", "강남구")), listOf("commute:highway_merge:1"), setOf("S1"), "S1",
    )

    @Test fun missingFile_loadsEmpty() = assertEquals(Record.EMPTY, RecordStore(File(tmp.root, "record.json")).load())

    @Test fun corruptFile_isMovedAside_thenLoadsEmpty() {
        val f = File(tmp.root, "record.json").apply { writeText("{not json") }
        val s = RecordStore(f)
        assertEquals(Record.EMPTY, s.load())
        val aside = tmp.root.listFiles()!!.single { it.name.startsWith("record.json.corrupt-") }
        assertEquals("{not json", aside.readText())
        s.save(rec) // the next save no longer overwrites the bad file
        assertEquals("{not json", aside.readText())
    }

    @Test fun oldFormatFile_doesNotCrash() {
        val f = File(tmp.root, "record.json")
        f.writeText("""{"cards":[],"stamps":[{"segmentTitle":"합류","type":"HIGHWAY_MERGE","conditions":[],"atMs":1}],
            "regions":[],"revisitSegments":["합류"],"processedSessionIds":[]}""")
        assertEquals(Record.EMPTY, RecordStore(f).load())
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("record.json.corrupt-") })
    }

    @Test fun update_isReadModifyWrite_andPublishes() {
        val s = RecordStore(File(tmp.root, "record.json"))
        s.save(rec)
        val out = s.update { it.copy(unseenSessionId = null) }
        assertEquals(rec.copy(unseenSessionId = null), out)
        assertEquals(out, s.load())
        assertEquals(out, s.records.value)
    }

    @Test fun concurrentUpdates_loseNothing() {
        val s = RecordStore(File(tmp.root, "record.json"))
        val threads = (0 until 4).map { k ->
            thread { repeat(25) { i -> s.update { it.copy(processedSessionIds = it.processedSessionIds + "$k-$i") } } }
        }
        threads.forEach { it.join() }
        assertEquals(100, s.load().processedSessionIds.size)
    }

    @Test fun roundTrip() {
        val s = RecordStore(File(tmp.root, "record.json"))
        s.save(rec)
        assertEquals(rec, s.load())
    }

    @Test fun store_atomicSave_survivesPartialWrite() {
        val f = File(tmp.root, "record.json")
        val s = RecordStore(f)
        s.save(rec)
        File(tmp.root, "record.json.tmp").writeText("{\"cards\":[") // crash mid-write
        assertEquals(rec, s.load())
        s.save(rec.copy(unseenSessionId = null)) // next save overwrites the stale tmp
        assertEquals(null, s.load().unseenSessionId)
        assertEquals(false, File(tmp.root, "record.json.tmp").exists())
    }
}
