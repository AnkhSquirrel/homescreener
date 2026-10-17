package ankhsquirrel.homescreener.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Caches generated shortcut icons as PNGs so the UI and shortcut updates don't re-render them. */
class IconStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "icons")

    fun save(id: String, icon: Bitmap) {
        dir.mkdirs()
        file(id).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun load(id: String): Bitmap? =
        file(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(dir, "$id.png")
}
