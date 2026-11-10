# Changelog

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
