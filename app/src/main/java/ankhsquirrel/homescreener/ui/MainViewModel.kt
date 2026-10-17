package ankhsquirrel.homescreener.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ankhsquirrel.homescreener.R
import ankhsquirrel.homescreener.data.DocumentAccess
import ankhsquirrel.homescreener.data.IconStore
import ankhsquirrel.homescreener.data.ShortcutEntry
import ankhsquirrel.homescreener.data.ShortcutRepository
import ankhsquirrel.homescreener.data.defaultLabel
import ankhsquirrel.homescreener.shortcut.IconFactory
import ankhsquirrel.homescreener.shortcut.Pinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class FileSnapshot(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val icon: Bitmap?,
)

data class EntryItem(
    val entry: ShortcutEntry,
    val icon: Bitmap?,
    val pinned: Boolean,
    val available: Boolean,
)

data class EditorState(
    /** The saved entry being edited, or null while creating a new shortcut. */
    val existing: ShortcutEntry?,
    val file: FileSnapshot,
    val label: String,
    val available: Boolean = true,
) {
    val canSave: Boolean get() = label.isNotBlank()
    val isDirty: Boolean
        get() = existing == null || label.trim() != existing.label || file.uri.toString() != existing.uri
}

data class UiState(
    val loaded: Boolean = false,
    val items: List<EntryItem> = emptyList(),
    val pinSupported: Boolean = true,
    val editor: EditorState? = null,
    @StringRes val message: Int? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Application get() = getApplication()
    private val repository = ShortcutRepository.get(app)
    private val iconStore = IconStore(app)

    /** Serializes operations so grant cleanup never races a pick, save or delete. */
    private val ops = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() = launchOp { reload() }

    fun onNewFilePicked(uri: Uri) = launchOp {
        if (!DocumentAccess.takeRead(context, uri)) return@launchOp showMessage(R.string.error_no_persistable_access)
        val file = snapshot(uri)
        _state.update {
            it.copy(editor = EditorState(existing = null, file = file, label = defaultLabel(file.fileName)))
        }
    }

    fun openEntry(id: String) = launchOp {
        val entry = repository.get(id) ?: return@launchOp
        val uri = entry.uri.toUri()
        val (icon, available) = withContext(Dispatchers.IO) {
            iconStore.load(entry.id) to DocumentAccess.isReachable(context, uri)
        }
        _state.update {
            it.copy(
                editor = EditorState(
                    existing = entry,
                    file = FileSnapshot(uri, entry.fileName, entry.mimeType, icon),
                    label = entry.label,
                    available = available,
                )
            )
        }
    }

    fun onLabelChange(label: String) {
        _state.update { it.copy(editor = it.editor?.copy(label = label)) }
    }

    fun onReplacementPicked(uri: Uri) = launchOp {
        val current = _state.value.editor ?: return@launchOp
        if (uri == current.file.uri && current.available) return@launchOp
        if (!DocumentAccess.takeRead(context, uri)) return@launchOp showMessage(R.string.error_no_persistable_access)
        val file = snapshot(uri)
        _state.update { state ->
            val editor = state.editor ?: return@update state
            // Follow the new file name unless the user typed their own label.
            val customLabel = editor.label.trim() != defaultLabel(editor.file.fileName)
            state.copy(
                editor = editor.copy(
                    file = file,
                    available = true,
                    label = if (customLabel) editor.label else defaultLabel(file.fileName),
                )
            )
        }
        // The previously staged file's grant (if unused) is released by the next reload().
    }

    fun pin() = launchOp {
        val editor = _state.value.editor?.takeIf { it.canSave } ?: return@launchOp
        val entry = commit(editor)
        val requested = Pinner.requestPin(context, entry, editor.file.icon)
        showMessage(if (requested) R.string.message_pin_requested else R.string.error_pin_unsupported)
        closeEditorLocked()
    }

    fun save() = launchOp {
        val editor = _state.value.editor?.takeIf { it.canSave && it.isDirty } ?: return@launchOp
        commit(editor)
        showMessage(R.string.message_saved)
        closeEditorLocked()
    }

    fun delete() = launchOp {
        val existing = _state.value.editor?.existing
        if (existing != null) {
            repository.remove(existing.id)
            Pinner.disable(context, existing.id, context.getString(R.string.shortcut_disabled_message))
            withContext(Dispatchers.IO) { iconStore.delete(existing.id) }
            showMessage(R.string.message_deleted)
        }
        closeEditorLocked()
    }

    fun closeEditor() = launchOp { closeEditorLocked() }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun closeEditorLocked() {
        _state.update { it.copy(editor = null) }
        reload()
    }

    private suspend fun commit(editor: EditorState): ShortcutEntry {
        val previous = editor.existing
        val file = editor.file
        val entry = ShortcutEntry(
            id = previous?.id ?: UUID.randomUUID().toString(),
            uri = file.uri.toString(),
            mimeType = file.mimeType,
            fileName = file.fileName,
            label = editor.label.trim(),
            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
        )
        val fileChanged = previous?.uri != entry.uri
        if (fileChanged && file.icon != null) {
            withContext(Dispatchers.IO) { iconStore.save(entry.id, file.icon) }
        }
        repository.upsert(entry)
        if (previous != null) Pinner.update(context, entry, file.icon.takeIf { fileChanged })
        return entry
    }

    private suspend fun reload() {
        val entries = repository.all()
        releaseUnusedGrants(entries)
        val pinned = Pinner.pinnedIds(context)
        val items = withContext(Dispatchers.IO) {
            entries.sortedByDescending { it.createdAt }.map { entry ->
                EntryItem(
                    entry = entry,
                    icon = iconStore.load(entry.id),
                    pinned = entry.id in pinned,
                    available = DocumentAccess.isReachable(context, entry.uri.toUri()),
                )
            }
        }
        _state.update { it.copy(loaded = true, items = items, pinSupported = Pinner.isSupported(context)) }
    }

    /** Grants are capped per app; drop any not backing a saved entry or the open editor. */
    private fun releaseUnusedGrants(entries: List<ShortcutEntry>) {
        val keep = entries.mapTo(HashSet()) { it.uri }
        _state.value.editor?.let { keep += it.file.uri.toString() }
        DocumentAccess.persistedUris(context)
            .filter { it.toString() !in keep }
            .forEach { DocumentAccess.release(context, it) }
    }

    private suspend fun snapshot(uri: Uri): FileSnapshot = withContext(Dispatchers.IO) {
        val info = DocumentAccess.describe(context, uri)
        FileSnapshot(uri, info.displayName, info.mimeType, IconFactory.create(context, uri, info.mimeType, info.displayName))
    }

    private fun showMessage(@StringRes message: Int) {
        _state.update { it.copy(message = message) }
    }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch { ops.withLock { block() } }
    }
}
