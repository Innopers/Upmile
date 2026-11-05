package app.upmile.core.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/** The one writer of [file]. The drive service and the UI both change the record through [update] only. */
class RecordStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _records = MutableStateFlow(load())

    /** The last saved record; emits on every [save] / [update], so the UI never polls the file. */
    val records: StateFlow<Record> = _records

    /** A corrupt (or old-format) file is moved aside to `<name>.corrupt-<ms>` before returning EMPTY, never overwritten. */
    @Synchronized
    fun load(): Record = if (!file.exists()) Record.EMPTY else try {
        json.decodeFromString(Record.serializer(), file.readText())
    } catch (_: SerializationException) {
        file.renameTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
        Record.EMPTY
    }

    @Synchronized
    fun save(r: Record) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Record.serializer(), r))
        if (!tmp.renameTo(file)) { // some filesystems refuse to rename over an existing file
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace ${file.path}" }
        }
        _records.value = r
    }

    /** Atomic read-modify-write: load, apply [f], save. Throws (and changes nothing) if the save fails. */
    @Synchronized
    fun update(f: (Record) -> Record): Record = f(load()).also(::save)
}
