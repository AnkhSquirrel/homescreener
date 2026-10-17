package ankhsquirrel.homescreener.data

import kotlinx.serialization.Serializable

/** A file pinned to the home screen. [id] doubles as the [android.content.pm.ShortcutInfo] id. */
@Serializable
data class ShortcutEntry(
    val id: String,
    val uri: String,
    val mimeType: String,
    val fileName: String,
    val label: String,
    val createdAt: Long,
)

/** Default shortcut label for a file: its name without the extension. */
fun defaultLabel(fileName: String): String =
    fileName.substringBeforeLast('.').ifBlank { fileName }
