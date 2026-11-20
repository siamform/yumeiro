# Yumeiro user test program

A one-page brief the owner can share with people who want to help test Yumeiro.

## What it is

Yumeiro is tested every day on virtual phones and TVs in the cloud. Some things can only be checked
on a real device: headphones, a real TV remote, your home Wi-Fi, a phone with little memory, your own
music or manga sources. This program asks you to try a few of those on your own phone or TV and tell
us what happened.

## What is tested

Short tasks such as:

- music keeps going and the Music home keeps loading new shelves;
- music pauses when you unplug your headphones;
- videos and YouTube play and switch quality;
- downloads finish at a good speed and appear in your gallery;
- Yumeiro works with your TV remote, behind a VPN, on a low-memory phone;
- new in 1.9.0: profiles with a PIN, private downloads, hold for 2x, public Telegram channels and script repos.

The build to test now is **Yumeiro 1.9.0**. A few things are known to be
imperfect (for example YouTube sometimes asks "Sign in to confirm you're not a bot"); the list is in the
[CHANGELOG](../CHANGELOG.md).

Start with the six **mandatory** tasks (U-29, U-26, U-3, U-27, U-24, U-20). The full list, with plain steps, is the **Yumeiro users** checklist at the top of
[OWNER-MANUAL-TESTS.md](OWNER-MANUAL-TESTS.md#yumeiro-users-checklist).

## How long it takes

Each task takes 5–15 minutes. Pick any you can do; you don't need to do them all.

## What you need

An Android phone (Android 9 or newer) or an Android TV (Android 7 or newer), the 1.9.0 release candidate the owner sends you,
and for some tasks headphones, a TV remote or a VPN app.

## How to report

For each task, send:

1. The task number (for example **U-3**).
2. **Passed** or **Failed**, and one sentence on what you saw.
3. A screenshot or a short screen recording if something looked wrong.
4. The app version: Yumeiro → Settings → About (the version line).
5. Your phone or TV model and Android version.

If the app crashed or froze: Yumeiro → Settings → Crash reports → open the report → Share, and attach it.

Send it to the person who invited you, or open an issue at
[github.com/siamform/yumeiro/issues](https://github.com/siamform/yumeiro/issues) with the task number in the title.

## Privacy

- Don't send passwords, account names or anything from your own accounts.
- Screenshots may show your music, videos or playlists. Crop or blur anything you don't want to share.
- Your report is used only to fix Yumeiro. It is not published.

## Thank you

Every report, even "it worked", helps.
