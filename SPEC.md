# DevxyzCode — SPEC v1.0.0

Sariling branding ni Jepong: AI coding assistant for Android, inspired by
Claude Code, Cursor, OpenCode, at Harness. Hindi fork ng kahit alin sa kanila —
sariling app, sariling pangalan, sariling package.

- Package: `com.jepongdevxyz.devxyzcode`
- App: DevxyzCode 1.0.0 (versionCode 1)
- minSdk 26, targetSdk 35, compileSdk 35
- Stack: Kotlin + Jetpack Compose (Material3), AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9
  — same proven template as DevxyzLogin, na nag-build sa CI sa unang try.
- Build: GitHub Actions `.github/workflows/android.yml` (JDK 17, `gradle assembleDebug`)
- Delivery: APK via muse.ai link, never direct attachment.

## Feature parity — honest table

| Feature | Claude Code | Cursor | OpenCode | Harness | DevxyzCode v1 |
|---|---|---|---|---|---|
| Chat with AI about code | yes | yes | yes | — | **yes** (BYOK) |
| Read whole project / search files | yes | yes (@codebase) | yes | — | **yes** (SAF folder, recursive list + search) |
| Edit code files in-app | yes | yes | yes | — | **yes** (editor + Save) |
| AI proposes code, user applies it | yes | yes (Composer) | yes | — | **yes** (Apply button, then Save — manual) |
| Attach a file as context (@file) | yes | yes | yes | — | **yes** |
| Multi-provider / BYOK models | — | yes | yes | — | **yes** (OpenAI-compatible base URL + key, default APInex) |
| Build / test pipeline checklist | — | — | — | yes | **yes, as a record** (Runs tab — you log Pass/Fail yourself) |
| Auto-write files without asking | yes | yes | yes | — | **NO — v1 never writes without your tap** |
| Run shell commands / builds by itself | yes | yes | yes | yes | **NO in v1** |
| Tab autocomplete while typing | — | yes | — | — | **NO in v1** |
| LSP / go-to-definition / diagnostics | — | yes | yes | — | **NO in v1** |

## v1 scope (what is built now)

1. **Agent tab** — chat with a BYOK model. Default base `https://api.apinex.bond/v1`,
   models: Gemini 3.8 Flash, GPT-6 Luna, Kimi K3, Muse Spark 1.3.
   No API key is built in. The reply's first fenced code block can be applied
   to the currently open file with one tap, then you still tap Save.
2. **Files tab** — open a project folder (Storage Access Framework),
   recursive file list (max 500), search by name, open + edit + save text/code
   files (read truncated at 200 KB in v1).
3. **Runs tab** — record a build/test run by hand (name, Pass/Fail, note, time).
   This is the Harness-style pipeline checklist for v1.
4. **Settings tab** — base URL, API key, model. Key stays in the app's private
   storage on the device and is only sent to the base URL you set.

## v1 honest limits

- No shell execution, no auto file writes, no auto builds/tests.
- No autocomplete, no LSP, no syntax highlighting beyond monospace text.
- API key is stored in private SharedPreferences in v1 (not yet
  EncryptedSharedPreferences — v2).
- One project folder at a time; file list caps at 500 files.
- AI replies depend on the key you enter — with no key, Agent shows an error
  and tells you to add one in Settings.

## v2 / v3 (planned, not built)

- v2: EncryptedSharedPreferences for the key, syntax highlighting,
  multi-file context, diff view before Apply, real Gradle build trigger
  from inside the app (with approval).
- v3: Tab-style autocomplete, LSP diagnostics, background agent sessions.

## How to build

1. Create repo `JepongDevxyz/DevxyzCode` (public, main branch).
2. Push `android/` contents to the repo root (so CI finds the workflow).
3. Commit `.github/workflows/android.yml` via the **GitHub web UI**
   (the GitHub App has no `workflows` permission for API pushes).
4. CI run "Build DevxyzCode Android" → artifact `DevxyzCode-debug-apk`.
5. Verify the APK (aapt badging: package, version, minSdk) and deliver
   via muse.ai link with expiry. Her phone test is the final verdict.
