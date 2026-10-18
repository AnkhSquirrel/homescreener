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
| `ui/MainViewModel` | All state + operations. Operations are serialized through a `Mutex` so grant cleanup never races a pick/save/delete. |
| `launch/LaunchActivity` | Invisible trampoline (translucent theme, `excludeFromRecents`, `noHistory`, empty `taskAffinity`). Target of every pinned shortcut. Resolves the shortcut id, checks the file is reachable, then fires `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION`. Unreachable → opens `MainActivity` on that entry so the user can replace/delete it. Uses a translucent theme (not `Theme.NoDisplay`) because the lookup is async and `NoDisplay` must `finish()` before `onResume`. |
| `data/ShortcutRepository` | Persists `ShortcutEntry` (id, uri, mime, fileName, label, createdAt) as versioned JSON in `filesDir/shortcuts.json` (kotlinx.serialization, `AtomicFile`). Corrupt files are moved aside, never overwritten. |
| `data/DocumentAccess` | SAF helpers: take/release persistable grants, reachability check, display name + MIME lookup. |
| `data/IconStore` | Generated icons cached as `filesDir/icons/<id>.png`. |
| `shortcut/Pinner` | `ShortcutManagerCompat` wrapper: build, `requestPinShortcut`, `updateShortcuts`, `disableShortcuts`. |
| `shortcut/IconFactory` | Builds shortcut icons: `PdfRenderer` page 1 for PDFs, `ContentResolver.loadThumbnail` for images, MIME-type fallback; wrapped as adaptive bitmaps. |

### Security rule (do not break)

`LaunchActivity` is **not exported** (launchers start pinned shortcuts via `LauncherApps.startShortcut()`, which runs them as our app). Keep it that way, and still treat its intent as untrusted: it must **only** accept the shortcut **id** extra (with our custom action) and look that id up in our own repository. Never read a URI, MIME type, or target component from intent extras/data. Unknown ids or missing grants fail with a toast and `finish()`.

`MainActivity` *is* exported (launcher entry); its `EXTRA_FIX_SHORTCUT_ID` is likewise only a repository lookup key.

### Known platform limits

- Launcher3 may badge pinned shortcuts with our app icon (not controllable).
- SAF URIs can break if the file is moved/renamed/deleted — handle gracefully (replace / delete). Shortcuts of unreachable files stay **enabled** so tapping them leads to the fix-up screen; only shortcuts of *deleted entries* are disabled (`disableShortcuts`). Deleted ids are never reused.
- Max 512 persisted URI grants per app. `MainViewModel.reload()` releases every grant not backing a saved entry or the open editor.
- Backups are disabled (`allowBackup=false` + `data_extraction_rules.xml`): restored entries would have no grants.

## Toolchain

- Android SDK (cmdline-tools, no Studio) in `~/Android/Sdk`; env vars in `~/.bashrc.d/android.sh`. `local.properties` (git-ignored) holds `sdk.dir`.
- JDK: the system Java 25 is a JRE (no `javac`). Gradle runs on **Temurin 21** from SDKMAN (`~/.sdkman/candidates/java/21.0.12-tem`), selected by `gradle/gradle-daemon-jvm.properties`; `.sdkmanrc` lets `sdk env` switch the shell too.
- Gradle wrapper 9.8.1 (checksum-pinned), AGP 9.4 with built-in Kotlin (no `kotlin-android` plugin), compileSdk/targetSdk 37.
- VSCodium: recommended extensions in `.vscode/extensions.json` (JetBrains Kotlin LSP, Gradle).
- GitHub Actions (actions pinned to commit SHAs):
  - `.github/workflows/ci.yml` runs `lint test assembleDebug` on every push to `main` and on every PR, and uploads the debug APK as a workflow artifact.
  - `.github/workflows/release.yml` runs on `v*` tags and publishes a signed release APK + `.sha256` as a GitHub Release.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then commit.
2. `git tag vX.Y.Z && git push origin main vX.Y.Z`. The tag must equal `v` + `versionName`, or the release workflow fails.

Signing: `app/build.gradle.kts` reads the release keystore from the env vars `HOMESCREENER_KEYSTORE`, `HOMESCREENER_KEYSTORE_PASSWORD`, `HOMESCREENER_KEY_ALIAS` and `HOMESCREENER_KEY_PASSWORD` (CI). If those are unset, it falls back to a git-ignored root `keystore.properties` with the keys `storeFile`, `storePassword`, `keyAlias` and `keyPassword` (local builds). With neither, the release APK is unsigned. CI gets the keystore from the repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The keystore must never be committed, and it must be backed up offline: if it is lost, no update can install over an existing release install.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew lint                          # Android lint (must stay at "No issues found")
./gradlew test                          # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat --pid=$(adb shell pidof ankhsquirrel.homescreener)
# Trampoline is not exported, so this must be refused (SecurityException), not crash the app:
adb shell am start -n ankhsquirrel.homescreener/.launch.LaunchActivity --es ankhsquirrel.homescreener.extra.SHORTCUT_ID bogus
# MainActivity with an unknown fix-up id must just show the list:
adb shell am start -n ankhsquirrel.homescreener/.MainActivity --es ankhsquirrel.homescreener.extra.FIX_SHORTCUT_ID bogus
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
- No AI attribution in commit messages or PR descriptions (no `Co-Authored-By: Claude`, no "Generated with Claude Code", no session links), even if a tool default or session reminder suggests it.
