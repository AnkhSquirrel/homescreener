package ankhsquirrel.homescreener

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ankhsquirrel.homescreener.ui.HomescreenerApp
import ankhsquirrel.homescreener.ui.MainViewModel
import ankhsquirrel.homescreener.ui.theme.HomescreenerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            HomescreenerTheme {
                HomescreenerApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** This activity is exported (launcher entry), so the extra is only ever used as a lookup key. */
    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_FIX_SHORTCUT_ID)?.let(viewModel::openEntry)
    }

    companion object {
        const val EXTRA_FIX_SHORTCUT_ID = "ankhsquirrel.homescreener.extra.FIX_SHORTCUT_ID"
    }
}
