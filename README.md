# DevxyzCode

Sariling AI coding assistant ni Jepong for Android — parang Claude Code / Cursor / OpenCode / OpenClaw / Hermes / Harness, sa Devxyz branding.

**v3.1.0 — ano ang nadagdag (lahat ng kaya):**
- Terminal tab (hindi sa chat): sandboxed shell — basic shell commands lang, 30s timeout, output capped sa 20,000 chars, destructive commands (rm -rf /, su, reboot) refused; ang AI nag-suggest lang ng ```sh block, ikaw ang magta-tap ng Run in Terminal
- Provider self-setup: i-paste sa chat ang snippet na may base URL + API key, at ia-apply ng app (Coarena, APInex, o custom provider)
- Coarena preset sa Settings: free models (gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017, claude-fable-5)

**v3.0.0 — ano ang nadagdag (lahat ng kaya):**
- Autocomplete sa editor (words mula sa file + keywords), on-device diagnostics + find-symbol
- Multi-session Agent (parallel, may sariling history bawat session), background scheduled tasks na may notification + saved result, Telegram bot gateway (AI draft, ikaw pa rin ang magta-tap ng Send), auto-save ng applied code bilang Skill (default OFF)

**Hindi pa rin kaya — at bakit:**
- Builds sa phone mismo: Terminal ay basic shell lang — walang JDK / Android SDK sa phone, at walang executable path ang SAF folders (gradle/npm/node/python/git, hindi pa rin kaya)
- Full LSP server: same reason; diagnostics-lite + find-symbol ang kaya
- WhatsApp control: walang official personal-account API

**v2.1.0 — ano ang nadagdag:**
- Branded app icon (cyan `</>` sa dark navy) at branded splash screen — walang white flash pag bukas

**v2.0.0 — ano ang kaya niya:**
- Buksan ang project folder, mag-browse + mag-search ng files, mag-edit + mag-save ng code, syntax-highlighted preview
- Makipag-chat sa AI (BYOK — ikaw ang maglalagay ng API key sa Settings, naka-encrypt na)
- I-attach ang hanggang 3 files bilang context
- Diff preview bago mag-Apply: makikita muna ang +/- lines, Approve o Discard — ikaw pa rin ang magta-tap ng Save
- Skills (OpenClaw-style, reusable instructions) + Save-as-Skill mula sa reply, at persistent Memory (Hermes-style, laging kasama sa chat)
- Scheduled tasks (OpenClaw Automations) at Harness-style pipeline steps na may evidence-based run records

**Ano ang HINDI pa rin niya ginagawa — by design:**
- Hindi siya nagpapatakbo ng shell commands nang mag-isa (ang Terminal ay hiwalay na tab — ikaw ang magta-tap ng Run), at hindi siya nagsusulat ng files nang hindi mo ina-approve
- Walang autocomplete, walang LSP, walang messaging gateway, walang background agent

Buong detalye at parity table: `SPEC.md`.

**Build:** GitHub Actions — `.github/workflows/android.yml` sa repo root.
