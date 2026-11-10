# MusicBrainz + ListenBrainz for Spotube V6 — 0.1.3

Download **plugin.smplug** below and import it in **Spotube 6.0.0 → Settings → Plugins**, accepting replacement of the existing plugin. Keep **musicbrainz-listenbrainz-v6** as Metadata and **NewPipe (built-in)** as the audio source. The plugin identity, permissions and login storage are unchanged.

The main search and Tracks filter now find songs on **YouTube Music**. Public ListenBrainz playlist search no longer blocks or discards song results. The Playlists filter remains available separately, with a 12-second timeout and a readable message. Your ListenBrainz library and optional scrobbling remain available.

Playing and scrobbling a search result needs no additional account. Saving it to a ListenBrainz playlist or loved tracks requires a MusicBrainz title/artist match. If none is found, a clear message is shown before any remote changes. See the README for matching and YouTube artist-page limits.

**40 tests pass** locally, including live public search for “zaho allo” and continuation results, existing playlist/album retrieval, sign-in lifecycle, credential isolation and loading/searching the actual packaged plugin through Zipline. No personal account mutations were performed. Android search/playback still needs a device retest.

Community project maintained by Meiday14, independent of Spotube, MetaBrainz and YouTube.
