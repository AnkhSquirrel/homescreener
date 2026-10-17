package ankhsquirrel.homescreener.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On-disk format of the shortcut store. Bump [ShortcutCodec.CURRENT_VERSION] on incompatible changes. */
@Serializable
internal data class StoreFile(
    val version: Int,
    val entries: List<ShortcutEntry> = emptyList(),
)

object ShortcutCodec {
    const val CURRENT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entries: List<ShortcutEntry>): String =
        json.encodeToString(StoreFile.serializer(), StoreFile(CURRENT_VERSION, entries))

    /** @throws kotlinx.serialization.SerializationException if [text] is not a valid store. */
    fun decode(text: String): List<ShortcutEntry> =
        json.decodeFromString(StoreFile.serializer(), text).entries
}
