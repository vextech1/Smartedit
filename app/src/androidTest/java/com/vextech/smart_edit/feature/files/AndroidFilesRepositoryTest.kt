package com.vextech.smart_edit.feature.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import com.vextech.smart_edit.core.analytics.AnalyticsService
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.DocumentEntity
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.feature.files.data.AndroidFilesRepository
import com.vextech.smart_edit.feature.files.domain.FileOperationError
import com.vextech.smart_edit.feature.files.domain.FileOperationException
import com.vextech.smart_edit.feature.files.domain.InvalidFileNameException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidFilesRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: SmartEditDatabase
    private lateinit var store: DocumentStore
    private lateinit var repository: AndroidFilesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartEditDatabase::class.java).build()
        store = DocumentStore(context)
        store.root.deleteRecursively()
        repository = AndroidFilesRepository(
            db = database,
            store = store,
            metadataReader = UriMetadataReader(context),
            analytics = AnalyticsService(FirebaseAnalytics.getInstance(context)),
            ioDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        database.close()
        store.root.deleteRecursively()
    }

    @Test
    fun createRenameMoveFavoriteAndTrash_preserveCanonicalState() = runBlocking {
        val folderA = repository.createFolder(null, "Folder A")
        val folderB = repository.createFolder(null, "Folder B")
        val file = managedDocument(folderA.id, "draft.txt", "hello")

        val renamed = repository.rename(file.id, "final.txt")
        assertThat(renamed.name).isEqualTo("final.txt")
        assertThat(File(requireNotNull(Uri.parse(renamed.uri).path)).readText()).isEqualTo("hello")

        val moved = repository.move(file.id, folderB.id)
        assertThat(moved.parentId).isEqualTo(folderB.id)
        assertThat(File(requireNotNull(Uri.parse(moved.uri).path)).parentFile?.name).isEqualTo("Folder B")

        repository.toggleFavorite(file.id)
        assertThat(repository.get(file.id)?.isFavorite).isTrue()

        repository.moveToTrash(folderB.id)
        assertThat(repository.get(folderB.id)).isNull()
        assertThat(repository.get(file.id)).isNull()
        assertThat(database.documentDao().get(folderB.id)?.isTrashed).isTrue()
        assertThat(database.documentDao().get(file.id)?.isTrashed).isTrue()
        assertThat(database.trashDao().snapshot()).hasSize(1)
        assertThat(database.favoriteDao().find("DOCUMENT", file.id)).isNull()
    }



    @Test
    fun importDocuments_copiesContentUriIntoManagedStorage() = runBlocking {
        val external = File(context.cacheDir, "external-import.txt").apply { writeText("external content") }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", external)

        val imported = repository.importDocuments(listOf(contentUri), null)

        assertThat(imported).hasSize(1)
        val item = imported.single()
        val managed = File(requireNotNull(Uri.parse(item.uri).path))
        assertThat(managed.canonicalPath).startsWith(store.documentsDir.canonicalPath)
        assertThat(managed.readText()).isEqualTo("external content")
        assertThat(item.source).isEqualTo("import")
        assertThat(database.documentDao().get(item.id)).isNotNull()
        external.delete()
    }

    @Test
    fun createFolder_rejectsUnsafeAndConflictingNamesWithoutMutatingStorage() = runBlocking {
        val invalid = runCatching { repository.createFolder(null, "../escape") }
        assertThat(invalid.exceptionOrNull()).isInstanceOf(InvalidFileNameException::class.java)
        assertThat(store.documentsDir.listFiles().orEmpty()).isEmpty()

        repository.createFolder(null, "Projects")
        val conflict = runCatching { repository.createFolder(null, "Projects") }
        assertThat(conflict.exceptionOrNull()).isInstanceOf(FileOperationException::class.java)
        assertThat((conflict.exceptionOrNull() as FileOperationException).reason)
            .isEqualTo(FileOperationError.NAME_CONFLICT)
        assertThat(store.documentsDir.listFiles().orEmpty().map { it.name }).containsExactly("Projects")
    }

    @Test
    fun clipboardImport_createsManagedDocumentAndSearchIndex() = runBlocking {
        val item = repository.importClipboard("{\"hello\":\"world\"}", null)

        assertThat(item.mimeType).isEqualTo("application/json")
        assertThat(item.name).endsWith(".json")
        assertThat(File(requireNotNull(Uri.parse(item.uri).path)).readText()).contains("hello")
        assertThat(database.searchDao().search("hello*").map { it.resourceId }).contains(item.id)
    }

    @Test
    fun refreshMetadata_updatesChangedFileAndRemovesMissingManagedRecord() = runBlocking {
        val folder = repository.createFolder(null, "Folder")
        val first = managedDocument(folder.id, "first.txt", "one")
        val second = managedDocument(folder.id, "second.txt", "two")
        repository.toggleFavorite(second.id)

        val firstPath = File(requireNotNull(Uri.parse(first.uri).path))
        firstPath.writeText("one two three")
        firstPath.setLastModified(System.currentTimeMillis() + 1_000)
        File(requireNotNull(Uri.parse(second.uri).path)).delete()

        repository.refreshMetadata()

        assertThat(repository.get(first.id)?.sizeBytes).isEqualTo(firstPath.length())
        assertThat(repository.get(second.id)).isNull()
        assertThat(database.favoriteDao().find("DOCUMENT", second.id)).isNull()
    }

    @Test
    fun move_rejectsDestinationNameConflictWithoutChangingSource() = runBlocking {
        val from = repository.createFolder(null, "From")
        val to = repository.createFolder(null, "To")
        val source = managedDocument(from.id, "same.txt", "source")
        managedDocument(to.id, "same.txt", "destination")
        val original = File(requireNotNull(Uri.parse(source.uri).path))

        val result = runCatching { repository.move(source.id, to.id) }

        assertThat(result.exceptionOrNull()).isInstanceOf(FileOperationException::class.java)
        assertThat((result.exceptionOrNull() as FileOperationException).reason)
            .isEqualTo(FileOperationError.NAME_CONFLICT)
        assertThat(original.readText()).isEqualTo("source")
        assertThat(repository.get(source.id)?.parentId).isEqualTo(from.id)
    }

    @Test
    fun moveFolder_intoOwnDescendant_isRejectedWithoutChangingFilesystem() = runBlocking {
        val parent = repository.createFolder(null, "Parent")
        val child = repository.createFolder(parent.id, "Child")
        val originalPath = File(requireNotNull(Uri.parse(parent.uri).path))

        val result = runCatching { repository.move(parent.id, child.id) }

        assertThat(result.isFailure).isTrue()
        assertThat(originalPath.exists()).isTrue()
        assertThat(repository.get(parent.id)?.parentId).isNull()
        assertThat(repository.get(child.id)?.parentId).isEqualTo(parent.id)
    }

    private suspend fun managedDocument(parentId: String, name: String, content: String): DocumentEntity {
        val parent = requireNotNull(database.documentDao().get(parentId))
        val parentDir = File(requireNotNull(Uri.parse(parent.uri).path))
        val file = File(parentDir, name).apply { writeText(content) }
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            id = UUID.randomUUID().toString(),
            displayName = name,
            uri = file.toURI().toString(),
            mimeType = "text/plain",
            sizeBytes = file.length(),
            createdAt = now,
            modifiedAt = now,
            parentId = parentId,
        )
        database.documentDao().upsert(entity)
        return entity
    }
}
