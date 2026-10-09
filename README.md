# Paperwork for Android

A local PDF form-filling app written in Kotlin and Jetpack Compose. Android 8.0 (API 26) or newer.

[Download releases](https://github.com/nivekmai/paperwork/releases) · [Build status](https://github.com/nivekmai/paperwork/actions/workflows/android.yml) · [Changelog](CHANGELOG.md)

## Using the app

1. Install the APK on your Android phone. Android may ask you to allow installs from the app opening the APK.
2. Choose **Open a PDF**, or use **Open with / Share → Paperwork** from your email or file app.
3. Use **Text**, **Shape**, **Sign**, or **Doodle** to add content. Text opens a keyboard directly on the PDF. For an existing text box, tap once to select it, then tap again to edit it in place. Tap the checkmark to finish. Typing saves automatically and each typing session is one undo step.
4. Drag a selected addition to move it. Its large lower-right handle scales both dimensions while keeping the current proportions; the four edge handles change just width or height. Circles and squares can become ovals and rectangles. Signature corner resizing preserves proportions too. Pinch to zoom; drag empty space to pan.
5. The pencil icon opens **Edit style**. Text controls include font, point size, line spacing, text/background colors (including transparency). Resize boxes using their handles on the page. Fonts: sans, serif, monospace, and script. Shapes have stroke width, solid/dashed/dotted stroke style, stroke color, and fill color for closed shapes. Fill stays inside the shape. Squares and rectangles have a **Corner radius** slider, from sharp corners to fully rounded ends; the preview updates as you adjust it. Swatches fill their entire rounded outline, and transparency uses a checkerboard. Custom colors have a saturation/brightness square, hue slider, opacity slider and alpha percentage, and hex input (RRGGBB or RRGGBBAA). The custom picker grows to fit the available screen height, with scrolling retained for large fonts, small screens, and the keyboard. Style dialogs no longer include box dimensions.
6. **Sign → Draw a new signature** opens the portrait naming form. Choose **Draw in landscape** to see an animated rotate-phone cue before a full-screen finger drawing pad, with Undo, Clear, and Accept in a vertical strip on the right. Accept returns the drawing to the portrait preview; **Save signature** saves it to the library. Back cancels a drawing, and cancelling a redraw keeps the previously accepted drawing. Name and save signatures or initials, insert them into documents, or delete them from the library. Deleting a library signature leaves existing placed copies intact.
7. **Shape → Line** inserts a line. Drag the head (end handle) freely; snapping engages only within ±2° of a multiple of 45°. The tail (start handle) moves freely. Snapped lines shorten at page edges to preserve their angle. The pencil icon lets you set arrowheads independently at the start and end: None, Open, Triangle, Circle, Diamond, or Bar. Stroke width, color, and solid/dashed/dotted styles also apply. Circle resizing replaces the removed Oval picker option; existing ovals still load.
8. **Doodle** enters direct drawing mode. Draw on the PDF with one finger; use two fingers to zoom/pan. A tap makes a dot. Each completed stroke saves immediately and joins the current doodle. Undo/redo works stroke by stroke. Tap the checkmark or Doodle again to finish, then move/resize/style the doodle like other additions. Starting a new drawing session creates a separate doodle.
9. **Export** saves or shares a finished PDF. Leave Pages blank for all pages, or enter a range such as `1, 3-5, 2` to extract and reorder pages. Omit pages to exclude them. The original PDF remains untouched.

New text, shapes, and signatures appear at the center of the current PDF viewport, accounting for pan and zoom and keeping additions on the page. Returning from the signature library preserves the viewport. Newly inserted signatures are capped at 50% of the visible editor width at the current zoom, leaving room for resize handles. Tall signatures also fit within half the viewport height, preserving their proportions. This cap applies only at insertion; you can enlarge them afterward. Style previews stay pinned above the scrolling controls.

Quick color and stroke controls are available for every tool. The rounded swatch shows the current color; the stacked-line icon opens width presets (font sizes for selected text). The color and stroke buttons share the undo/redo/edit row. Trays open above them. While typing, preset color and font-size changes preserve the text input, caret, and keyboard; custom hex editing returns to that same text session. Choices update the selected item and become defaults for new items; signature and doodle insertion use these defaults too.

Dragging additions aligns their edges and centers with other additions on the same page, plus page edges and centers, within **2 screen pixels** at the current zoom. Alignment guides appear during a snap. Both alignment and 45° line detents request a brief haptic bump on entry, respecting Android haptic settings. Moving off a detent releases it. The top nudge bar appears only for a selected item outside text editing/keyboard use. It offers **1 px** and **5 px** up/down/left/right movement in screen pixels at the current zoom; long-press repeats every 150 ms after the normal long-press delay. Nudges bypass snapping, stay on the page, and support undo.

The editing toolbar has centered buttons and an opaque background and icons for undo, redo, style, duplicate, and delete. Long-press an icon for its label. Back navigation uses a left chevron. The shape picker displays the actual shapes.

The Text/Shape/Sign/Doodle row hides while typing, and the bottom gesture-hint line is removed. Keyboard and toolbar resizing preserve the actual PDF scale. Doodle presets omit transparent, and starting a doodle with a transparent default restores visible ink. Shape-picker samples use thicker strokes. Touch slop distinguishes taps from the start of a drag. Once dragging starts, every movement updates the preview smoothly. Release protection remembers a position held for at least 100 ms. If the finger then wobbles by at most 8 screen pixels and lifts within 100 ms, placement returns to that settled position. All live MOVE events still render immediately; longer or larger adjustments remain intact. Finger-up coordinates are ignored.

Drafts save automatically after each completed edit and during inline typing. Install this APK over an earlier version to preserve drafts and signatures; do not uninstall first. Undo/redo supports the last 50 changes during the current editing session. Reopen drafts from the home screen. Draft deletion only removes the app's local copy.

## Privacy and storage

- No account, server, analytics, or INTERNET permission.
- Imported PDFs, draft data, and signatures live in app-private internal storage.
- Android cloud backups and device transfer are explicitly excluded. Export copies you need to keep. Uninstalling or clearing app data removes drafts and signatures.
- System pickers can read or save to a provider you choose, including a cloud-backed provider. Sharing sends the exported document to the app you select.
- Exports are cached in the app cache for sharing. Android may clear that cache.

## Current limitations

- This is an overlay editor for completing forms: it does not modify existing PDF text, perform OCR, or provide native AcroForm field/tab navigation. Place text and checks over the appropriate areas, including on scanned PDFs.
- Original page content is retained. New text, shapes, and ink are embedded as a transparent raster overlay, at up to 3 pixels per PDF point with a 3000-pixel maximum page dimension. Added text is therefore not selectable/searchable in exports. Draft additions remain editable.
- Export flattens existing PDF form fields and removes bookmarks. Links to excluded pages may no longer work.
- Password-protected PDFs are not supported. Drawn signatures are visual marks, not certificate-backed digital signatures; exporting an already digitally signed document can invalidate its existing signature.
- No PDF merging or page rotation controls in this version. Existing rotated pages are supported; export ranges support extraction, exclusion, and reordering.
- Single-page preview is capped at 1800 pixels on the longest side. Very large or complex PDFs still depend on device memory and PDF compatibility.
- English UI; canvas editing is touch-oriented. Full screen-reader canvas editing is not implemented; dimensions are adjusted with canvas handles.

## Build

GitHub Actions builds and validates every pull request and push to `main`. A version bump automatically prepares a signed APK and changelog in a draft release. See [RELEASING.md](RELEASING.md) for the update and publishing workflow.

Open this folder in Android Studio, or use JDK 21 and Android SDK 37:

```sh
# Set JAVA_HOME to your JDK, and sdk.dir in local.properties to your Android SDK.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. The packaged APK is debug-signed for sideloading, not a Play Store release. The Gradle wrapper and all dependency versions are pinned. The project uses AGP's built-in Kotlin support with the Compose compiler plugin.

## Layout

- `MainActivity.kt`: Compose screens, import/export pickers, signature library, style dialogs.
- `QuickTools.kt` / `AlignmentGeometry.kt`: quick style trays, nudge bar, placement alignment, and snap feedback state.
- `ColorPicker.kt`: HSV selection, checkerboard transparency, alpha percentage and hex controls.
- `RotatePhoneCue.kt` / `SignatureDrawingActivity.kt`: animated portrait cue and landscape signature pad.
- `EditorModel.kt`: editor state, bounded undo/redo, background PDF operations.
- `PdfFiles.kt`: private file storage, atomic draft writes, PDF rendering and export.
- `PageCanvas.kt`: zoom/pan, selection, movement, resize handles, signature pad.
- `PageEditor.kt`: native inline text input, keyboard positioning, live draft updates.
- `ResizeGeometry.kt`: proportional corner scaling, independent edge resizing, and line endpoint dragging.
- `DrawingGeometry.kt`: line endpoint normalization, line hit testing, and multi-stroke doodle bounds.
- `EditorIcons.kt`: native vector icons and accessible toolbar buttons.
- `MarkPainter.kt`: shared preview/export drawing, text wrapping, shapes, ink.
- `Models.kt`: backward-compatible draft/signature JSON; old stroke sizes and shape backgrounds migrate to stroke width and fill.
- `PageSelection.kt`: page ranges and rotation/crop transforms.

Dependencies: [Android Jetpack Compose](https://developer.android.com/compose), [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), and [Robolectric](https://robolectric.org/) for Android simulation tests. See `VALIDATION.md` for verified behavior and remaining device checks.
