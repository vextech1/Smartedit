# Image editor (Edit Image)

Status: **implemented at source level; not yet tried on a device.** Built in phases from the site's photo studio; see `docs/IMAGE_BUILD_PLAN.md` for the plan, lanes and status.

Everything image-related is reached from inside Edit Image. There are no separate screens and no extra Tools-tab tiles. The dock holds the common tools (Filters, Adjust, Crop, Rotate, Flip, Remove background, More); **More** lists every tool grouped Edit, Mark and Output, from the registry in `feature/imageeditor/ImageTools.kt`.

## Tools

| Group | Tool | Notes |
|---|---|---|
| Edit | Adjust | Brightness, contrast, saturation, temperature, tint. |
| Edit | Crop | Free, Original, 1:1, 4:5, 3:4, 4:3, 3:2, 16:9, 9:16, and **Auto** (finds the page or object on a plain background). |
| Edit | Rotate, Flip | Turn in 90 degrees; mirror either way. |
| Edit | Resize | 31 sizes in four groups, custom px/mm/cm/in/%, dpi, crop or whole photo, file-size limit for forms. |
| Edit | Round | Circle, rounded, soft; picture shape, roundness, zoom and drag. Transparent corners. |
| Edit | Remove background | Plain backgrounds. |
| Mark | Draw & Text | Pen, arrow, box, oval, text. |
| Mark | Blur or Hide | Black box, pixelate, blur. Pixels under a box are replaced in the saved picture. |
| Mark | Watermark | Text or logo, nine spots or repeat, drag and pinch. |
| Mark | Frames | Wall frame with mat and wall; generic phone frame. |
| Mark | Colour picker | Tap to read HEX, RGB, HSL; main colours; keep and copy. Changes nothing. |
| Output | Collage | This photo plus up to 11 more; replaces the photo in the editor (edit history starts again). |
| Output | Photo sheet | One-page PDF with copies to print (4x6, 5x7, A4, Letter), cutting lines. |
| Output | Export | JPG, PNG, WebP, quality, longest side, live size estimate. Opened by the Save button. |

## How it works

- Edits are `EditOp`s on an undo/redo stack (`core/library/ImageEditModel.kt`), replayed on a 1600 px preview and, when saving, on a copy of up to 4096 px. Marks, hide boxes and watermark positions are stored as fractions of the picture so they land in the same place at any size.
- Pure logic lives in `core/image/*` with unit tests in `app/src/test/.../core/image/`; Android drawing lives in `core/processing/` (`ImageProcessor`, `MarkPainter`, `FramePainter`).
- Tools with settings open as a `ToolSheet` (see `ui-kit.md`). The tick applies; the handle or Back closes without applying. The image editor does not yet use the side-panel layout on wide screens.
- A result that has see-through pixels (Remove background, Round with cut corners, a phone frame with no background) offers PNG and WebP only.
- Every save lands on the saved card (Open, Share, Save to device, Make PDF, Read text). The Photo sheet's PDF hides Make PDF and Read text.
- The scanner's page menu has **Edit more in Edit Image**, which opens a copy of the selected page in the editor.

## Accessibility

- Sheets are scrollable and use standard Material controls, so large text and TalkBack work as elsewhere in the app.
- The touch layers over the photo (Draw, Hide, Watermark, Colour picker, Resize and Round drag) are labelled for TalkBack with what a drag or tap does. Drawing, hiding and placing remain touch gestures; there is no keyboard or switch alternative yet.

## Known limits

- Resize shows one generic "check this size" note instead of the site's per-country notes.
- Photo sheet crops the photo to the chosen size; it does not take the size from the Resize sheet.
- Collage format and quality are chosen at Export, not inside Collage.
- Nothing here has been run on a device. Check on a real phone: memory on very large photos (Collage, Frames), gesture feel (pinch, drag), and the look of frames and watermarks.
