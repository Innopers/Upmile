package app.upmile.core.record

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

class RecordStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Record = if (!file.exists()) Record.EMPTY else try {
        json.decodeFromString(Record.serializer(), file.readText())
    } catch (_: SerializationException) {
        Record.EMPTY // corrupt file: start fresh rather than crash the app
    }

    fun save(r: Record) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Record.serializer(), r))
        if (!tmp.renameTo(file)) { // some filesystems refuse to rename over an existing file
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace ${file.path}" }
        }
    }
}
