package ankhsquirrel.homescreener.launch

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import ankhsquirrel.homescreener.MainActivity
import ankhsquirrel.homescreener.R
import ankhsquirrel.homescreener.data.DocumentAccess
import ankhsquirrel.homescreener.data.ShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible trampoline behind every pinned shortcut: resolves the shortcut id from our own store
 * and hands the file to its default viewer.
 *
 * Security: only the id extra is read from the incoming intent. The URI and MIME type always
 * come from [ShortcutRepository].
 */
@SuppressLint("CustomSplashScreen") // Name-based false positive: this is a trampoline, not a splash screen.
class LaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.takeIf { it.action == ACTION_OPEN_SHORTCUT }?.getStringExtra(EXTRA_SHORTCUT_ID)
        if (id == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            open(id)
            finish()
        }
    }

    private suspend fun open(id: String) {
        val entry = ShortcutRepository.get(this).get(id)
        if (entry == null) {
            toast(R.string.error_unknown_shortcut)
            return
        }

        val uri = entry.uri.toUri()
        val reachable = withContext(Dispatchers.IO) { DocumentAccess.isReachable(applicationContext, uri) }
        if (!reachable) {
            toast(R.string.error_file_unavailable)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_FIX_SHORTCUT_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            return
        }

        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, entry.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.error_no_viewer)
        }
    }

    private fun toast(@StringRes message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ACTION_OPEN_SHORTCUT = "ankhsquirrel.homescreener.action.OPEN_SHORTCUT"
        private const val EXTRA_SHORTCUT_ID = "ankhsquirrel.homescreener.extra.SHORTCUT_ID"

        fun intentFor(context: Context, shortcutId: String): Intent =
            Intent(context, LaunchActivity::class.java)
                .setAction(ACTION_OPEN_SHORTCUT)
                .putExtra(EXTRA_SHORTCUT_ID, shortcutId)
    }
}
