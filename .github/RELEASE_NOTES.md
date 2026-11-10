# MusicBrainz + ListenBrainz for Spotube V6 — 0.1.4

Download **plugin.smplug** below and import it in **Spotube 6.0.0 → Settings → Plugins**, accepting replacement. Keep this plugin as Metadata and **NewPipe (built-in)** as the audio source. Login storage and permissions are unchanged.

Fixes **0 tracks** in the playlist picker and library when the playlist contains songs. ListenBrainz list responses intentionally omit recordings and their count. The plugin now counts lightweight recording identifiers without loading per-song metadata, caches results by account, limits concurrent reads and refreshes counts after changes. If a count read temporarily fails, playlists remain usable and a later read can retry.

YouTube Music search, ListenBrainz playlists, playlist additions and optional scrobbling are preserved. **47 tests pass**, including live public list/detail counts and the packaged plugin's Zipline bridge. No personal account was modified; the Android counter display still needs a device retest.

**Removing tracks:** the plugin already implements the removal API, but Spotube 6.0.0 does not expose a native track-menu action for it. Remove entries on listenbrainz.org until the app adds that action.

**Rollback:** earlier releases and their installable files are retained. Reimport a previous release's plugin.smplug and accept Spotube's downgrade/replacement prompt; do not uninstall the app first.
