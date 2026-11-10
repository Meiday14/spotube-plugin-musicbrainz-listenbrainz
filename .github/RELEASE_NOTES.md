# MusicBrainz + ListenBrainz for Spotube V6 — 0.1.0

The first consolidated public release, based on development version 0.1.5. See [CHANGELOG.md](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/blob/main/CHANGELOG.md) for the numbering reset and earlier milestones.

Download **plugin.smplug** below and import it in **Spotube 6.0.0 → Settings → Plugins**. Select this plugin as Metadata and keep **NewPipe (built-in)** as the audio source. Scrobbling is optional.

Includes YouTube Music song search, MusicBrainz browsing, ListenBrainz playlists and loved tracks, playlist counts, token Show/Hide and the sign-in readiness fixes.

If replacing a prototype, accept the replacement/downgrade prompt and restart Spotube. The plugin identity, permissions and login storage are unchanged.

**54 tests pass locally**, including public read-only API probes and loading the packaged plugin through Zipline. Recent sign-in and counter changes still need Android device confirmation.
