# CodeQL on PR #40 (2.1.0): 17 new alerts, reviewed, not dismissed

The CodeQL check on PR #40 failed with 17 new alerts (6 high, 11 medium). Main is not protected, so the check does not
block the merge. The agent read each one; none is a new hole in user data. They are left open: dismissing them is the
owner's call (GitHub > Security > Code scanning).

| Alerts | Rule | File | Reading |
|---|---|---|---|
| 92, 93, 105-108 (high) | weak / potentially weak crypto | sources/ecosystem/ScriptCrypto.kt | crypto-js shim for third-party extension scripts. Sites use MD5, SHA-1, TripleDES; the shim decodes their data and never protects user data. By design. |
| 97-104 (medium) | WebView content:// access | ui/screens/stream/PageKeep.kt | PageKeep only keeps the WebView made in PageStreamFinderDialog.kt:474-475, which sets allowFileAccess = false and allowContentAccess = false. CodeQL does not follow the field. False positive. |
| 94, 96 (medium) | no certificate pinning | online/telegram/td/TdEngine.kt, stream/torrent/TorrentEngine.kt | Native library download from a fixed URL, checked against a pinned SHA-256 before it loads. |
| 95 (medium) | no certificate pinning | stream/TeraBoxApi.kt | Third-party share host; no user secret sent; pinning would break when TeraBox rotates certificates. |
| 87-91 | (already open on main) | EpisodeStills, Upcoming, PlaylistStore temp file, CloudRecapture | Not new in 2.1.0. PlaylistStore temp file is in java.io.tmpdir = the app's private cache on Android. |

Owner action: dismiss with these reasons, or ask for a code change (for example an explicit
allowContentAccess = false inside PageKeep so CodeQL can see it).
