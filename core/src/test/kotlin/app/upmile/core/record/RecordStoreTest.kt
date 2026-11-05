package app.upmile.core.record

import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val rec = Record(
        listOf(ExperienceCard(ExperienceType.RAIN, 2, 5L, "강남구", setOf(Condition.RAIN))),
        listOf(SegmentStamp("합류", SegmentType.HIGHWAY_MERGE, setOf(Condition.NIGHT), 7L)),
        listOf(Region("11680", "강남구")), listOf("합류"), setOf("S1"), "S1",
    )

    @Test fun missingFile_loadsEmpty() = assertEquals(Record.EMPTY, RecordStore(File(tmp.root, "record.json")).load())

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
