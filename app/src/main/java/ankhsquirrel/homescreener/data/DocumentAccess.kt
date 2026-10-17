package ankhsquirrel.homescreener.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

data class DocumentInfo(val displayName: String, val mimeType: String)

/** Storage Access Framework helpers. All calls may do IPC; keep them off the main thread. */
object DocumentAccess {

    private const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private const val FALLBACK_MIME = "application/octet-stream"

    /** Persists read access to [uri] across reboots. Returns false if the provider refuses. */
    fun takeRead(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, READ)
        true
    } catch (e: SecurityException) {
        false
    }

    fun release(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, READ)
        } catch (e: SecurityException) {
            // Already released.
        }
    }

    fun persistedUris(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }

    /** True if we still hold a grant for [uri] and its provider still knows the document. */
    fun isReachable(context: Context, uri: Uri): Boolean {
        if (uri !in persistedUris(context)) return false
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            // Providers signal missing documents with a variety of exceptions.
            false
        }
    }

    fun describe(context: Context, uri: Uri): DocumentInfo {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

        val mime = context.contentResolver.getType(uri)?.takeUnless { it == FALLBACK_MIME }
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: FALLBACK_MIME

        return DocumentInfo(name, mime)
    }
}
