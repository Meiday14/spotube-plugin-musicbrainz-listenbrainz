# Changelog

## 0.1.5

- Fix sign-in remaining disabled when local HTML has the native WebView bridge but no Spotube convenience wrapper or ready event.
- Recheck bridge/button state after paste insertion, input, focus, and returning from an external browser; bound startup retries.
- Add an accessible eye button with Show/Hide while keeping tokens masked initially and never submitting on visibility changes.
- Preserve the busy state during validation, restore it on native errors, and allow explicit retry if a native response is dropped.
- Clarify that the 20-second validation limit starts after Sign in; the whole form remains available for up to ten minutes from opening.
- Add seven actual-form JavaScript regression tests for native-only/late bridges, paste events, visibility, reply recovery, cancellation and duplicate-click prevention.

## 0.1.4

- Fix zero track counts in library and add-to-playlist pickers: ListenBrainz list responses omit tracks and counts even for non-empty playlists.
- Fetch lightweight recording identifiers only for missing counts, with four concurrent requests, two-minute account-scoped caching and eight-second per-read and whole-batch limits.
- Refresh counters after adds, removals, replacements and deletion; reuse counts from fully loaded playlists. Keep playlist access available if an optional count read fails.
- Add seven counter regression tests, real public list/detail API coverage and packaged Zipline counter assertions.
- Document Spotube 6.0.0's missing native removal action and rollback through retained releases.
- Preserve YouTube Music search, sign-in, permissions and scrobbling.

## 0.1.3

- Search songs through public YouTube Music in the main search and Tracks filter.
- Use original video IDs for direct playback through Spotube's built-in NewPipe source.
- Stop calling ListenBrainz playlist search during general song search; bound the explicit playlist filter to 12 seconds.
- Support continuation pagination, persistent recent song details and safe saved-status checks for YouTube IDs.
- Resolve title/artist matches to MusicBrainz only when explicitly adding to ListenBrainz playlists or loved tracks. Validate all source IDs before writes.
- Preserve ListenBrainz library retrieval, existing MusicBrainz album/artist browsing, scrobbling, identity, permissions and saved login.
- Add 11 search regression tests and extend live API and actual Zipline bridge coverage.

## 0.1.2

- Use English for sign-in, browsing, support text and plugin error messages.
- Keep the token form local and explain how to obtain a token in a regular browser.
- End pending sign-in when the native WebView is closed, so the login button can be used again.
- Preserve the form when Android pauses it while the user switches apps.
- Serialize login attempts, ignore stale window messages, and avoid delayed closes affecting a fresh window.
- Limit token validation to 20 seconds; allow retry in the same form on errors or timeouts.
- Retain the ten-minute maximum for an unused sign-in form.
- Add lifecycle, form JavaScript and real Zipline ownership-error regression coverage.
- Add community documentation and release metadata for Meiday14.

## 0.1.1

- Fix manifest ability and permission names to the lowercase values expected by Spotube 6.0.0.
- Add tests using the host's actual enum decoders. This fixes a plugin appearing as installed but missing from the Metadata selector.

## 0.1.0

- Initial Kotlin/JS community implementation for Spotube 6.0.0, with MusicBrainz catalogue metadata, ListenBrainz playlists, loved tracks and optional scrobbling.
