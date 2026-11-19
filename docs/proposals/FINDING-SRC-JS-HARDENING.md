# Finding: JS sources have no time limit and can reach the local network

Stream: D-sources. Severity: high (security and hang), small fix. Repo `r15-stability` head
1187427a. Paths relative to `app/src/main/java/dev/asfakul/yumeiro` unless they start with
`app/`.

## 1. No evaluation time limit

Where:
- `sources/js/JsEngine.kt:79-81` creates the runtime and sets `memoryLimit = 64 MB` and
  `maxStackSize = 2 MB`, but never sets `evaluationTimeoutMillis`.
- `sources/js/JsEngine.kt:98-105` `call()` runs `q.evaluate(...)` under a `mutex`, then
  pumps the job queue up to 50 times. A script that loops (`while(true){}`) never returns from
  `evaluate`, so the mutex is held forever and every later call to that source waits forever.
  Coroutine cancellation cannot stop native code that is busy.
- Two more runtimes for extension helpers have no limit either:
  `app/src/main/java/eu/kanade/tachiyomi/network/JavaScriptEngine.kt:17` and
  `app/src/main/java/app/cash/quickjs/QuickJs.kt:50`. Those run inside DEX extensions, which
  already have full app rights, so for them this is about hangs, not security.

What the library offers: quickjs-kt v1.0.18 (the version Yumeiro uses,
`gradle/libs.versions.toml:147`, `app/build.gradle.kts:374`) has
`var evaluationTimeoutMillis: Long` and `fun interruptEvaluation()`
(dokar3/quickjs-kt tag v1.0.18, commit 673a6df9,
`quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt:124-138`; JNI implementation
`quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt:285-293`). Going over the limit
throws `QuickJsInterruptedException` (`QuickJsException.kt:33-36`). It stops busy loops too.

Fix:
- `JsEngine.runtime()`: `evaluationTimeoutMillis = LOAD_TIMEOUT_MS` (20 s) while the runtime and
  source load, then `CALL_TIMEOUT_MS` (60 s) for each call. The limit is wall clock per
  `evaluate` and covers the HTTP calls a source makes inside it (see "Time limit values").
- On a stop, the runtime is closed and `js = null`, so the next call starts clean.
- On `QuickJsInterruptedException`: throw a friendly `IllegalStateException("This source took
  too long and was stopped")`.
- Same 10 s limit in the two helper runtimes.

Prior art: Nuvio sets the timeout on its Android runtime
(`androidFull/.../PluginRuntime.android.kt:9-21`, NuvioStreaming 1.1.0-beta.2, 81f836d4) and
uses a 60 s plugin budget (`PluginRuntime.kt:40-41`).

## 2. No guard against private network addresses (SSRF)

Where:
- `sources/js/JsBridge.kt:120-140` `http()` takes `url = a.s(2)` from the script and runs
  `Request.Builder().url(url)` (`:133`) then `newCall(req).execute()` (`:136`) with the
  shared client (`NetworkHelper(context).client`, `JsEngine.kt:74`). Redirects are followed.
- The response body, headers and final URL go back to the script (`:137-143`), and the script
  can send them anywhere. So a Mangayomi source can read `http://192.168.1.1/` (router),
  `http://127.0.0.1:8090/` (TorrServer), a NAS, or a home Jellyfin server, from the user's
  phone, and leak it.
- Other fetches in the same package (`JsSources.kt:147,162`, `JsProviders.kt:176`) use URLs
  the user typed (repo index) or the index lists. They stay unguarded on purpose: a user may host
  a repo on the home network, and device tests (`UniversalAddEcosystemsDeviceTest`) do so.
- The app already has the address test: `network/SmartDns.kt:157-165` `isBogon()` (0/8, 10/8,
  127/8, 100.64/10, 169.254/16, 172.16/12, 192.168/16, IPv6 loopback, link-local, site-local,
  fc00::/7). It is not used for scripts.

Fix:
- New `network/PublicOnly.kt`: an OkHttp network interceptor that reads
  `chain.connection()?.route()?.socketAddress?.address` and throws
  `IOException("Blocked: private address")` when `SmartDns.isBogon(address)` is true, or when
  the address is multicast. A network interceptor runs on every hop after DNS, so it also covers
  redirects, IP literals (OkHttp does not call `Dns` for IP literals) and DNS rebinding. Also
  refuse schemes other than http and https before building the request.
- `JsBridge` adds the interceptor itself (`client.newBuilder().addNetworkInterceptor(PublicOnly)`),
  so every bridge, whatever client it gets, is guarded. Only script-made requests pass the bridge.
- Cap response size at 5 MB (read through `source().peek`/limit) so a script cannot fill memory.
- Do not apply this guard to user-added home servers (Jellyfin, SMB), which are meant to be
  local; they do not go through `JsBridge`.

Prior art: the Vega app checks every provider URL and every redirect against private ranges
(`src/lib/sandbox/urlGuard.ts`, 129 lines, vega-app v5.3.3, 6f2d7579; read only, licence is
non-commercial).

## Tests that fail before the fix

JVM (Robolectric, `app/src/test/java/dev/asfakul/yumeiro/sources/js/`):
- `JsBridgePrivateNetworkTest` (as built): `MockWebServer` listens on loopback. `handle("http_get")`
  to the server address, to `localhost`, and to a public-looking name whose DNS answer is the
  loopback address (rebinding) must return status 0 and the server must get no request. Today all
  three return 200. Redirect hops use the same network interceptor, so they are covered by design;
  a public-to-private redirect cannot be built without a public server, so it is not a unit test.
  Non-http schemes are already refused by OkHttp (`Request.Builder.url` throws), so no test.
- `JsTimeLimitGuardTest` (source check, like the existing
  `JsBridgeExtractorGuardTest.kt:14-20`): reads `JsEngine.kt` and asserts
  `evaluationTimeoutMillis` is set and `interruptEvaluation` is used on timeout. Fails today.

Device (`app/src/androidTest/.../JsEngineTimeoutDeviceTest`, api33-lowram and api36; QuickJS
native code is Android-only in this project, so the real runtime test must run on a device):
- `JsTimeLimitDeviceTest`: a fake source whose `getPopular(1)` is `while(true){}` must fail within
  90 s (limit 60 s, see below) and `getPopular(2)` on the same engine must then work. The two
  extension helper runtimes must stop `while(true){}` within 30 s (limit 10 s).

Time limit values (as built): the quickjs-kt deadline is wall clock from the start of one
`evaluate` (`quickjs/native/common/quickjs_interrupt.c:79`), and a Mangayomi call runs all its
HTTP requests (synchronous bindings) inside that one `evaluate`. So 5 s would break normal
sources. As built: 20 s for loading the source, 60 s per call (Nuvio uses the same 60 s budget),
10 s for the extension helper runtimes, which have no network bindings.

## Size

About 120 lines of code and 150 lines of tests, 2 commits:
1. `PublicOnly` + JsBridge wiring + JVM tests.
2. Timeouts in the three runtimes + device test + source-guard test.
