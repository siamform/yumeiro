# Security policy

## Supported versions

Only the latest release on [GitHub Releases](https://github.com/siamform/yumeiro/releases)
receives fixes. The app updates itself in place and verifies every update APK against the
official signing certificate before installing it.

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting instead:
[Report a vulnerability](https://github.com/siamform/yumeiro/security/advisories/new).

You will get a reply within 7 days. Please include the app version, Android version, steps to
reproduce and the impact you see.

## Scope

In scope: the Yumeiro app, its in-app updater, the release workflow and the `market`, `streams`
and `extensions` branches the app reads from. Out of scope: third-party playlists, stream hosts
and extensions (report those to their authors), and issues that need a rooted or already
compromised device.

## Disclosure

We confirm the issue, fix it in a release, then publish a GitHub Security Advisory crediting you
(unless you prefer otherwise). Please give us 90 days before public disclosure.

## Verifying a download

Official APKs are signed with one key. Its SHA-256 certificate fingerprint is:

```
2e:cd:fd:04:f3:b2:d1:28:05:01:33:4a:5c:24:7d:71:29:ba:fa:2b:5a:43:48:47:28:9d:fc:06:9a:41:3f:6b
```

Check it with `apksigner verify --print-certs yumeiro.apk`. Anything else is not an official build.

Every release also carries `SHA256SUMS.txt`, generated in CI next to the APK:

```bash
sha256sum -c SHA256SUMS.txt
```

