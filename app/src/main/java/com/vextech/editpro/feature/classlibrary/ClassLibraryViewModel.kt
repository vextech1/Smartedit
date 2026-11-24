package com.vextech.editpro.feature.classlibrary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.R
import com.vextech.editpro.core.auth.AuthFailure
import com.vextech.editpro.core.auth.AuthRepository
import com.vextech.editpro.core.auth.AuthResult
import com.vextech.editpro.core.classlibrary.AccessPolicies
import com.vextech.editpro.core.classlibrary.ClassLibraryFilters
import com.vextech.editpro.core.classlibrary.ClassLibraryLinks
import com.vextech.editpro.core.classlibrary.ClassLibraryParser
import com.vextech.editpro.core.classlibrary.ClassLibraryRepository
import com.vextech.editpro.core.classlibrary.CloudFile
import com.vextech.editpro.core.classlibrary.LibraryAccessRepository
import com.vextech.editpro.core.classlibrary.LibrarySection
import com.vextech.editpro.core.di.IoDispatcher
import com.vextech.editpro.core.sharing.ShareService
import com.vextech.editpro.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadProgress(val fileId: String, val done: Int, val total: Int)

/** Why a download was not allowed, and for which file, so the screen can explain it and retry after the person signs in. */
enum class FetchMode { SAVE, OPEN }

data class LibraryGate(val access: AccessPolicies.Access, val file: CloudFile, val mode: FetchMode = FetchMode.SAVE)

data class ClassLibraryUiState(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val failed: Boolean = false,
    val partial: Boolean = false,
    val sections: List<LibrarySection> = emptyList(),
    val files: List<CloudFile> = emptyList(),
    val sectionId: String? = null,
    val classId: String? = null,
    val subjectId: String? = null,
    val categoryKey: String = ClassLibraryFilters.ALL,
    val query: String = "",
    val selected: CloudFile? = null,
    val gate: LibraryGate? = null,
    val progress: DownloadProgress? = null,
    val message: UiText? = null,
    /** A PDF fetched with Open, waiting for the screen to open it in the PDF tools. */
    val openPdf: String? = null,
) {
    val section: LibrarySection? get() = sections.firstOrNull { it.id == sectionId }
    val scoped: List<CloudFile> get() = ClassLibraryFilters.inScope(files, sectionId, classId, subjectId)
    val chips: List<ClassLibraryFilters.CategoryChip> get() = ClassLibraryFilters.chips(scoped)
    val visible: List<CloudFile> get() = ClassLibraryFilters.sorted(ClassLibraryFilters.filter(scoped, categoryKey, query))
}

@HiltViewModel
class ClassLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ClassLibraryRepository,
    private val access: LibraryAccessRepository,
    private val auth: AuthRepository,
    private val share: ShareService,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _ui = MutableStateFlow(ClassLibraryUiState())
    val ui: StateFlow<ClassLibraryUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load(force: Boolean = false) {
        if (!repository.enabled) {
            _ui.update { it.copy(loading = false, unavailable = true) }
            return
        }
        _ui.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val data = repository.load(force)
                _ui.update { state ->
                    // Keep the person's choice if it still exists; otherwise start at the first class.
                    val section = data.sections.firstOrNull { it.id == state.sectionId } ?: data.sections.firstOrNull()
                    val cls = section?.classes?.firstOrNull { it.id == state.classId } ?: section?.classes?.firstOrNull()
                    val subject = cls?.subjects?.firstOrNull { it.id == state.subjectId }?.id
                    state.copy(
                        loading = false, failed = false, partial = data.partial, sections = data.sections, files = data.files,
                        sectionId = section?.id, classId = cls?.id, subjectId = subject,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun selectSection(id: String) = _ui.update { state ->
        val section = state.sections.firstOrNull { it.id == id } ?: return@update state
        state.copy(sectionId = id, classId = section.classes.firstOrNull()?.id, subjectId = null, categoryKey = ClassLibraryFilters.ALL)
    }

    fun selectClass(id: String) = _ui.update { it.copy(classId = id, subjectId = null, categoryKey = ClassLibraryFilters.ALL) }

    /** A null id means every subject of the class. */
    fun selectSubject(id: String?) = _ui.update { it.copy(subjectId = id, categoryKey = ClassLibraryFilters.ALL) }

    fun selectCategory(key: String) = _ui.update { it.copy(categoryKey = key) }

    fun setQuery(text: String) = _ui.update { it.copy(query = text) }

    fun open(file: CloudFile) = _ui.update { it.copy(selected = file) }

    fun dismissDetails() = _ui.update { if (it.progress != null) it else it.copy(selected = null) }

    fun dismissGate() = _ui.update { it.copy(gate = null) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** Opens the phone's share sheet with the file's title and its page on the site. */
    fun share(file: CloudFile) {
        val text = context.getString(R.string.library_share_text, file.title, ClassLibraryLinks.fileUrl(file.id))
        share.shareText(text, file.title)
    }

    fun clearOpenPdf() = _ui.update { it.copy(openPdf = null) }

    /** Opens the phone's mail app with a report to the site's contact address, for a student's file. */
    fun report(file: CloudFile) {
        share.compose(
            ClassLibraryLinks.CONTACT_EMAIL,
            context.getString(R.string.library_report_subject, file.title).take(150),
            context.getString(R.string.library_report_body, file.title, file.id),
        )
    }

    /** Saves [file] into the phone's Downloads folder. */
    fun download(file: CloudFile) = fetch(file, FetchMode.SAVE)

    /** Fetches [file] and shows it: a PDF opens in this app's PDF tools, anything else in a viewer app. */
    fun openFile(file: CloudFile) = fetch(file, FetchMode.OPEN)

    /** Checks whether this person may download [file], records it, downloads the pieces, then saves or opens the file. */
    private fun fetch(file: CloudFile, mode: FetchMode) {
        if (_ui.value.progress != null) return
        _ui.update { it.copy(progress = DownloadProgress(file.id, 0, file.parts), gate = null) }
        viewModelScope.launch {
            try {
                val user = auth.state.first().user?.takeIf { it.emailVerified }
                val state = if (user != null) access.state(user.uid, file.id) else null
                val decision = AccessPolicies.decide(
                    AccessPolicies.Input(
                        fileId = file.id, free = access.freeFileId(), own = user != null && file.uploader == user.uid, signedIn = user != null,
                        unlocked = state?.unlocked ?: false, liveUploads = state?.liveUploads ?: 0, used = state?.used ?: 0,
                    ),
                )
                if (!AccessPolicies.isAllowed(decision.access)) {
                    _ui.update { it.copy(progress = null, gate = LibraryGate(decision.access, file, mode)) }
                    return@launch
                }
                when (decision.access) {
                    AccessPolicies.Access.FREE -> access.markFreeUsed(file.id)
                    AccessPolicies.Access.CREDIT -> access.unlock(requireNotNull(user).uid, file.id)
                    else -> Unit
                }
                val bytes = repository.download(file) { done, total ->
                    _ui.update { it.copy(progress = DownloadProgress(file.id, done, total)) }
                }
                val saved = withContext(io) {
                    File(File(context.cacheDir, "class-library").apply { mkdirs() }, "${file.id}-${file.name}").also { it.writeBytes(bytes) }
                }
                val mime = ClassLibraryParser.mimeOf(file.ext)
                if (mode == FetchMode.OPEN) {
                    if (file.ext.equals("pdf", ignoreCase = true)) {
                        _ui.update { it.copy(progress = null, selected = null, openPdf = share.uriFor(saved)) }
                    } else {
                        _ui.update { it.copy(progress = null, selected = null) }
                        share.open(saved, mime)
                    }
                } else if (withContext(io) { saveToDownloads(saved, file.name, mime) }) {
                    _ui.update { it.copy(progress = null, selected = null, message = UiText.res(R.string.library_saved)) }
                } else {
                    _ui.update { it.copy(progress = null, selected = null) }
                    share.open(saved, mime)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(progress = null, message = UiText.res(R.string.library_download_failed)) }
            }
        }
    }

    /** The gate's "sign in" button: after a successful sign-in the download is tried again for the same file. */
    fun signInAndRetry(activityContext: Context, file: CloudFile, mode: FetchMode = FetchMode.SAVE) {
        viewModelScope.launch {
            when (val result = auth.signIn(activityContext)) {
                is AuthResult.Success -> fetch(file, mode)
                is AuthResult.Failure -> {
                    if (result.reason != AuthFailure.CANCELLED) {
                        val text = if (result.reason == AuthFailure.DISABLED) R.string.account_unavailable else R.string.account_signin_failed
                        _ui.update { it.copy(message = UiText.res(text)) }
                    }
                }
            }
        }
    }

    /** Copies [from] into the Downloads folder (Android 10 and later); returns false where that is not possible so the caller can fall back. */
    private fun saveToDownloads(from: File, name: String, mime: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(uri)!!.use { out -> from.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        }.getOrDefault(false)
    }
}
