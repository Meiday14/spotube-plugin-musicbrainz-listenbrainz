# Development

Requires JDK 21. Use the Gradle wrapper from the repository root:

- Windows: ./gradlew.bat jvmTest
- Linux/macOS: sh ./gradlew jvmTest

The test task builds and loads the actual plugin through Zipline. The installable output is build/distributions/plugin-production.smplug. Set BRAINZ_LIVE_TESTS=1 to include public read-only API probes. Account mutations use simulated services. See [VALIDATION.md](VALIDATION.md) for coverage and device-test limits.

Keep JavaScript targeting ES5 for QuickJS compatibility. Preserve the plugin name and author identifiers so updates keep the same plugin identity and login storage.

Update plugin.json, PluginInfo.kt, CHANGELOG.md and .github/RELEASE_NOTES.md together for a release. A main-branch manifest change triggers the build/test/release workflow. Keep future release tags and assets for rollback; the prototype cleanup is a one-time numbering reset.

See [NOTICE](NOTICE) for attribution and [LICENSE](LICENSE) for the licence.
