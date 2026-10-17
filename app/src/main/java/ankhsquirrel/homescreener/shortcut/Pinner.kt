package ankhsquirrel.homescreener.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ankhsquirrel.homescreener.data.ShortcutEntry
import ankhsquirrel.homescreener.launch.LaunchActivity

/** Thin wrapper around [ShortcutManagerCompat] for pinned shortcuts. */
object Pinner {

    private const val TAG = "Pinner"

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Ids of our shortcuts currently pinned (and enabled) on the home screen. */
    fun pinnedIds(context: Context): Set<String> =
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { it.isEnabled }
            .mapTo(HashSet()) { it.id }

    /**
     * Asks the launcher to pin [entry]. Returns false if the launcher doesn't support it.
     * The launcher shows its own confirmation; the result is visible later via [pinnedIds].
     */
    fun requestPin(context: Context, entry: ShortcutEntry, icon: Bitmap?): Boolean {
        if (!isSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, build(context, entry, icon), null)
    }

    /** Updates label/intent of an existing shortcut; [icon] null keeps the current icon. */
    fun update(context: Context, entry: ShortcutEntry, icon: Bitmap?) {
        try {
            ShortcutManagerCompat.updateShortcuts(context, listOf(build(context, entry, icon)))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to update shortcut ${entry.id}", e)
        }
    }

    /** Greys out the shortcut on the home screen; tapping it shows [message]. */
    fun disable(context: Context, id: String, message: CharSequence) {
        try {
            ShortcutManagerCompat.disableShortcuts(context, listOf(id), message)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to disable shortcut $id", e)
        }
    }

    private fun build(context: Context, entry: ShortcutEntry, icon: Bitmap?): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, entry.id)
            .setShortLabel(entry.label)
            .setLongLabel(entry.label)
            .setIntent(LaunchActivity.intentFor(context, entry.id))
            .apply { if (icon != null) setIcon(IconCompat.createWithAdaptiveBitmap(icon)) }
            .build()
}
