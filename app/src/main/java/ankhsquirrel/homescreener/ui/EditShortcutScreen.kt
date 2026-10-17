package ankhsquirrel.homescreener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ankhsquirrel.homescreener.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShortcutScreen(
    editor: EditorState,
    pinSupported: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLabelChange: (String) -> Unit,
    onReplaceFile: () -> Unit,
    onPin: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = editor.existing == null
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShortcutIcon(editor.file.icon, size = 96.dp)

            if (!editor.available) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        stringResource(R.string.file_unavailable_explainer),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = editor.label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.label_hint)) },
                singleLine = true,
                isError = !editor.canSave,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.file_details, editor.file.fileName, editor.file.mimeType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isNew) {
                Button(onClick = onPin, enabled = editor.canSave && pinSupported, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_pin))
                }
                OutlinedButton(onClick = onReplaceFile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_replace_file))
                }
            } else {
                Button(onClick = onSave, enabled = editor.canSave && editor.isDirty, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = onPin, enabled = editor.canSave && pinSupported, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_repin))
                }
                OutlinedButton(onClick = onReplaceFile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_replace_file))
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
