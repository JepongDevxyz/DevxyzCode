# DevxyzCode — SPEC v3.1.0


Sariling branding ni Jepong: AI coding assistant for Android, inspired by
Claude Code, Cursor, OpenCode, OpenClaw, Hermes Agent, at Harness.
Hindi fork ng kahit alin sa kanila — sariling app, sariling pangalan,
sariling package.

- Package: `com.jepongdevxyz.devxyzcode`
- App: DevxyzCode 3.1.0 (versionCode 5)
- minSdk 26, targetSdk 35, compileSdk 35
- Stack: Kotlin + Jetpack Compose (Material3), AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9
  — same proven template as DevxyzLogin, na nag-build sa CI.
- Build: GitHub Actions `.github/workflows/android.yml` at the REPO ROOT
  (JDK 17, working-directory android, `gradle assembleDebug`) — GitHub only
discovers workflows at the repo-root .github/workflows/, never under android/
- Delivery: APK via muse.ai link, never direct attachment.

## What "100%" means here

100% = every capability the six tools are known for is present in DevxyzCode
in some form — full where the phone allows it, honest-lite where it does not.

## Parity table (claimed only where the phone can actually do it)

| Capability | Claude Code | DevxyzCode |
|---|---|---|
| Code edit + save | yes | yes |
| Agent chat (BYOK) | yes | yes |
| File context attach | yes | yes (up to 3 files) |
| Diff preview before Apply | yes | yes |
| Skills | yes | yes |
| Persistent memory | yes | yes |
| Scheduled tasks | yes | yes |
| Telegram bot gateway | yes | yes |
| Shell / build / run code | NO | Terminal tab: yes, basic shell (sandboxed; gradle/npm/node/python/git still NO) |
| Provider self-setup (paste snippet) | — | Yes — paste a base URL + API key snippet in chat and apply it |
| Coarena preset (free models) | — | Yes — Coarena preset in Settings; models gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017, claude-fable-5 |
| LSP / autocomplete | yes | NO (diagnostics-lite + find-symbol only) |
| WhatsApp control | — | NO (no official personal-account API) |

## v3.0 scope

- Multi-session Agent: parallel sessions, each with its own history.
- Background scheduled tasks: run even when the app is closed, with
  notification + saved result.
- Telegram bot gateway: AI drafts a reply, the user still taps Send.
- Auto-save applied code as a Skill (default OFF).
- Diagnostics-lite (unbalanced braces + line-length check) + find-symbol.
- Autocomplete in the editor (words from the file + common keywords).

## v3.1 scope (new)

- Terminal tab: a sandboxed shell (`ShellRunner`) running `sh -c` in the app's
  own files folder — 30s timeout per command, output capped at 20,000 chars,
  destructive commands (rm -rf /, su, reboot, shutdown, mkfs, dd if=) refused.
  Separate tab, never inside chat; the AI suggests commands in ```sh blocks
  and the user taps Run in Terminal.
- Provider self-setup: paste a snippet with a base URL and API key into the
  Agent chat and the app detects it (`ProviderSetup.detect`), shows a card
  with the provider name, base URL and masked key preview, and applies it
  on tap (masked keys cannot be applied).
- Coarena preset in Settings (base https://coarena.ai/api/v1) with free models
  gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017, claude-fable-5;
  model lists follow the provider (Coarena vs APInex defaults).

## v3.0 honest limits

- No on-device builds: no JDK / Android SDK on the phone, and SAF folders
  have no executable path.
- No full LSP server: same reason; diagnostics-lite + find-symbol is what
  the phone can do.
- No WhatsApp control: no official personal-account API.

## v3.1 honest limits

- The Terminal is a basic shell only: no gradle, npm, node, python or git on
  the phone, and still no on-device builds.
- The AI still cannot run commands itself; it only suggests ```sh blocks for
  the user to run in the Terminal tab.
- A pasted provider setup with a masked key cannot be applied — paste the
  full key.
- Still no LSP, autocomplete, messaging gateway beyond Telegram, or
  background agent.

## Build

CI builds v3.1.0 (versionCode 5) from `main` on every push.
Workflow: `.github/workflows/android.yml` (repo root).
It runs `gradle assembleDebug` (working-directory `android`, JDK 17) and
uploads the debug APK as an artifact. The app itself is not signed in CI;
the debug APK installs directly for testing.

## Screens / behavior

- Files: browse project files, search, open in editor.
- Editor: syntax-highlighted preview, edit + save, autocomplete, diagnostics.
- Agent: chat with the AI, attach up to 3 files, Apply code with diff
  preview, Save as Skill, Run in Terminal for ```sh blocks.
- Terminal: sandboxed shell, 30s timeout, 20,000-char output cap.
- Runs: scheduled-task runs and their saved results.
- Skills: list, edit, delete.
- Messages: Telegram bot gateway drafts.
- Settings: provider preset (APInex / Coarena), base URL, API key, model.

## Notes

- BYOK only: the user supplies their own API key, stored encrypted on
  the device. No key is bundled in the app or the repo.
- The AI never writes files on its own: every Apply goes through the diff
  preview and the user taps Save.
- The AI never runs commands on its own: the Terminal tab is separate from
  chat, and only the user taps Run.
