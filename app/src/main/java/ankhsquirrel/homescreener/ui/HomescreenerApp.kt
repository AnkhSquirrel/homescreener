package ankhsquirrel.homescreener.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ANY_FILE = arrayOf("*/*")

@Composable
fun HomescreenerApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pickNewFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onNewFilePicked)
    }
    val pickReplacement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onReplacementPicked)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.onMessageShown()
        snackbarHostState.showSnackbar(resources.getString(message))
    }

    val editor = state.editor
    if (editor == null) {
        HomeScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onAddFile = { pickNewFile.launch(ANY_FILE) },
            onOpenEntry = viewModel::openEntry,
        )
    } else {
        BackHandler(onBack = viewModel::closeEditor)
        EditShortcutScreen(
            editor = editor,
            pinSupported = state.pinSupported,
            snackbarHostState = snackbarHostState,
            onBack = viewModel::closeEditor,
            onLabelChange = viewModel::onLabelChange,
            onReplaceFile = { pickReplacement.launch(ANY_FILE) },
            onPin = viewModel::pin,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        )
    }
}
