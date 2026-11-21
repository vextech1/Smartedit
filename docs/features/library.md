# Phase 2 — Library cluster (Notes, Favorites, Search, Activity, Trash)

Status: **logic compiled and tested; Android layer written but not compiled** (no Android SDK available here).

## Architecture
`SmartEditRepository` (285 lines, everything) is split into focused repositories in `core/repository`:

| Repository | Owns |
|---|---|
| `SearchIndexer` | Only writer of the FTS index (always replaces, never duplicates) |
| `HistoryRepository` | Activity entries, record, clear |
| `FavoritesRepository` | Favorites resolved against live files/notes; keeps a note's own flag in step |
| `NotesRepository` | Notes CRUD, pin, favorite, trash, restore, purge |
| `TrashRepository` | Unified trash: file restore/delete (moved verbatim) plus notes; purge of both |
| `SearchRepository` | Prefix search, filter, ranking, snippets |
| `DocumentIndexRepository` | `registerFile` for output files |
| `DashboardRepository` | Summary flow |
| `SmartEditRepository` | Slim registrar (`registerFile`, `recordHistory`, a few flows) for Scanner/PDF/Converter/Compressor/Image editor/Backup |

Domain logic is pure Kotlin in `core/library` (no Android imports): `NotePolicies`, `TrashPolicies`, `FavoritePolicies`, `HistoryPolicies`, `HistoryExportFormatter`, `SearchPolicies`, plus models. Repositories return domain models; screens never see Room entities.

## Behavior changes
- Note titles are stored as typed. A blank title shows a localized "Untitled note" (it was stored as English text before).
- Editor autosaves 800 ms after typing stops. A save lock prevents autosave and close from creating two notes. A new note records one *created* entry; an existing note records one *edited* entry when closed after changes. A completely empty new note is never stored.
- Notes now expire from the trash after 30 days (they never did). The cleanup worker purges files and notes.
- One Trash screen for files and notes (Notes → Trash opens it). The separate Notes-trash screen and route are removed.
- Trash rows show days left (plural-aware in all six languages).
- Moving a note to trash and restoring it are recorded in Activity; trashing offers Undo.
- Favorites show live names and drop items that were trashed or deleted.
- Search: debounce, All/Files/Notes filter, title matches ranked first, match highlighting, snippets centered on the match.
- Activity: grouped by day (Today/Yesterday/date), filter chips, localized action labels, tap to open, clear with confirmation, export as PDF/CSV/JSON/TXT (user's default format listed first). Export covers the whole list; PDF now paginates and wraps lines instead of cutting them. CSV neutralizes spreadsheet formulas.
- Fixed in passing: the old History screen imported `Modifier` from the wrong package (compile error).

## Tests (run, passing)
`tools/verify_library_feature.sh` compiles `core/library` with the real Kotlin compiler and runs 80 checks: tag parsing/limits/dedupe, note sort and filter, trash retention math, favorite resolution, day grouping across time zones, export escaping (CSV/JSON/formula), line wrapping, search snippets and highlighting. Mutating the code makes the checks fail (verified for rounding and word-start rules).
`tools/static_checks.py` also checks plurals and placeholder parity across the six languages and unescaped quotes.

## Not verified
- Gradle/Room/Hilt compilation of repositories and screens (the Kotlin compiler found no syntax errors; type resolution needs the Android SDK).
- Room runtime behavior (queries, transactions) and Compose rendering.
- Two new DAO queries (`NoteDao.observe`, `NoteDao.deletedBefore`) rely on Room's compile-time SQL check.

## Left for later phases
- Tab hosting (Files segments Files/Notes/Favorites, Activity tab) and search icon placement: Phase 4. Screens are standalone composables and slot into tabs unchanged.
- History `description` stays English text from writers; screens render localized labels from the action plus the item name and only fall back to the text.
