# Phase 1 — Shared UI kit (`core/ui`)

Status: **implemented at source level** (not yet compiled).

## Components
| Piece | File | Purpose |
|---|---|---|
| `UiText` | `UiText.kt` | ViewModel-safe localized message: `Res`, `Plural`, `Dynamic`. Resolve at the screen with `asString()`. |
| `UiEvent`, `UiEventChannel`, `CollectUiEvents` | `UiEvent.kt` | One-off effects (snackbar messages) kept out of `UiState`. Each event reaches one collector. |
| `LoadState`, `LoadStateContent` | `LoadState.kt` | Standard Loading / Success / Failure shape plus a renderer. |
| `FeatureScaffold` | `Common.kt` | Now takes `snackbarHost`; back-arrow description is localized (`R.string.back`). |
| `LoadingState`, `ErrorState`, `ConfirmDialog`, `ProgressCard`, `ResultCard`, `SectionHeader`, `SourcePickerRow` | `Components.kt` | Reusable building blocks. No hard-coded user text. |
| `rememberSaveToDevice` | `SaveToDevice.kt` | "Save to device" through the system file picker (ACTION_CREATE_DOCUMENT). No storage permission. |
| `ShareService.saveManagedTo` | `core/sharing/ShareService.kt` | Copies a managed file to the chosen destination on the injected IO dispatcher. Managed-root check is the same one used by open/share. |
| `@DefaultDispatcher` | `core/di/DispatchersModule.kt` | Added next to `@IoDispatcher`. |

## Adopted so far
- **Converter:** progress → `ProgressCard`; outputs → `ResultCard` with Save to device; open/share failures now show a message instead of failing silently; snackbar host wired.
- **Files:** move-to-trash dialog → `ConfirmDialog`.
- **All screens:** back-arrow content description now localized.

## Not migrated yet (deliberate)
- PDF editor and Scanner keep their typed message enums and custom dialogs. They are large and working; they adopt `UiText` / kit components when next touched.
- Tier B screens are rewritten on the kit in Phases 2–4, not patched.

## New string keys (all six locales)
`common_retry`, `common_loading`, `common_error_generic`, `common_save_to_device`, `common_saved_to_device`, `common_save_failed`, `common_open_failed`, `common_share_failed`, `common_progress_percent`.
`common_error_generic` is not used yet; the Notes/Library screens use it first.

## Verification
`tools/verify_ui_kit.sh` runs `tools/static_checks.py`: import resolution (main, test, androidTest), duplicate declarations, same type name in several packages, `R.string` references, locale key parity, XML validity, hard-coded text in `core/ui`, TODO/FIXME markers. No Android SDK needed.
Not run here: Gradle compile, Compose rendering, the system file picker flow.
