# DevxyzCode

Sariling AI coding assistant ni Jepong for Android — parang Claude Code /
Cursor / OpenCode, sa Devxyz branding.

**v1.0.0 — ano ang kaya niya:**
- Buksan ang project folder, mag-browse + mag-search ng files, mag-edit + mag-save ng code
- Makipag-chat sa AI (BYOK — ikaw ang maglalagay ng API key sa Settings)
- I-attach ang bukas na file bilang context, at i-Apply ang code na iminungkahi ng AI — ikaw pa rin ang magta-tap ng Save
- I-record ang build/test runs (Runs tab, Harness-style checklist)

**Ano ang HINDI pa niya kaya sa v1:**
- Hindi siya nagpapatakbo ng shell commands o builds nang mag-isa
- Hindi siya nagsusulat ng files nang hindi mo ina-approve
- Walang autocomplete at walang LSP

Buong detalye: `SPEC.md`.

**Build:** GitHub Actions — tingnan ang `.github/workflows/android.yml`
sa `android/`. Ang workflow file ay kailangang i-commit via GitHub web UI.
