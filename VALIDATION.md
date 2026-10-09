# Validation — Paperwork 0.10.2

## Result

`assembleDebug`, `testDebugUnitTest`, and `lintDebug` all completed successfully. All 56 tests passed. Lint reported no errors; remaining warnings concern dependency/tool updates, bundled library code, and drawing/style recommendations. The shipped APK also passed signature and alignment verification.

## Automated coverage

- Page selection: all pages, explicit ranges, user-specified ordering, duplicate/out-of-bounds/malformed input rejection.
- Rotation transforms: 0/90/180/270 degrees, negative angles, crop-box offsets.
- Real PDF import, atomic draft save/reload, export, extraction, page ordering, preserved original source bytes, retained original text and crop/rotation metadata.
- Text, circle, square, oval, check, X, and signature painting using native Android graphics under Robolectric; transparent pixels outside additions.
- Signature library save/reload/delete; deletion leaves copies in saved drafts intact.
- Signature pad normalization, undo stroke, and clearing.
- Landscape drawing activity uses the available screen area with Undo, Clear, and Accept aligned vertically on the right. Acceptance is disabled until there is ink; accepting returns only the drawing. Drawing restoration survives activity recreation within floating-point tolerance.
- Portrait signature form launches the landscape activity, receives its drawing, preserves the accepted drawing when a redraw is cancelled, and retains the name and drawing across recreation before saving.
- Canvas dragging and resizing constrained to page bounds. All four edge handles preserve their opposite edge. Corner handles retain the current aspect ratio, including for stretched circles/squares and signatures.
- Tap once selects text; tap again requests inline input. Handle taps and drags do not start typing.
- Native inline input saves changed text to disk before closing and groups the typing session into one undo action.
- Old draft JSON loads with matching stroke widths and migrated shape fills. Ellipse fill colors stay inside the oval, not its rectangular bounds. Stroke style round-trips through draft storage.
- Compose home/library/drawing-pad navigation, visual shape selection, shape stroke controls and absence of box dimension fields, and import → inline text insertion → undo/redo → tap-again inline editing → undo/redo → export screen. Export page selection preserves the entered order and disables saving invalid ranges.

The UI interaction suite uses a test-only blank-page replacement for Android PdfRenderer because Robolectric does not implement PDFium page rendering. This replacement is **not** included in the APK. The PDF import/export and paint tests do not use it.

- Lines support independent endpoint movement, crossing endpoints, and page-bound clamping. Head dragging snaps within ±2° of all eight 45° directions while the tail stays fixed; outside that tolerance the head moves freely. Boundary and angle-wrap cases are covered. Tail dragging remains free. Page-edge clipping preserves the snapped angle. A minimum length prevents degenerate arrows, and hit testing ignores empty space around diagonal lines.
- All six arrowhead styles render and round-trip through draft storage; start and end settings can differ.
- Doodle gestures group multiple strokes (including dots) on the correct page, preserve page coordinates, and discard interrupted strokes. Finishing and starting again creates a new doodle.
- The UI test verifies Oval is absent, line arrowheads can be chosen, and doodle strokes save and undo/redo from the editor. Older Oval draft objects still load.

- Zoomed and panned viewport insertion for text, shapes, and signatures, including preserving the viewport across the signature-library round trip.
- Zoomed signature insertion is at most half the screen width, with at least quarter-width margins and the original aspect ratio.
- Style preview remains visible at the same screen position while scrolling style controls.

- Animated portrait rotation cue appears before launching the landscape drawing activity.
- Custom picker hue, saturation/brightness, opacity slider, percent entry and six/eight-digit hex entry; alpha survives draft JSON and native painting. Transparent swatch matches the other squares in size and alignment.

- Color picker has no scroll at normal 411 × 891 dp sizing, while 250% Android font scaling retains scrolling and reachable Apply/hex controls.
- Square corner-radius slider changes the stored radius without changing dimensions. Legacy marks default to sharp corners; rounded stroke/fill painting leaves corners transparent and clamps safely when resized smaller.

- Placement snapping tests cover the two-screen-pixel threshold at multiple zoom levels, edges and centers, page bounds, self exclusion, and cross-page exclusion.
- Haptic detent state fires on entry/change/re-entry without repeating while held. Native canvas gestures request Android CLOCK_TICK feedback for alignment and line-angle snaps. Physical vibration still needs phone testing.
- Canvas nudges move exactly 1/5 screen pixels at zoom. UI coverage verifies quick color/width tray selection, inherited defaults, nudge undo, and draft persistence.

- Keyboard editing hides insertion/nudge bars and keeps the same native EditText and caret during quick color/font-size and custom hex edits. Fit control is absent; doodle quick presets omit transparent and initialize visible ink.
- PDF scale stays constant through viewport shrinking/expanding. Insertion tests use the viewport at insertion time as conditional toolbars change available space.
- Native drag tests reject tap-sized motion, preserve consecutive one-pixel movements during an active drag, and ignore additional lift-off coordinates when committing.
- Release stabilization tests cover brief pre-UP wobble after a hold, continuous small movements, held adjustments, larger movements, screen-pixel limits at different zoom levels, gesture resets, and native batched MOVE events. The supplied recording was inspected frame by frame; actual finger behavior still requires phone testing.
- Nudge long-press test checks 150 ms repeats and immediate stop on release.

## Independent PDF verification

`tools/check-export.py` uses PyMuPDF, separately from the app's PDFBox implementation, to render the exported fixture. All four rotated/cropped pages must contain the red test addition at displayed pixel bounds `(40, 50, 109, 119)`, within one pixel. The extracted PDF must contain only original page 3. This check passed. The new drawing fixture also verifies filled circle/triangle arrowheads, doodle strokes, and a single-point dot after independent PDF rasterization.

## APK checks

- Android minimum version: API 26 (Android 8.0).
- APK Signature Scheme v2 verified; the debug certificate matches version 0.1.0 for in-place upgrades preserving local drafts and signatures.
- ZIP alignment verified, including 16 KB shared-library alignment.
- Manifest inspected: no INTERNET permission or broad file-storage permission.
- App-private document/signature storage; cloud backups and device transfer excluded.

## Still needs a real device

No physical phone or Android emulator was connected. Platform PDF preview, system file-provider pickers, email sharing, large/complex PDFs, real finger/pinch gestures, font scaling, and OEM-specific behavior need device testing. The APK is a first development build, not a Play Store release.
