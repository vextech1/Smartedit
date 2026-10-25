# Shell: theme, bottom navigation, Home, Files hub, Profile

Status: **written at source level; logic verifiers pass; Android layer not compiled.**

## Structure
`SmartEditNavigation` (app level) -> `StartRoute` decides -> `OnboardingRoute` (first launch only) or `ShellRoute` -> `MainShell`.

`MainShell` has four tabs, each with its own back stack: **Home**, **Files**, **Activity**, **Profile**.
- Tools open on top of the current tab and hide the bottom bar.
- Back pops the current stack; at a tab root it returns to Home; at Home it leaves the app.
- Tapping the selected tab pops that tab to its root.
- Window insets: the outer Scaffold pads only for the bar; inner screens keep their own top bars.

## Tabs
| Tab | Content |
|---|---|
| Home | Title "Smart Edit" and a 2x2 grid of cards: Scanner, Converter, Compressor, Settings. Cards come from `homeTools`; adding an entry adds a card. Equal-height cards (two description lines reserved). |
| Files | Segmented control **Files / Notes / Favorites**. Each segment is a full screen rendering the control as its header. Search icon on every segment; Trash in the Files menu and the Notes toolbar. New/open note goes to the editor. |
| Activity | The History screen (day groups, filters, export, clear). No back arrow at tab root. |
| Profile | Account card (stub: disabled "Sign in - Coming soon"), library stats and storage, Backup, AI Assist, About dialog. |

## Theme
Fixed brand palette (deep teal primary, pale cool-grey background, white cards, light-teal icon tiles), light and dark. Android dynamic wallpaper colors are no longer used.

## Changes to existing screens
- `FilesScreen`, `NotesScreen`, `FavoritesScreen`: back is optional, new `header` slot, optional search action. `FilesScreen` also gets an optional Trash menu item. Behavior without the new parameters is unchanged.
- `HistoryScreen`: back is optional; title is "Activity".
- Old Dashboard (stats, 15-action grid, recent files) removed. `DashboardRepository` now feeds Profile.
- Routes removed: Dashboard, Files, Notes, Favorites, History. Added: Shell, Home, FilesTab, Activity, Profile.

## Tool entry points
PDF editor, OCR and Image editor are reached from the Files tab file menu (see tools.md). Home cards open Scanner, Converter, Compressor and Settings.

## Not verified
Compilation, Compose rendering, Navigation 3 tab switching behavior (switching tabs recreates the previous tab's screens; their view models are not kept), edge-to-edge inset handling on a device.

## Onboarding
Deep teal screen with a soft radial glow, drifting document icons (PDF, DOC, PPT, XLS, TXT, ZIP, image, folder), three swipeable pages, page dots and a single "Get started" button. It shows only on the first launch; `StartViewModel` routes later launches straight to the shell.
- Icon layout lives in `OnboardingScatter.kt` (pure Kotlin) and is tested: nothing inside the text zone, larger and brighter near the glow, dimmer toward the edges, every file type present. `tools/verify_onboarding_feature.sh` runs it.
- Pages: 1 brand and tagline, 2 PDF tools, 3 edit and organize. All text is localized in six languages.
- Floating animation is skipped when the system "remove animations" setting is on. Decorative icons are hidden from screen readers; the dots announce "Page X of Y".
- Status and navigation bar icons are forced light while this screen shows (the background is dark in both themes) and restored afterwards.
- Audio/video icons from the first mockup are replaced with document types. The tagline is "Scan, edit and convert documents on the go."
