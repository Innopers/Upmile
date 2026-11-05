package app.upmile.core.record

import kotlinx.serialization.json.Json
import java.io.File

class RecordStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Record = if (file.exists()) json.decodeFromString(Record.serializer(), file.readText()) else Record.EMPTY

    fun save(r: Record) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Record.serializer(), r))
        if (!tmp.renameTo(file)) { // some filesystems refuse to rename over an existing file
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace ${file.path}" }
        }
    }
}
