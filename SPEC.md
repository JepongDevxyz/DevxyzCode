# DevxyzCode — SPEC v3.0.0

Sariling branding ni Jepong: AI coding assistant for Android, inspired by
Claude Code, Cursor, OpenCode, OpenClaw, Hermes Agent, at Harness.
Hindi fork ng kahit alin sa kanila — sariling app, sariling pangalan,
sariling package.

- Package: `com.jepongdevxyz.devxyzcode`
- App: DevxyzCode 3.0.0 (versionCode 4)
- minSdk 26, targetSdk 35, compileSdk 35
- Stack: Kotlin + Jetpack Compose (Material3), AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9
  — same proven template as DevxyzLogin, na nag-build sa CI.
- Build: GitHub Actions `.github/workflows/android.yml` at the REPO ROOT
  (JDK 17, working-directory android, `gradle assembleDebug`) — GitHub only
  discovers workflows at the repo-root .github/workflows/, never under android/
- Delivery: APK via muse.ai link, never direct attachment.

## What "100%" means here

100% = every capability the six tools are known for is present in DevxyzCode
in an Android-feasible, approval-gated form. Two things a phone genuinely
cannot do are documented below as the only remaining gaps — everything else
is in.

## Feature parity — honest table

| Feature | Claude Code | Cursor | OpenCode | OpenClaw | Hermes | Harness | DevxyzCode v3 |
|---|---|---|---|---|---|---|---|
| Chat with AI about code | yes | yes | yes | yes | yes | — | **yes** (BYOK) |
| Read whole project / search files | yes | yes | yes | yes | yes | — | **yes** (SAF, recursive + search) |
| Edit code files in-app | yes | yes | yes | yes | yes | — | **yes** (editor + Save) |
| AI proposes code, user applies it | yes | yes | yes | yes | yes | — | **yes** (Preview diff → Apply → Save) |
| Diff preview before applying | yes | yes | yes | — | yes | — | **yes** (added/removed lines, Approve/Discard) |
| Attach files as context (@file) | yes | yes | yes | yes | yes | — | **yes, up to 3 files** |
| Multi-provider / BYOK models | — | yes | yes | yes | yes | — | **yes** (OpenAI-compatible, default APInex) |
| Syntax-highlighted code view | yes | yes | yes | — | yes | — | **yes** (read-only highlight preview) |
| Autocomplete while typing | — | yes | — | — | — | — | **yes** (word-based: identifiers from the open file + keywords, tap to insert) |
| Diagnostics / go-to-definition | — | yes | yes | — | — | — | **yes, lite** (on-device brace/paren/string checks, TODO scan, project-wide find-symbol) |
| Reusable Skills (SKILL.md style) | yes | — | — | yes | yes | — | **yes** (create/use/delete; Save-as-Skill; auto-save of applied code, toggle default OFF) |
| Persistent memory across sessions | — | — | — | yes | yes | — | **yes** (Memory facts loaded into every chat) |
| Multiple agent sessions (parallel subagents) | yes | — | — | yes | yes | — | **yes** (sessions with own histories; switch while another thinks) |
| Scheduled tasks / Automations (cron) | — | — | — | yes | yes | — | **yes** (WorkManager background runs every N hours + notification + saved result; Run now) |
| Messaging gateway (Telegram bot) | — | — | — | yes | yes | — | **yes** (Bot API polling; AI-drafted replies, sent only on her tap) |
| Build / test pipeline + evidence | — | — | — | — | yes | yes | **yes** (pipeline step commands to copy, run elsewhere, record Pass/Fail + evidence note) |
| Encrypted API key storage | — | — | — | — | — | — | **yes** (EncryptedSharedPreferences; bot token encrypted too) |
| Branded icon + splash screen | — | — | — | — | — | — | **yes** (cyan `</>` on dark navy, splash no white flash) |
| Auto-write files without asking | yes | yes | yes | yes | yes | — | **NO — always needs her tap** |
| Run shell commands / builds on-device | yes | yes | yes | yes | yes | yes | **NO — not possible on a phone** (no JDK / Android SDK on-device; SAF folders have no executable paths) |
| Full LSP server on-device | — | yes | yes | — | — | — | **NO — same reason; diagnostics-lite + find-symbol is the feasible slice** |
| WhatsApp control | — | — | — | yes | yes | — | **NO — no official personal-account API** |

## v3 scope (added on top of v2.1)

1. **Autocomplete** (`Autocomplete.kt`) — suggestions from identifiers in the
   open file + language keywords, shown as tap-to-insert chips under the editor.
2. **Diagnostics + find-symbol** (`Diagnostics.kt`) — on-device checks
   (brace/paren balance, unclosed strings, TODO/FIXME, trailing whitespace,
   long lines) via a Check button; project-wide content search for a symbol
   (max 100 files / 50 hits), tap a hit to open that file at the line.
3. **Multi-session Agent** — sessions with their own histories, persisted
   across restarts; switch sessions while another is thinking (coroutines).
4. **Background scheduled tasks** (`TaskWorker.kt`, WorkManager, 15-min
   cycle) — due tasks run through the AI, result saved in Runs + notification
   posted (POST_NOTIFICATIONS permission, asked once). Never writes files,
   never sends messages.
5. **Telegram gateway** (`TelegramClient.kt`, Messages tab) — user's own bot
   token (encrypted); incoming messages polled in the background + Refresh;
   AI drafts a reply; she edits it and taps Send. Nothing is sent without her tap.
6. **Auto-save applied code as Skill** — toggle in Settings, default OFF;
   when ON, every applied code block is also saved as a Skill (Hermes-style
   learning from what worked).

## v2.1 scope

- **Branded app icon** — adaptive icon, cyan `</>` mark on dark navy
  (`#0B1220` bg, `#22D3EE` accent), round + square.
- **Branded splash screen** — Android 12+ SplashScreen API
  (`androidx.core:core-splashscreen`), dark navy background, same `</>`
  mark as the animated icon, no white flash on launch.

## v2 scope

1. **Agent tab** — BYOK chat; up to 3 attached files; Skills in use + Memory
   facts are loaded into the system prompt; a reply's code block gets a
   **diff preview** (added/removed counts + coloured lines) with Apply /
   Discard; any reply can be saved as a Skill.
2. **Files tab** — SAF project folder, recursive list + search, edit + save,
   Attach to Agent (max 3), and a syntax-highlighted read-only preview.
3. **Runs tab** — Harness-style pipeline steps (Gradle assembleDebug / unit
   tests, npm build, pytest) with copyable commands, run recording with
   Pass/Fail + evidence note, and OpenClaw-style scheduled tasks.
4. **Skills tab** — create/list/use/delete Skills; add/delete Memory facts.
5. **Settings tab** — base URL, model, API key in EncryptedSharedPreferences.

## v3 honest limits (the only gaps left)

- No shell execution, no auto file writes, no on-device builds — a phone has
  no JDK / Android SDK and SAF folders have no executable paths. The
  Harness-style pipeline (copyable commands + evidence records) is the
  feasible substitute; builds run in DevxyzIDE / a terminal / GitHub Actions.
- No full LSP server — same reason; diagnostics-lite + find-symbol instead.
- No WhatsApp control — no official personal-account API.
- Telegram polling runs at most every 15 min in the background (Android's
  WorkManager minimum); Refresh in the Messages tab polls immediately.
- One project folder at a time; file list caps at 500 files; highlight
  preview caps at 200 lines; diff caps at 400,000 line-pairs; find-symbol
  caps at 100 files / 50 hits.
- AI replies depend on the key she enters.

## How to build

1. Repo `JepongDevxyz/DevxyzCode` (public, main branch) — already live.
2. Push the v3 changes (versionCode 4; new files Autocomplete.kt /
   Diagnostics.kt / TelegramClient.kt / TaskWorker.kt; updated Store.kt /
   AiClient.kt / MainActivity.kt / Screens.kt / AndroidManifest.xml /
   build.gradle.kts, SPEC.md, README.md).
3. Workflow `.github/workflows/android.yml` is already at the repo root —
   a push to main auto-triggers CI run "Build DevxyzCode Android" →
   artifact `DevxyzCode-debug-apk`.
4. Verify the APK (aapt badging: package, version, minSdk; zip sha vs GitHub
   digest) and deliver via muse.ai link with expiry. Her phone test is the
   final verdict — only her phone makes it 100%.
