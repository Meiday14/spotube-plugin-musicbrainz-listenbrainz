# Validation — November 10, 2026 — 0.1.3

- Target: Spotube Android 6.0.0 stable, plugin API 0.1.0.
- Kotlin 2.4.21, JavaScript ES5, Zipline/QuickJS 1.28.0, JDK 21, Gradle 9.4.1.
- Executed: BRAINZ_LIVE_TESTS=1 gradlew.bat jvmTest.
- Result: 40 passed, zero failures or errors.
- 11 metadata/account tests, including a 65-track playlist, order/duplicates, credential validation/restoration, request authorization, scrobbling, descending removal indices, account isolation and recommendations.
- 11 YouTube search tests: real response parsing, continuations, token isolation, persistent metadata, safe saved checks, Brainz matching before writes, duplicate preservation, failure-before-mutation guarantees, timeouts and cancellation.
- 11 sign-in lifecycle tests: native Back and fresh retry, background/null/paused scripts, concurrent calls, invalid tokens, 20-second validation timeout, stale messages, session timeout, closure during validation, early closure and cancellation preserving a saved login.
- 2 tests running the actual token form JavaScript inside QuickJS.
- 1 generated-module QuickJS compilation test.
- 1 real .smplug loading test through the Zipline bridge, including account restoration, all services, YouTube song search and saved-status checks, login, native ownership-error cancellation and subsequent retry.
- 1 read-only public API probe for a ListenBrainz playlist, YouTube Music search for zaho allo, continuation results and MusicBrainz album tracks.
- 2 host manifest tests using Spotube 6.0.0 enum decoders.
- All 10 packaged module SHA-256 checksums matched the Zipline manifest.

The user confirmed 0.1.1 installation and playlist retrieval on Android. The new 0.1.3 search flow still needs an Android device retest. Account mutations were tested with simulated services only; no personal token was used.
