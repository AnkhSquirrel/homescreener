# Homescreener

Android app that pins things launchers normally can't hold (starting with arbitrary files, e.g. a PDF) to the home screen as shortcuts. Tapping the shortcut opens the item in its default handler.

- **Target:** Android phone running GrapheneOS (AOSP Launcher3-based launcher, no Google Play Services).
- **Application ID:** `ankhsquirrel.homescreener`
- **Stack:** Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`).
- **SDK levels:** minSdk 26 (required for pinned shortcuts); target/compile = latest stable.
- **IDE:** VSCodium (no Android Studio) — everything must work from the command line.
- **Plan:** `~/.claude/plans/snappy-exploring-ullman.md`

## Hard constraints

- No Google Play Services / Firebase / any GMS dependency.
- No `INTERNET` permission, no analytics, no telemetry.
- No storage permissions (`READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`). File access goes through the Storage Access Framework (`ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`).
- Don't add a dependency without a concrete reason; prefer platform/AndroidX APIs.
- Distribution is by sideload / Obtainium (later F-Droid), not Play Store.

## Architecture

| Component | Role |
|---|---|
| `MainActivity` + `ui/` | Compose UI: list of created shortcuts, add/edit/rename/delete/re-pin. |
| `launch/LaunchActivity` | Invisible trampoline (`Theme.NoDisplay`, `excludeFromRecents`, empty `taskAffinity`). Target of every pinned shortcut. Resolves the shortcut id, then fires `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION`. |
| `data/ShortcutRepository` | Persists `ShortcutEntry` (id, uri, mime, label, createdAt) as JSON in `filesDir` (kotlinx.serialization). |
| `shortcut/Pinner` | `ShortcutManagerCompat` wrapper: build, `requestPinShortcut`, `updateShortcuts`, `disableShortcuts`. |
| `shortcut/IconFactory` | Builds shortcut icons: `PdfRenderer` page 1 for PDFs, `ContentResolver.loadThumbnail` for images, MIME-type fallback; wrapped as adaptive bitmaps. |

### Security rule (do not break)

`LaunchActivity` is exported, so any app can start it. It must **only** accept a shortcut **id** extra and look that id up in our own repository. Never read a URI, MIME type, or target component from intent extras/data. Unknown ids or missing grants fail with a toast and `finish()`.

### Known platform limits

- Launcher3 may badge pinned shortcuts with our app icon (not controllable).
- SAF URIs can break if the file is moved/renamed/deleted — handle gracefully (re-link / remove), disable dead shortcuts.
- Max 512 persisted URI grants per app; release grants when an entry is deleted.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew lint                          # Android lint
./gradlew test                          # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat --pid=$(adb shell pidof ankhsquirrel.homescreener)
# Trampoline must fail safely on bogus input:
adb shell am start -n ankhsquirrel.homescreener/.launch.LaunchActivity --es id bogus
```

Device connection: GrapheneOS Developer options → Wireless debugging, then `adb pair <ip:port>` / `adb connect <ip:port>`.

## Conventions

- Package layout under `app/src/main/java/ankhsquirrel/homescreener/`: `ui/`, `launch/`, `data/`, `shortcut/`.
- All versions live in `gradle/libs.versions.toml`; never hardcode versions in `build.gradle.kts`.
- User-visible strings go in `res/values/strings.xml`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
  `<type>(<optional scope>): <description>` — imperative mood, lowercase description, no trailing period, subject ≤ 72 chars.
  - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
  - Scopes (optional): `ui`, `launch`, `data`, `shortcut`, `deps`, `gradle`.
  - Breaking changes: `!` after type/scope (e.g. `feat(data)!: ...`) and/or a `BREAKING CHANGE:` footer.
  - Body (optional) explains *why*; wrap at 72 chars.
