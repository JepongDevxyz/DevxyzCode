# DevxyzCode — SPEC v2.0.0 (the 100% release)

Sariling branding ni Jepong: AI coding assistant for Android, inspired by
Claude Code, Cursor, OpenCode, OpenClaw, Hermes Agent, at Harness.
Hindi fork ng kahit alin sa kanila — sariling app, sariling pangalan,
sariling package.

- Package: `com.jepongdevxyz.devxyzcode`
- App: DevxyzCode 2.0.0 (versionCode 2)
- minSdk 26, targetSdk 35, compileSdk 35
- Stack: Kotlin + Jetpack Compose (Material3), AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9
  — same proven template as DevxyzLogin, na nag-build sa CI.
- Build: GitHub Actions `.github/workflows/android.yml` at the REPO ROOT
  (JDK 17, working-directory android, `gradle assembleDebug`) — GitHub only
  discovers workflows at the repo-root .github/workflows/, never under android/
- Delivery: APK via muse.ai link, never direct attachment.

## What "100%" means here

100% = every capability the six tools are known for is present in DevxyzCode
in an Android-safe, approval-gated form. One deliberate difference from the
terminal harnesses: DevxyzCode NEVER runs a shell command or writes a file
without Jepong tapping Approve/Apply/Save first. That is the safety line —
Claude Code / OpenClaw / Hermes run commands by themselves; on a phone,
DevxyzCode does every step with her tap. Everything else is in.

## Feature parity — honest table

| Feature | Claude Code | Cursor | OpenCode | OpenClaw | Hermes | Harness | DevxyzCode v2 |
|---|---|---|---|---|---|---|---|
| Chat with AI about code | yes | yes | yes | yes | yes | — | **yes** (BYOK) |
| Read whole project / search files | yes | yes | yes | yes | yes | — | **yes** (SAF, recursive + search) |
| Edit code files in-app | yes | yes | yes | yes | yes | — | **yes** (editor + Save) |
| AI proposes code, user applies it | yes | yes | yes | yes | yes | — | **yes** (Preview diff → Apply → Save) |
| Diff preview before applying | yes | yes | yes | — | yes | — | **yes** (added/removed lines, Approve/Discard) |
| Attach files as context (@file) | yes | yes | yes | yes | yes | — | **yes, up to 3 files** |
| Multi-provider / BYOK models | — | yes | yes | yes | yes | — | **yes** (OpenAI-compatible, default APInex) |
| Syntax-highlighted code view | yes | yes | yes | — | yes | — | **yes** (read-only highlight preview) |
| Reusable Skills (SKILL.md style) | yes | — | — | yes | yes | — | **yes** (create/use/delete Skills; Save-as-Skill from a reply) |
| Persistent memory across sessions | — | — | — | yes | yes | — | **yes** (Memory facts loaded into every chat) |
| Scheduled tasks / Automations (cron) | — | — | — | yes | yes | — | **yes, as due tasks** (create + list; run from Agent — no background agent on Android in v2) |
| Build / test pipeline + evidence | — | — | — | — | yes | yes | **yes** (pipeline step commands to copy, run elsewhere, record Pass/Fail + evidence note) |
| Encrypted API key storage | — | — | — | — | — | — | **yes** (EncryptedSharedPreferences, v1 key migrated) |
| Auto-write files without asking | yes | yes | yes | yes | yes | — | **NO — always needs her tap** |
| Run shell commands / builds by itself | yes | yes | yes | yes | yes | yes | **NO — approval-gated by design** |
| Tab autocomplete while typing | — | yes | — | — | — | — | **NO in v2** |
| LSP / go-to-definition / diagnostics | — | yes | yes | — | — | — | **NO in v2** |
| Messaging-gateway control (WhatsApp/Telegram) | — | — | — | yes | yes | — | **NO in v2** |

## v2 scope (what is built now)

1. **Agent tab** — BYOK chat; up to 3 attached files; Skills in use + Memory
   facts are loaded into the system prompt; a reply's code block gets a
   **diff preview** (added/removed counts + coloured lines) with Apply /
   Discard; any reply can be saved as a Skill (Hermes-style self-created skill).
2. **Files tab** — SAF project folder, recursive list + search, edit + save,
   Attach to Agent (max 3), and a syntax-highlighted read-only preview.
3. **Runs tab** — Harness-style pipeline steps (Gradle assembleDebug / unit
   tests, npm build, pytest) with copyable commands, run recording with
   Pass/Fail + evidence note, and OpenClaw-style scheduled tasks (create/list/delete).
4. **Skills tab** — create/list/use/delete Skills; add/delete Memory facts.
5. **Settings tab** — base URL, model, API key in EncryptedSharedPreferences.

## v2 honest limits (the only gaps left)

- No shell execution, no auto file writes, no auto builds/tests — by design,
  every change needs her tap.
- No autocomplete, no LSP, no messaging gateway, no background agent
  (Android does not run the agent while the app is closed in v2).
- One project folder at a time; file list caps at 500 files; highlight
  preview caps at 200 lines; diff caps at 400,000 line-pairs.
- AI replies depend on the key she enters.

## How to build

1. Repo `JepongDevxyz/DevxyzCode` (public, main branch) — already live.
2. Push the v2 changes (versionCode 2, new files Diff.kt / Highlight.kt,
   updated Store.kt / MainActivity.kt / Screens.kt, SPEC.md, README.md).
3. Workflow `.github/workflows/android.yml` is already at the repo root —
   a push to main auto-triggers CI run "Build DevxyzCode Android" →
   artifact `DevxyzCode-debug-apk`.
4. Verify the APK (aapt badging: package, version, minSdk; zip sha vs GitHub
   digest) and deliver via muse.ai link with expiry. Her phone test is the
   final verdict — only her phone makes it 100%.
