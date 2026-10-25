# Files / Document Management

Status: **implemented**

This feature is the canonical browser for Smart Edit-managed files. External documents are copied into app-managed storage before Smart Edit mutates them.

## Behavior

- Browse root and nested folders with breadcrumbs and system-back navigation.
- Switch between list and adaptive grid layouts.
- Sort by name, modified time, size, or type while always keeping folders ahead of files.
- Import one or more documents through Android's Storage Access Framework.
- Import clipboard text as `.txt` or JSON-shaped text as `.json`.
- Create folders using strict single-path-segment validation.
- Open and share managed files through `FileProvider` read-only content URIs.
- Rename files or folders, updating descendant metadata for directory renames.
- Move files or folders, preventing a folder from being moved into itself or a descendant.
- Add/remove file favorites. Folders are intentionally not favoritable.
- Show file/folder metadata.
- Move files or whole directory trees to Trash, retaining canonical database records as trashed records for later restoration.
- Refresh metadata and remove records whose managed files have disappeared.
- Honor the user's delete-confirmation preference.
- Present all Files UI and validation/success messages through Android string resources in English, French, Spanish, German, Arabic, and Hindi.

## Storage invariants

1. Smart Edit only renames, moves, trashes, opens, or shares files that live under its managed app storage root.
2. File-system moves happen off the main thread.
3. Database changes for rename/move/trash are transactional.
4. A database failure after a physical move attempts a filesystem rollback; rollback failure is attached as a suppressed error instead of silently discarded.
5. A directory move/rename rewrites descendant URIs as one database transaction.
6. Name collisions never overwrite an existing file or folder.
7. Trashed documents are excluded from active file queries, search rows, and favorites.

## Tests

Fast production-policy verification (does not require Android SDK):

```bash
tools/verify_files_feature.sh
```

JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.vextech.smart_edit.feature.files.*'
```

Android repository integration tests:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vextech.smart_edit.feature.files.AndroidFilesRepositoryTest
```

The integration suite covers content-URI import, create/rename/move/favorite/trash consistency, unsafe/conflicting names, clipboard indexing, metadata refresh, destination conflicts, and folder-cycle rejection.
