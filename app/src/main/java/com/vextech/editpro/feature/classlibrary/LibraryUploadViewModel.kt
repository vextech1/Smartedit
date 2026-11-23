package com.vextech.editpro.feature.classlibrary

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vextech.editpro.R
import com.vextech.editpro.core.auth.AdminRepository
import com.vextech.editpro.core.auth.AdminRole
import com.vextech.editpro.core.auth.AuthFailure
import com.vextech.editpro.core.auth.AuthRepository
import com.vextech.editpro.core.auth.AuthResult
import com.vextech.editpro.core.auth.AuthState
import com.vextech.editpro.core.classlibrary.AdminLibraryRepository
import com.vextech.editpro.core.classlibrary.ClassLibraryRepository
import com.vextech.editpro.core.classlibrary.LibraryCategory
import com.vextech.editpro.core.classlibrary.LibrarySection
import com.vextech.editpro.core.classlibrary.Notice
import com.vextech.editpro.core.classlibrary.UploadMeta
import com.vextech.editpro.core.classlibrary.UploadRefused
import com.vextech.editpro.core.classlibrary.UserFile
import com.vextech.editpro.core.classlibrary.UserLibraryRepository
import com.vextech.editpro.core.files.UriMetadataReader
import com.vextech.editpro.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedUpload(val uri: Uri, val name: String, val size: Long)

data class UploadUiState(
    val admin: Boolean = false,
    val auth: AuthState = AuthState(),
    val role: AdminRole = AdminRole.NONE,
    val checking: Boolean = true,
    val sections: List<LibrarySection> = emptyList(),
    val picked: PickedUpload? = null,
    val title: String = "",
    val sectionId: String? = null,
    val classId: String? = null,
    val subjectId: String? = null,
    val category: LibraryCategory = LibraryCategory.NOTES,
    val label: String = "",
    val progress: Pair<Int, Int>? = null,
    val myFiles: List<UserFile> = emptyList(),
    val notices: List<Notice> = emptyList(),
    val message: UiText? = null,
) {
    val canUpload: Boolean get() = picked != null && sectionId != null && classId != null && progress == null &&
        (category != LibraryCategory.OTHER || label.isNotBlank())
}

/** The upload form for students (`admin = false`) and for the Library's admins (`admin = true`), plus a student's own uploads and notices. */
@HiltViewModel
class LibraryUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: ClassLibraryRepository,
    private val user: UserLibraryRepository,
    private val adminLibrary: AdminLibraryRepository,
    private val adminRoles: AdminRepository,
    private val auth: AuthRepository,
    private val metadata: UriMetadataReader,
) : ViewModel() {
    private val _ui = MutableStateFlow(UploadUiState())
    val ui: StateFlow<UploadUiState> = _ui.asStateFlow()
    private var started = false

    fun start(admin: Boolean) {
        if (started) return
        started = true
        _ui.update { it.copy(admin = admin) }
        viewModelScope.launch {
            auth.state.collect { state ->
                _ui.update { it.copy(auth = state, checking = true) }
                refresh(state)
            }
        }
    }

    private suspend fun refresh(state: AuthState) {
        val me = state.user
        val role = runCatching { adminRoles.role(me) }.getOrDefault(AdminRole.NONE)
        val sections = runCatching { library.load(force = true).sections }.getOrDefault(emptyList())
        _ui.update { s ->
            val section = sections.firstOrNull { it.id == s.sectionId } ?: sections.firstOrNull()
            val cls = section?.classes?.firstOrNull { it.id == s.classId } ?: section?.classes?.firstOrNull()
            s.copy(role = role, sections = sections, sectionId = section?.id, classId = cls?.id, checking = false)
        }
        if (me != null && !_ui.value.admin) loadMine(me.uid)
    }

    private suspend fun loadMine(uid: String) {
        val files = runCatching { user.myFiles(uid) }.getOrDefault(emptyList())
        val notices = runCatching { user.notices(uid) }.getOrDefault(emptyList())
        _ui.update { it.copy(myFiles = files, notices = notices) }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            when (val result = auth.signIn(activityContext)) {
                is AuthResult.Success -> Unit
                is AuthResult.Failure -> if (result.reason != AuthFailure.CANCELLED) {
                    say(if (result.reason == AuthFailure.DISABLED) R.string.account_unavailable else R.string.account_signin_failed)
                }
            }
        }
    }

    fun pick(uri: Uri) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                metadata.persistReadPermission(uri)
                runCatching { metadata.read(uri) }.getOrNull()
            }
            if (meta == null) say(R.string.upload_failed) else _ui.update { it.copy(picked = PickedUpload(uri, meta.name, meta.size)) }
        }
    }

    fun setTitle(v: String) = _ui.update { it.copy(title = v.take(120)) }
    fun setLabel(v: String) = _ui.update { it.copy(label = v.take(40)) }
    fun setCategory(c: LibraryCategory) = _ui.update { it.copy(category = c) }
    fun setSubject(id: String?) = _ui.update { it.copy(subjectId = id) }
    fun setSection(id: String) = _ui.update { s ->
        val section = s.sections.firstOrNull { it.id == id } ?: return@update s
        s.copy(sectionId = id, classId = section.classes.firstOrNull()?.id, subjectId = null)
    }
    fun setClass(id: String) = _ui.update { it.copy(classId = id, subjectId = null) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun upload() {
        val s = _ui.value
        val picked = s.picked ?: return
        val sectionId = s.sectionId ?: return
        val classId = s.classId ?: return
        if (!s.canUpload) return
        val meta = UploadMeta(picked.name, s.title.ifBlank { null }, sectionId, classId, s.subjectId, s.category, s.label.ifBlank { null })
        _ui.update { it.copy(progress = 0 to 1) }
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(picked.uri)) { "Cannot open file" }.use { it.readBytes() }
                }
                val onProgress = { done: Int, total: Int -> _ui.update { it.copy(progress = done to total) } }
                if (s.admin) {
                    adminLibrary.upload(bytes, meta, onProgress)
                } else {
                    user.upload(requireNotNull(s.auth.user).uid, bytes, meta, onProgress)
                }
                _ui.update { it.copy(progress = null, picked = null, title = "", label = "", message = UiText.res(if (s.admin) R.string.upload_added_admin else R.string.upload_done)) }
                s.auth.user?.takeIf { !s.admin }?.let { loadMine(it.uid) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadRefused) {
                _ui.update { it.copy(progress = null, message = UiText.res(R.string.upload_refused, e.reason)) }
            } catch (e: Exception) {
                val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                _ui.update { it.copy(progress = null, message = UiText.res(if (denied) R.string.upload_denied else R.string.upload_failed)) }
            }
        }
    }

    fun remove(file: UserFile) {
        viewModelScope.launch {
            try {
                user.remove(file)
                _ui.value.auth.user?.let { loadMine(it.uid) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                say(R.string.upload_failed)
            }
        }
    }

    fun dismissNotice(notice: Notice) {
        val uid = _ui.value.auth.user?.uid ?: return
        viewModelScope.launch {
            runCatching { user.dismissNotice(uid, notice.id) }
            loadMine(uid)
        }
    }

    private fun say(res: Int) = _ui.update { it.copy(message = UiText.res(res)) }
}
