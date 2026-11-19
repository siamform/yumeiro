# Server test, final (1.9.0 release candidate)

End-to-end checks of every server-backed feature (master prompt section 7). One row per request: what was
sent, what came back, what was expected, the result and where (local Worker test, staging, production read-only,
emulator). The Worker fix is not deployed until the owner's go; tests before the go run against the local Worker
(Miniflare / wrangler dev) and the production endpoints only read.

| ID | Service | Case | Request | Response | Expected | Result | Where |
|---|---|---|---|---|---|---|---|
