# tdlib-bin

TDLib engine files for Yumeiro's Telegram sign-in (F-02, ADR-052). Built by `.github/workflows/tdlib-build.yml`
(run 44871959357) from https://github.com/tdlib/td at the commit in `TD_COMMIT` (TDLib 1.8.70). Licence: Boost
Software License 1.0 (`docs/licenses/TDLib-BSL-1.0.txt` on the main branches). OpenSSL 3.5.9 (Apache-2.0) is linked
in statically.

The app downloads `<abi>/libtdjsonjava.so.gz` from a pinned commit of this branch and refuses it unless both SHA-256
values and the size match the pins in `TdEngine.kt`. Never rewrite this branch: old app versions pin old commits.
