# Yumeiro FAQ

<details>
<summary><b>Why does Google Play Protect ask to scan the app?</b></summary>

Play Protect shows that prompt for sideloaded apps whose signing key it has not seen often yet — it is about the
app's reputation, not its code. Apps that have been installed by many people, or whose developer has registered
the signing key with Google's Android developer verification, stop getting the prompt. You can safely tap
**Scan app** or **Install without scanning**. Always check that the APK came from this repository's Releases page.
</details>

<details>
<summary><b>A stream says "Access denied (HTTP 403)". What now?</b></summary>

Yumeiro already retried it for you: with browser headers, the site and the host as referer, no referer,
a media-player and a VLC user agent, and the host's own web-player headers. If it still fails, the link's token
has expired (refresh the playlist or get a fresh link), the host needs a Cookie (add it under Headers & DRM),
or the stream is limited to certain countries. Yumeiro does not bypass geo-blocking or access controls.
</details>

<details>
<summary><b>Do I need an account?</b></summary>

No. Yumeiro needs no account. YouTube and YouTube Music work without signing in; signing in to YouTube (to get past
"confirm you're not a bot") or to Telegram (to watch private chats you are in) is optional and stays on your phone.
</details>

<details>
<summary><b>Is it safe? What data does it collect?</b></summary>

No analytics, no ads, no tracking. Your history, playlists and settings stay on your device. The optional instant
notices use Firebase push through the Yumeiro Cloud Worker with no account and no IDs shared with anyone else
(see [PRIVACY.md](../PRIVACY.md)); you can switch each kind off.
Every update is verified against the official signing certificate before it is installed.
</details>

<details>
<summary><b>How do I know my APK is the real one?</b></summary>

Official releases are signed with one key; its fingerprint is in [SECURITY.md](../SECURITY.md).
Re-signed copies are detected by the app and marked **Unofficial build** in Settings → About.
</details>

<details>
<summary><b>Where do online videos come from?</b></summary>

From CloudStream-format extensions that *you* choose to install. Yumeiro ships only one public-domain extension
(Internet Archive). Only add extensions for content you have the right to stream.
</details>

<details>
<summary><b>Notifications arrive late or not at all. What can I do?</b></summary>

Open Settings, Background, **Keep notifications on time**. It checks notifications, background checks, battery
optimisation and Android's background restrictions, then shows the steps for your phone maker (auto-start,
sleeping apps, app launch) with a button that opens the right page.
</details>

<details>
<summary><b>The app crashed. Where is the report?</b></summary>

Settings, **Crash reports**. Reports stay on your phone (web addresses, e-mails and tokens are removed). Tap
Share to send one with your bug report.
</details>

<sub>Back to the [README](../README.md).</sub>
