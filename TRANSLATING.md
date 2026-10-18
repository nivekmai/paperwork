# Translating Paperwork

All app-provided UI text, accessibility labels, and user-facing validation/error messages are in `app/src/main/res/values/strings.xml`. English is the default. Add translations in `values-<language>/strings.xml` (for example `values-fr/strings.xml`), or Android BCP-47 resource directories for regional variants. Android selects the resources using the device/app locale and falls back to English for missing entries.

- Preserve resource names and positional placeholders such as `%1$s`, `%1$d`, and `%1$.1f`. Translators can reorder placeholders. `%%` in formatted strings produces a literal percent sign.
- Preserve Android XML escaping (for example `\'` for an apostrophe and `&amp;` for an ampersand). Do not translate entries marked `translatable="false"`.
- Count summaries use neutral labels (for example “Pages: 2”) to avoid English plural concatenation. If a translation needs plural forms, introduce Android `<plurals>` and `getQuantityString` rather than building sentences from fragments.
- Model identifiers such as `Text`, `Square`, `Dashed`, and `Triangle` are persisted in drafts. Do not translate these Kotlin/JSON identifiers. Their visible labels are mapped to string resources by `Resources.optionLabel`.
- User-authored document names, signatures, saved text, and inserted text are data, not translated UI. Date templates use the current app resource locale when inserted; already inserted dates remain unchanged.
- Protocol keys, MIME types, filenames used for internal storage, regexes, and numeric color encoding remain stable implementation constants. Error details from third-party libraries are not displayed as untranslated UI; a resource-backed fallback is shown.

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease` after changes. Check longer labels and RTL layouts on a device. This change supplies the English resources; it does not claim to add other languages.
