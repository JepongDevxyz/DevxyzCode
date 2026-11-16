# DevxyzCode

Sariling AI coding assistant ni Jepong for Android — parang Claude Code /
Cursor / OpenCode / OpenClaw / Hermes / Harness, sa Devxyz branding.

**v2.0.0 (the 100% release) — ano ang kaya niya:**
- Buksan ang project folder, mag-browse + mag-search ng files, mag-edit + mag-save ng code, syntax-highlighted preview
- Makipag-chat sa AI (BYOK — ikaw ang maglalagay ng API key sa Settings, naka-encrypt na)
- I-attach ang hanggang 3 files bilang context
- Diff preview bago mag-Apply: makikita mo muna ang +/− lines, Approve o Discard — ikaw pa rin ang magta-tap ng Save
- Skills (OpenClaw-style, reusable instructions) + Save-as-Skill mula sa reply, at persistent Memory (Hermes-style, laging kasama sa chat)
- Scheduled tasks (OpenClaw Automations) at Harness-style pipeline steps na may evidence-based run records

**Ano ang HINDI pa rin niya ginagawa — by design:**
- Hindi siya nagpapatakbo ng shell commands o builds nang mag-isa, at hindi siya nagsusulat ng files nang hindi mo ina-approve
- Walang autocomplete, walang LSP, walang messaging gateway, walang background agent

Buong detalye at parity table: `SPEC.md`.

**Build:** GitHub Actions — `.github/workflows/android.yml` sa repo root.
