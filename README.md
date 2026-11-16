# DevxyzCode

Sariling AI coding assistant ni Jepong for Android — parang Claude Code /
Cursor / OpenCode / OpenClaw / Hermes / Harness, sa Devxyz branding.

**v3.1.1 — repair ng v3.1.0 (nabura ng push ang 2 dependencies + editor UI):**
- Ibinalik ang splash-screen + WorkManager dependencies (kaya bumagsak ang CI build ng v3.1.0) at ang v3 editor UI: autocomplete, on-device diagnostics, find-symbol, at syntax-highlight preview — lahat ng ito, nasa main na ulit nang buo.

**v3.1.0 — ano ang nadagdag:**
- **Terminal tab (hindi sa chat)** — sariling tab para sa shell commands (`ls`, `echo`, `cat` gumagana; gradle/npm/node/python/git wala sa phone kaya "command not found" sila nang totoo). May "Run in Terminal" button sa ```sh blocks ng AI — ikaw pa rin ang magta-tap.
- **Provider self-setup** — i-paste mo lang ang setup ng provider (tulad ng Coarena "Copy for your agent") sa Agent chat, siya na bahala mag-set up: base URL + key + model, Apply mo lang. Hindi pinapadala ang key sa AI, at naka-encrypt ang pag-save. May Coarena preset na rin sa Settings (free models: gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017; claude-fable-5 kailangan ng paid balance).

**Hindi pa rin kaya — at bakit:**
- Builds sa phone mismo: walang JDK / Android SDK sa phone, at walang executable path ang SAF folders — Terminal pang-basic shell lang
- Full LSP server: same reason; diagnostics-lite + find-symbol ang kaya
- WhatsApp control: walang official personal-account API

**v3.0.0 — ano ang nadagdag (lahat ng kaya):**
- Autocomplete sa editor (words mula sa file + keywords), on-device diagnostics + find-symbol
- Multi-session Agent (parallel, may sariling history bawat session), background scheduled tasks na may notification + saved result, Telegram bot gateway (AI draft, ikaw pa rin ang magta-tap ng Send), auto-save ng applied code bilang Skill (default OFF)

**v2.1.0 — ano ang nadagdag:**
- Branded app icon (cyan `</>` sa dark navy) at branded splash screen — walang white flash pag bukas

**v2.0.0 — ano ang kaya niya:**
- Buksan ang project folder, mag-browse + mag-search ng files, mag-edit + mag-save ng code, syntax-highlighted preview
- Makipag-chat sa AI (BYOK — ikaw ang maglalagay ng API key sa Settings, naka-encrypt na)
- I-attach ang hanggang 3 files bilang context
- Diff preview bago mag-Apply: makikita mo muna ang +/− lines, Approve o Discard — ikaw pa rin ang magta-tap ng Save
- Skills (OpenClaw-style, reusable instructions) + Save-as-Skill mula sa reply, at persistent Memory (Hermes-style, laging kasama sa chat)
- Scheduled tasks (OpenClaw Automations) at Harness-style pipeline steps na may evidence-based run records
- Announce tab (v3.2): mag-connect sa GitHub gamit ang sariling Personal Access Token (naka-encrypt), tapos mag-post ng Issue o mag-publish ng Release sa kahit anong repo — may AI Draft pa ng announcement text. Walang pino-post nang hindi mo tina-tap
- AI Studio preset (v3.2): Google Gemini API (aistudio.google.com/apikey) — gemini-3.8-flash at iba pa, BYOK din. Antigravity: walang public API kaya hindi siya puwedeng idagdag bilang provider; yung agent-style coding niya, ginagawa na ng Agent tab
- Multi-file plan + vision (v3.3): sagot ng AI na maraming files, may diff preview bawat isa, isang tap lang ang Apply all (bago at lumang files); mag-attach ng image at makikita ito ng Gemini model. Validation = buksan ang mga binagong file sa Files/editor — hindi kayang makita ng Terminal ang project folder (SAF), kaya walang terminal build/validate doon

**Ano ang HINDI pa rin niya ginagawa — by design:**
- Hindi siya nagpapatakbo ng shell commands o builds nang mag-isa (Terminal tab lang, ikaw ang magta-tap ng Run), at hindi siya nagsusulat ng files nang hindi mo ina-approve

Buong detalye at parity table: `SPEC.md`.

**Build:** GitHub Actions — `.github/workflows/android.yml` sa repo root.
