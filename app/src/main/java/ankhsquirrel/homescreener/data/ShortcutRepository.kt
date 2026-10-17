package ankhsquirrel.homescreener.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

/** Process-wide store of [ShortcutEntry]s, persisted as JSON in `filesDir`. */
class ShortcutRepository private constructor(private val file: AtomicFile) {

    private val mutex = Mutex()
    private var cache: List<ShortcutEntry>? = null

    suspend fun all(): List<ShortcutEntry> = mutex.withLock { load() }

    suspend fun get(id: String): ShortcutEntry? = all().firstOrNull { it.id == id }

    suspend fun upsert(entry: ShortcutEntry) = mutex.withLock {
        val current = load()
        val updated = if (current.any { it.id == entry.id }) {
            current.map { if (it.id == entry.id) entry else it }
        } else {
            current + entry
        }
        save(updated)
    }

    suspend fun remove(id: String) = mutex.withLock {
        save(load().filterNot { it.id == id })
    }

    private suspend fun load(): List<ShortcutEntry> = cache ?: withContext(Dispatchers.IO) {
        val base = file.baseFile
        if (!base.exists()) return@withContext emptyList()
        try {
            ShortcutCodec.decode(file.readFully().decodeToString())
        } catch (e: SerializationException) {
            // Keep the unreadable file for inspection instead of overwriting it on the next save.
            val backup = File(base.parentFile, "${base.name}.corrupt-${System.currentTimeMillis()}")
            Log.e(TAG, "Unreadable shortcut store, moved to ${backup.name}", e)
            base.renameTo(backup)
            emptyList()
        }
    }.also { cache = it }

    private suspend fun save(entries: List<ShortcutEntry>) {
        withContext(Dispatchers.IO) {
            val out = file.startWrite()
            try {
                out.write(ShortcutCodec.encode(entries).encodeToByteArray())
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
                throw e
            }
        }
        cache = entries
    }

    companion object {
        private const val TAG = "ShortcutRepository"
        private const val FILE_NAME = "shortcuts.json"

        @Volatile
        private var instance: ShortcutRepository? = null

        fun get(context: Context): ShortcutRepository = instance ?: synchronized(this) {
            instance ?: ShortcutRepository(
                AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))
            ).also { instance = it }
        }
    }
}
