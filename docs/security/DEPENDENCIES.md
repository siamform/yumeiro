# Dependency vulnerability scan

Scan date: 2026-11-07. Method: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
(272 resolved Maven coordinates) queried against the OSV batch API (`https://api.osv.dev/v1/querybatch`).

| Library | Resolved | Advisory | Severity | Pulled in by | Fix |
|---|---|---|---|---|---|
| org.jsoup:jsoup | 1.22.2 | GHSA-65r4-943x-97jj (XML namespace nesting memory exhaustion) | High | CloudStream library 4.8.0 | Constrained to 1.23.2 |
| org.jsoup:jsoup | 1.22.2 | GHSA-pmhh-3w7g-xqp8 (Cleaner raw-text elements) | Moderate | CloudStream library 4.8.0 | Constrained to 1.23.2 |
| com.google.guava:guava | 33.3.1-android | GHSA-xxph-c9ww-hj94 (deserialization memory allocation) | High (not reachable: app does not Java-deserialize untrusted data) | Media3 1.11.1 | Constrained to 33.7.2-android |

The fixes are `constraints {}` in `app/build.gradle.kts`, so they don't change any direct dependency.

## Maintenance watch-list (no known CVE)

- `io.coil-kt:coil` 2.7.0: Coil 3.x (`io.coil-kt.coil3`) supersedes it. The 2.x line only gets critical fixes. Migrating means API changes, so it is out of scope for a security PR.
- `com.github.Blatzar:NiceHttp` 0.4.18: low release cadence, single maintainer, and the CloudStream extension ABI depends on it. Keep it aligned with upstream CloudStream.
- `com.github.recloudstream.cloudstream:library` 4.8.0: tracks upstream. Re-scan whenever it is bumped, because it brings in jsoup, jackson and rhino transitively.

Dependabot (`.github/dependabot.yml`) and the CI `dependency-review` job keep this current. Re-run the scan before each release.
