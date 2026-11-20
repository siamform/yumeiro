# Website update for 1.9.0 (prepared, not pushed)

`yumeiro-web-v1.9.0.patch` is the change for siamform/yumeiro-web: the changelog highlight
(`lib/changelog.ts`) and the /changelog page description move from v1.8.1 to v1.9.0.
It is prepared on a local branch only. It is pushed to the website repository only after the
owner's go for the 1.9.0 release (§11/§12 of the final-test prompt), because the page would
otherwise announce a version that is not published.

Apply after the go: `git am docs/website/yumeiro-web-v1.9.0.patch` in a clone of yumeiro-web, then open a PR.
Checked: `git apply --check` against yumeiro-web c9a73fc (v1.8.1 highlight) passes; 2026-11-20 the Airing alerts and Last.fm lines were reworded so they do not claim the Yumeiro Cloud update is live (string-only change).
