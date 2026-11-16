# DevxyzCode Privacy Policy

**Last updated: 2026-11-17**

DevxyzCode is an AI coding app for Android. This policy explains what the app does with your data.

## What the app stores on your device

- **Your API key** for your chosen AI provider (for example APInex, Coarena, or Google AI Studio). It is stored encrypted on your device (Android EncryptedSharedPreferences) and is only sent to the AI provider's API base URL you configured.
- **Your Telegram bot token** (if you connect a bot) — stored encrypted on your device, only sent to the Telegram Bot API.
- **Your GitHub Personal Access Token** (if you connect GitHub) — stored encrypted on your device, only sent to the GitHub API.
- **Your code, chats, skills, scheduled tasks, and settings** — stored locally on your device. Nothing is uploaded to any DevxyzCode server, because there is none.

## What the app sends over the network

- **Chat messages and attached files** go to the AI provider you chose, at the base URL you configured, so the AI can answer. That provider's own privacy policy applies to what you send it. Do not send secrets or sensitive code to a provider you do not trust.
- **GitHub posts** (Issues / Releases) go to the GitHub API only when you tap Post.
- **Telegram messages** go to the Telegram Bot API only when your bot is connected and you tap Send, or for scheduled tasks you created yourself.

## What the app does NOT do

- No analytics, no ads, no tracking, no crash reporting to any third party.
- No account, no sign-up, no server-side storage. There is no DevxyzCode account to delete — uninstalling the app removes its local data.
- The app never writes files or runs commands without your tap, except scheduled tasks you created yourself.

## Permissions

- **Notifications (Android 13+):** only to tell you when a scheduled task you created finishes.
- **Storage:** the app opens folders you pick (Storage Access Framework). It can only read and write inside folders you chose.

## Changes

If this policy changes, the new version will be published in this repository.

## Contact

Questions: open an issue at https://github.com/JepongDevxyz/DevxyzCode/issues
