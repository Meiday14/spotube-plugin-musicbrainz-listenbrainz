# Validation — consolidated public 0.1.0

Target: Spotube Android 6.0.0, plugin API 0.1.0. Functionality is retained from development prototype 0.1.5; only version metadata and documentation changed for this release.

- Kotlin 2.4.21, JavaScript ES5, Zipline/QuickJS 1.28.0, JDK 21.
- Command: BRAINZ_LIVE_TESTS=1 gradlew.bat jvmTest.
- 54 tests: metadata/account behaviour, playlist counts/cache, YouTube search/matching, sign-in lifecycle, actual form JavaScript, host manifest decoding, QuickJS compilation and the packaged Zipline bridge.
- Live probes use public read-only APIs. Personal account mutations use simulated services.
- Android playlist playback, song search and additions were confirmed during prototype testing. Recent sign-in/counter changes require device confirmation.

The build and test result for this consolidated release is recorded by GitHub Actions; the local package is checked against its Zipline module checksums before delivery.
