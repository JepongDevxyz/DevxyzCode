# DevxyzCode — SPEC v3.1.1

Sariling branding ni Jepong: AI coding assistant for Android, inspired by
Claude Code, Cursor, OpenCode, OpenClaw, Hermes Agent, at Harness.
Hindi fork ng kahit alin sa kanila — sariling app, sariling pangalan,
sariling package.

- Package: `com.jepongdevxyz.devxyzcode`
- App: DevxyzCode 3.1.1 (versionCode 6)
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
| Run shell commands / builds on-device | yes | yes | yes | yes | yes | yes | **yes, basic shell** (separate Terminal tab, sandboxed; `ls`/`echo`/`cat` work; gradle/npm/node/python/git still NO — those binaries do not exist on a phone) |
| Full LSP server on-device | — | yes | yes | — | — | — | **NO — same reason; diagnostics-lite + find-symbol is the feasible slice** |
| WhatsApp control | — | — | — | yes | yes | — | **NO — no official personal-account API** |
| Provider self-setup (paste snippet in chat) | — | — | — | — | — | — | **yes** (Coarena "Copy for your agent" snippet detected in Agent chat; base URL + key + model applied only on her tap; key never sent to the AI) |
| Coarena preset (base URL + free models) | — | — | — | — | — | — | **yes** (Settings preset; free models gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017; claude-fable-5 needs paid balance) |

## v3.1.1 repair (2026-11-16 — the v3.1.0 web push clobbered main)

The v3.1.0 push (browser, web UI) overwrote `build.gradle.kts` with a stale
pre-v2.1 copy: it lost `androidx.core:core-splashscreen:1.0.1` (splash theme)
and `androidx.work:work-runtime-ktx:2.9.0` (scheduled tasks), so CI run #32
failed at `processDebugResources` (Theme.SplashScreen attrs not found). The
same stale-copy mechanism had earlier shipped a `Screens.kt`/`MainActivity.kt`
pair whose editor lacks the v3 autocomplete / diagnostics / find-symbol /
syntax-highlight UI (the logic files exist on main but were uncalled).
v3.1.1 restores the exact local files: full dependencies + full editor UI.
Lesson (AGENTS.md): after EVERY browser push, diff live files vs local with
`get_file_contents` before trusting CI; for full-file restores, upload the
exact local file via the GitHub web UI instead of describing edits.

## v3.1 scope (added on top of v3)

1. **Terminal tab** (`ShellRunner.kt`, her order "Wag dapat direct sa chat yon")
   — a SEPARATE tab, never inside the Agent chat. Runs sandboxed shell
   commands via `sh -c` in the app's own files dir (no root). Basic commands
   work (ls, echo, cat, pwd); gradle/npm/node/python/git fail honestly with
   "command not found" (those binaries do not exist on a phone). 30s timeout,
   output capped at 20,000 chars, destructive commands refused (rm -rf /,
   su, reboot, shutdown, mkfs, dd). The AI's ```sh blocks get a "Run in
   Terminal" button that switches to the Terminal tab and runs the block —
   only on her tap.
2. **Provider self-setup** (`ProviderSetup.kt`) — paste a provider's setup
   snippet (e.g. Coarena's "Copy for your agent" prompt) into the Agent chat
   and the app detects base URL + API key + model and offers Apply. The pasted
   message is NEVER sent to the AI (the key would be sent to the provider and
   logged) and is redacted in chat history; Apply saves the key encrypted.
   A masked key (••••) cannot be applied — she must paste the full key.
   Coarena preset in Settings sets base URL https://coarena.ai/api/v1 and
   free models (gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017;
   claude-fable-5 needs paid balance). Model is now a free-text field too,
   so any model ID can be typed.

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

## v3.1 honest limits (the only gaps left)

- No on-device builds — a phone has no JDK / Android SDK and SAF folders
  have no executable paths. The Terminal runs basic shell only; gradle, npm,
  node, python and git do not exist on a phone. Builds run in DevxyzIDE /
  a terminal / GitHub Actions; the Harness-style pipeline (copyable commands
  + evidence records) is the feasible substitute.
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
2. Push the v3.1.1 changes (versionCode 6; restored build.gradle.kts /
   Screens.kt / MainActivity.kt / AiClient.kt + SPEC.md / README.md) — upload
   the EXACT local files via the GitHub web UI (browser only, signed in as
   JepongDevxyz, never via push-files or API), one commit for all files so no
   intermediate broken CI run is created.
   build.gradle.kts, SPEC.md, README.md).
3. Workflow `.github/workflows/android.yml` is already at the repo root —
   a push to main auto-triggers CI run "Build DevxyzCode Android" →
   artifact `DevxyzCode-debug-apk`.
4. Verify the APK (aapt badging: package, version, minSdk; zip sha vs GitHub
   digest) and deliver via muse.ai link with expiry. Her phone test is the
   final verdict — only her phone makes it 100%.

## v3.2.0 — GitHub Announce + Connect (2026-11-16, her order "Kaya din dapat mag anonsa GitHub" / "Mag connect")

- New **Announce tab** (tab 6, megaphone icon; Settings moves to tab 7): post an **Issue** or
  publish a **Release** in any repo she can access, via the GitHub REST API
  (`GitHubClient.kt`, org.json, no new dependencies).
- **Connect GitHub** button: verifies the token live (`GET /user`) and shows
  "Connected as @username". Auth is her own Personal Access Token (repo scope),
  entered in Settings and stored encrypted — never hardcoded, never sent anywhere
  except api.github.com. Nothing is posted without her tap.
- **AI Draft** button writes the announcement from the title (same BYOK model as chat).
- Release needs a tag (e.g. v3.2.0); Issue needs title + body. Result shows the posted
  URL, or the exact GitHub error (e.g. 401 Bad credentials, 404 repo not found).
- versionCode 7 / versionName "3.2.0". Push via browser (exact local files, signed in as
  JepongDevxyz) after the v3.1.1 APK is delivered; CI run "Build DevxyzCode Android" →
  artifact `DevxyzCode-debug-apk`, verify + muse.ai link as usual.

- **AI Studio preset (same v3.2.0, her order "Isama mo na aistudio"):** Google AI Studio
  (Gemini API) as a third provider preset — base
  `https://generativelanguage.googleapis.com/v1beta/openai` (OpenAI-compatible,
  verified 2026-11-16), models gemini-3.8-flash / gemini-3.6-flash /
  gemini-3.5-flash / gemini-3.1-pro, key from aistudio.google.com/apikey (BYOK,
  encrypted; paste detection also accepts GEMINI_API_KEY=). Model list follows
  the provider, same as Coarena.
- **Antigravity (her order, checked 2026-11-16): NOT added as a provider — it has
  NO public API** (its models are only reachable through the Antigravity IDE/CLI
  with a Google account + subscription, via an undocumented internal endpoint).
  No hacky internal-endpoint integration was built. Its agent-first coding is
  what DevxyzCode's multi-session Agent + Terminal + diff preview already do.

## v3.3.0 — Multi-file Agent plan + Vision (2026-11-16, her order "nagagawa ng aistudio at antigravity e kayang gawin ng DevxyzCode" — capabilities, not just presets)
- **Multi-file plan (Antigravity-style agent):** the AI answers with one ```lang:relative/path/File.ext block per file. A "Show plan (N files)" button on the reply opens one card per file — EDIT or NEW badge, +/- line counts, scrollable diff vs the current file (DiffUtil) — and one tap on "Apply all (N)" writes them all (existing files via ProjectStore.writeText, new files created with any missing parent folders via ProjectStore.createFile). "Open in Files" opens the first changed file in the editor for validation. The tap-gate stays: nothing is written before Apply all.
- **Build-from-prompt scaffolding:** the same machinery — when she asks for a new app/project, the system prompt tells the AI to output EVERY file with full relative paths, so the whole project is created in one Apply all.
- **Vision (AI Studio multimodal):** an Attach image button in the Agent composer (GetContent image/*, downscaled to max 1024px / JPEG 85 / Base64 on-device). The image rides the newest user message as an OpenAI-style image_url part (AiClient.chat imageBase64 param); Gemini models read it. The image is cleared after the send.
- **Honest correction to the promised "Terminal validate":** the sandboxed Terminal runs in the app's filesDir and cannot see the SAF project folder, so terminal validation of applied files is NOT possible. Validation in v3.3 = review each diff in the plan + open the changed files in Files/editor. No gradle/npm/node/python/git on the phone (unchanged honest gap).

## v3.4.0 — Professional UI, no overlapping (2026-11-17, her order from her phone screenshot: "Ayusin mo no overlapping" + "Mukhang professional naman sana ang UI Interface" + "Para UI ng mga ito pinag halo halo Claude Code / Cursor / OpenCode / OpenClaw / Harness / Hermess / Antigravity / Aistudio 100%")
- **No-overlap fixes (measured from her screenshot):** the model chips row squished "Kimi K3" into one vertical letter per line and the bottom nav wrapped every label ("Age nt", "Setti ngs"). Every chip row that can overflow is now a FlowRow (wraps to the next line, never squishes): model chips, session chips, message actions (Copy / Save as Skill / Preview diff / Show plan / Run in Terminal), editor toolbar (Save / Attach / Highlight / Check), autocomplete suggestions, provider presets. The bottom NavigationBar (8 tabs in one width) is replaced by a scrollable pill tab bar — icon + single-line label, active tab highlighted, never wraps. Long attached-file paths ellipsize instead of pushing "Remove" off-screen.
- **Professional blended UI (Cursor / AI Studio style):** the Agent gets an empty-state hero — "What do you want to build?" + suggestion chips ("Fix a bug in my code", "Build a new screen", "Explain this code") that fill the input on tap — instead of a big blank area.
- **Settings like the reference apps (Cursor / VS Code style):** organized into sections — Appearance (new System / Dark / Light theme picker, stored in SettingsStore, whole app re-themes), Provider & Model, GitHub (Announce), Telegram (Messages), Agent (auto-save Skill, Qwen-row), About. No setting was removed; every existing field kept its place.
- **Scope note (honest):** v3.4.0 is the UI parity pass. Capability parity with the eight apps continues within the phone's honest limits (no on-device gradle/npm/node/python/git, no full LSP, no WhatsApp control — documented in SPEC).

## v3.5.0 — Codex UI + capabilities (2026-11-17, her order "Idagdag mo rin Codex UI at kayang gawin lahat ng Codex")
- **Codex-style approval modes:** Suggest (default — the tap-gate: Preview diff / Show plan, then Apply) and Auto (single-file code goes straight into the editor, no tab switch — still NOT written to disk until she taps Save in Files). The mode switch sits in the Agent header like Codex's, and in Settings → Agent. Stored in SettingsStore (KEY_APPROVAL_MODE).
- **Codex UI:** working indicator names the model ("Working with Gemini 3.8 Flash…", no fake step list — the reply is one shot); plan diff headers are monospace like Codex's.
- **Parity mapping (honest):** Codex capabilities already present in DevxyzCode — agent coding, diff review, terminal, multi-session, GitHub posting (Announce). Deliberately NOT copied: Codex's full-auto disk writes (her tap-gate rule forbids silent disk writes) and cloud sandbox environments (a phone has no cloud sandbox). Still not possible on a phone: gradle/npm/node/python/git, full LSP, WhatsApp control.

## v3.6.0 — Public Release (2026-11-17, her order "Enhance mo gagawin kasi nating public yan rerelease natin sa maraming users")
- **First-run onboarding:** new OnboardingScreen — welcome (what the app is, BYOK, tap-gate) → provider setup (preset chips + base URL + key + model, key required or Skip) → optional project folder (SAF picker) → Finish. Every step skippable. Shown only when no API key is saved yet (existing users with a key skip it automatically); completion is stored in SettingsStore (KEY_ONBOARDING_DONE).
- **Public defaults:** the announce repo no longer defaults to her own repo (blank for fresh installs; her saved value is untouched). No other personal defaults ship.
- **Privacy:** PRIVACY.md in the repo root (keys encrypted on-device, requests only to the chosen provider, no analytics/ads/tracking, no account/server) + a privacy note in Settings → About. For the Play Store listing, the policy URL is the PRIVACY.md file on GitHub.
- **First-run safety (audited):** Files shows "No folder opened yet" with an Open button; Agent shows the empty-state hero; Settings has sane defaults. Nothing crashes on a fresh install.
- **Still open for a real Play Store release (needs her decision):** a signed release build (AAB/APK) needs a release keystore + GitHub secrets for CI signing, and the workflow file must be updated to build `bundleRelease` — the workflow edit goes through the GitHub web UI (the API cannot touch workflows). The debug APK in this version is still fine for direct distribution.

## v3.6.1 — Status-bar fix (2026-11-17, her phone screenshot of v3.6.0 onboarding)
- **Root cause:** the onboarding screen is shown WITHOUT a Scaffold, so it never got the system-bar insets. On targetSdk 35 the system draws edge-to-edge by default, so the "DevxyzCode" title rendered half-hidden behind the status bar.
- **Fix:** OnboardingScreen's Column now uses `.statusBarsPadding().navigationBarsPadding()` (imports added). The main app was already fine — its Scaffold applies the insets. No other screen is shown outside a Scaffold.

## v3.7.0 — Usable, like theirs, no mistakes (2026-11-17, her orders while she sleeps: "Ayusin mo di usable ang app" / "Enhance mo naman" / "Itulad mo talaga sa kanila" / "Make no mistakes 100% copy lahat lahat")
- **Root causes found by reading the code (no guessing):** (1) onboarding GATED the whole app and its only escape was a small "Skip for now" link — a fresh install with no key felt locked; (2) the Agent had NO Stop — a hung provider left it frozen on "Thinking…" with the Send button disabled; (3) with no key, the Agent only showed an error line after tapping Send, with no path to fix it.
- **Stop is REAL, not just a button (found while re-checking before push):** a blocked `read()` on Dispatchers.IO ignores coroutine cancellation, so `AiClient.chat` now disconnects the socket when its Job is cancelled and rethrows as CancellationException — Stop ends a hung reply at once instead of waiting out the 120s read timeout.
- **Fixes:** onboarding now has a big, unmissable way out on EVERY step (step 0: "Skip setup — open the app →"; step 1: full-width "Skip — I'll add a key later"; step 2 already had Skip) — nothing gates the app anymore. The Agent shows a no-key banner (like Cursor/Codex) with a one-tap "Add API key" jump to Settings. The running chat request is now a tracked Job: a Stop button appears while thinking and cancels it ("Stopped." in chat, busy state cleared).
- **What "like theirs" means here, honestly:** Claude Code / Cursor / Codex all open the workspace first and ask for a key as a banner (now copied), and all have Esc/Stop on a running request (now copied). What is still NOT copied: Codex full-auto disk writes (her tap-gate rule) and cloud sandboxes (not a phone).

## v3.8.0 — 100% copy: the 9-app study, built (2026-11-17, her order "Tignan mo mabuti ang mga ito Claude Code / Cursor / OpenCode / OpenClaw / Harness / Hermess / Antigravity / Aistudio / Codex ng 1000x times para makuha mo ng 100%" + "Lahat kung ano meron sila kung ano kaya nilang gawin kung ano settings nila")
- **Study:** 9 products studied in parallel (one researcher each) — full feature + full settings inventories, 64 source URLs, 32-row gap map. Deliverable: REFERENCE-STUDY.md (kept locally, not shipped in the app). v3.8.0 builds the study's prioritized list, items 1–8, in order.
- **1. Plan mode:** a Plan chip beside Suggest/Auto. In Plan mode the system prompt forbids edits and the model returns a plan only (steps, files, risks). The reply renders as a plan card with Approve & Build (flips to Suggest and sends "Proceed with this plan") or Discard. Plans persist per session (SessionRecord.plan).
- **2. @-mentions + `/` slash menu:** type @ in the composer for a fuzzy file popup from the picked folder — picking a file attaches its contents to the next request. Type / for a menu of Skills (by name, toggles them) + local commands: /review, /compact, /fork, /export, /goal. The send flow was extracted from the Send button into sendPrompt() so Plan Approve & Build, /review and /compact reuse it exactly.
- **3. AGENTS.md rules:** AGENTS.md (CLAUDE.md fallback) is read from the picked folder when a folder is picked and injected into every prompt (capped at 6,000 chars). A "Rules loaded: AGENTS.md" line shows under the Agent subtitle; when missing, a one-tap "Create AGENTS.md" writes a starter template (tap-gated, needs a folder picked).
- **4. Checkpoints:** before Apply all writes files, and before Save overwrites a file, the current disk contents are snapshotted (in-memory list + prefs, last 20, 100KB/file cap). A "Checkpoint saved … — Restore" row sits under the plan card; /undo restores the latest checkpoint (tap-gated, writes back via ProjectStore). Honest limit: file restore only, no conversation rewind (no git on a phone).
- **5. Ask mode + /review:** a third mode chip, Ask (read-only — the prompt forbids edits, and Preview diff / Show plan are hidden in Ask and Plan modes). /review (slash command, or the Review button in the Files editor toolbar) reads the open/attached file and asks for P0/P1/P2 findings in "P0 - path:line - problem, fix" format; findings are parsed and rendered as tappable rows that open the file and jump the editor cursor to that line.
- **6. Context meter:** under the Agent subtitle — a progress bar + "≈N tokens · P% of ~128k window (estimate)", tokens estimated as chars/4 from system prompt + attached files + conversation. Tap Details for the breakdown. The 128k window is a documented estimate (providers don't publish one number per model); $ is shown only when a provider returns usage.
- **7. Effort selector:** Low / Medium / High chips beside the model picker (Antigravity picker / Codex reasoning effort, phone-sized). Low = temp 0.2 + max_tokens 1024, Medium = current defaults (temp 0.2, no max_tokens), High = temp 0.7 + max_tokens 8192. Stored in SettingsStore (KEY_EFFORT). AiClient.chat takes temperature/maxTokens params; the shared Json now has explicitNulls=false so an absent max_tokens is omitted (some providers reject explicit null); the vision branch uses the same values.
- **8. Session polish:** auto-titles from the first user message (first 40 chars, only while the title is still "Session N"); long-press a session chip to Rename / Fork (fork duplicates history + plan into a new session); a search field above the chips when more than one session exists.
- **Deliberately NOT in v3.8.0 (from the study):** MCP client (low phone value until @//rules land), voice, smart approvals (weakens the tap-gate promise), cloud agents / live preview / real terminal / worktrees (not buildable on a phone — see honest gaps).

## v3.9.0 — 100% of what's phone-buildable: Git + Live Preview (2026-11-17, her order "Sige gawin mong 100% katulad ng mga ito" + "Tignan mo mabuti... ng 1000x times")
- **Re-study:** the whole REFERENCE-STUDY.md was re-read end to end (all 32 gap-map rows + settings comparison + not-buildable list). Full plan in V39-PLAN.md. v3.9.0 builds every gap-map item that is still missing AND phone-buildable.
- **1. Git tab (gap #21 — Antigravity Review/VCS pane, Codex PR creation, Claude Code commit):** new tab Git (index 7; Settings moves to 9). Status compares the picked folder with the repo branch by git blob SHA-1 (computed on-device, `"blob <len>\0" + bytes`); Commit uses the GitHub Git Data API (blobs → tree on `base_tree` → commit → PATCH update ref); Branch list/create/switch; PR into the repo's default branch. Repo = the announce repo setting, token = the encrypted GitHub PAT — no new auth. **Safety:** a commit NEVER deletes (deleted files are listed only), binary files are skipped (extension list), files over 200 KB are skipped. AI message button drafts a conventional-commit message from the changed list. Nothing commits, branches or opens a PR without her tap.
- **2. Live Preview tab (gap #22 — AI Studio Build preview, Hermes preview rail, Cursor Design mode):** the study marked this "not buildable" because the agent cannot see a running page. The phone-sized equivalent: pick an .html file → the project is copied into `filesDir/preview/` (raw bytes, 2 MB cap) → Android WebView renders it (JS on, file access on, universal file access off). **"Ask AI to review this screen"** captures the WebView bitmap (`view.draw(Canvas)`), downscales it (1024px / JPEG 85, same as v3.3 vision) and sends it to the model as a vision call — the Agent sees the render and lists what to fix. That is the browser-subagent visual check, phone-sized.
- **3. Model parameters (gap #9 — AI Studio Run settings panel):** Settings → Provider & Model gains top-p and top-k fields (blank = provider default). `AiClient.chat` takes `topP`/`topK`; `AiReq` omits them when null (same `explicitNulls = false` pattern); the vision branch has conditional puts. Stored in SettingsStore (KEY_TOP_P / KEY_TOP_K).
- **4. Auto-compaction (gap #15 — Claude Code /compact + auto, OpenCode auto):** /compact already exists (v3.8). New: at 90%+ on the context meter, a "Context is almost full — Compact now" card appears (tap-gated), and a Settings → Agent toggle "Auto-compact when context is almost full" (default OFF) compacts once per crossing (guard flag, reset under 80%).
- **5. Usage / cost (gap #14):** the context-meter breakdown gains a session total line ("Session total ≈N tokens · M messages"). $ stays only when a provider returns usage.
- **Deliberately NOT in v3.9.0 (still not buildable on a phone):** cloud agents (needs a server DevxyzCode does not have), real terminal/builds (no JDK/SDK/gradle/node/git on a phone, SAF has no executable paths), worktrees (no git binary), full LSP, desktop keyboard shortcuts (no keyboard). Deferred, LOW priority: MCP client (HTTP-only subset), voice dictation, extra themes, smart approvals (weakens the tap-gate promise), image generation (out of pure-coding scope).

## v3.9.1 — "Gawan ng paraan" round, pure mobile controls only (2026-11-17, her orders "Gawan mo ng paraan" + "Pure mobile controls lang ah" + "Walang pc" + "Naka mobile Android lang ang mga users niyan")
- **Rule:** every control is tap / long-press / chips — no keyboard shortcuts, no right-click/hover flows, no PC-dependent steps anywhere. Full plan in V391-PLAN.md. Builds the remaining phone-buildable gap-map items.
- **1. Inline AI edit (gap #11 — Cursor Cmd+K, touch-first):** new "AI edit" button in the Files editor toolbar. Long-press selects text in the editor (native Compose selection); tap AI edit → inline card shows the scope ("Selected N chars" or "Whole file") + an instruction field + "Generate edit". One chat call returns ONLY the replacement in a fenced block; the result shows as a DiffUtil diff card with Apply / Discard. Apply replaces the selected range (or the whole file) in the editor and marks it dirty — still NOT written to disk until Save is tapped. FilesScreen gains baseUrl/apiKey/model params.
- **2. Voice (gap #19):** mic button next to Send in the Agent composer — system speech recogniser via `StartActivityForResult` + `RecognizerIntent` (free-form, device locale); recognised text is appended to the input. No RECORD_AUDIO permission needed in-app; if the phone has no recogniser, the app says so instead of crashing. Assistant message cards gain a Listen / Stop button — `TextToSpeech` held in AgentScreen (init once, `UtteranceProgressListener` clears the speaking state on done/error, shutdown on dispose); fenced code blocks are stripped before speaking.
- **3. Per-hunk accept/reject (gap #28):** the single-file diff card groups `pendingDiff` into hunks (maximal runs of non-UNCHANGED lines, `splitHunks`); each hunk gets a tap toggle Accept/Reject (all accepted by default; state resets when a new diff arrives, keyed on pendingCode). The +N/−M counts reflect accepted hunks only; Apply reconstructs the file with `applyHunks` (UNCHANGED always kept; accepted hunks contribute ADDED lines, rejected hunks REMOVED lines). Plan cards keep per-file accept (already exists).
- **4. Completion notifications (gap #32):** new `Notify.kt` (`Notifier` object, NotificationCompat from androidx.core, framework icon, channel created in MainActivity.onCreate). Settings → Agent gains a "Notify when a reply finishes" switch (default OFF; SettingsStore KEY_NOTIFY_DONE); turning it ON requests POST_NOTIFICATIONS once (API 33+). MainActivity tracks foreground state via `LocalLifecycleOwner` + `LifecycleEventObserver`; AgentScreen posts "DevxyzCode — reply ready" (first 120 chars) when a send finishes while the app is in the background and the toggle is ON. No notification while she is looking at the screen. No new dependencies (NotificationCompat, lifecycle, TTS, recogniser intent are framework / already-transitive).

## v3.9.2 — Claude Code UI on the Agent tab (2026-11-17, her order "Ayusin mo dapat UI ng Claude Code na lang", with her phone video of the Agent screen)
- **Scope: AgentScreen ONLY.** The other tabs (Files, Runs, Skills, Messages, Terminal, Announce, Git, Preview, Settings) keep their current look; the bottom tab bar stays. Source: REFERENCE-STUDY.md §1 (Claude Code TUI anatomy) — single scrolling transcript, multi-line composer at the bottom, status bar showing the permission mode, `⏺ Tool(args)` lines with `⎿` results, `/context` usage, effort levels. Full plan in V392-PLAN.md.
- **The look:** near-black transcript background (#0F0F0F), monospace text, Claude orange #D97757. Header is a compact status line (`✻ DevxyzCode · <model>` + mode line `⏸ suggest mode on` / `⏵⏵ auto mode on` / `⏸ plan mode on` / `⏸ ask mode on`). User messages are `>` lines (orange prompt); assistant replies are plain text; every action is a `⏺` tool line (Preview diff, Show plan, Run in Terminal, Copy reply, Save as Skill, Listen) — same taps, same conditions. Mode / model / effort / session chips are small mono chips (active = orange border + text). Context meter is a status line + thin orange bar. Diff preview, plan card, review findings, provider setup card, @-mention popup, slash menu, compact-confirm card are dark bordered panels. Composer is a bordered box with an orange `>` prefix; Send is an orange button (dark text), "✻ Thinking…" while working; mic + Stop dim. Empty state: big orange `✻` + suggestions as `> …` lines.
- **Deliberately dark:** the Agent screen stays dark like Claude Code's TUI whatever the app theme is (System/Dark/Light) — that is the reference's own look, not a theme bug.
- **Nothing removed:** every Agent control still works exactly as before (modes, context meter + breakdown, auto-compact, sessions search/new/delete/rename/fork, model chips, effort, skills line, attached files, all message actions, diff preview + per-hunk toggles, plan card, voice, notifications).
