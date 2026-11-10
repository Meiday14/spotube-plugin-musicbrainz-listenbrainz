# MusicBrainz + ListenBrainz for Spotube V6 — 0.1.5

Download **plugin.smplug** below and import it in **Spotube 6.0.0 → Settings → Plugins**, accepting replacement. Fully close and reopen Spotube after replacement. The plugin identity, permissions and saved login are unchanged.

Fixes **Sign in remaining grey after pasting a token** when the local Android form has a native WebView bridge but Spotube's convenience wrapper or ready event is missing. The form rechecks readiness after pasting and returning from your browser. A pending check cannot be submitted twice, and an error or missing reply allows a manual retry.

The token stays masked initially. Use the **eye / Show / Hide** button to inspect it; showing it does not send it anywhere.

**The two time limits are different:**
- You have up to **10 minutes from opening the form** to retrieve and paste the token, including signing into the website in your regular browser.
- Only **after pressing Sign in** does the plugin validate that token with ListenBrainz, with a **20-second network-validation limit**. Those 20 seconds do not run while you obtain the token.

**54 tests pass** locally, including the actual form JavaScript in QuickJS, native-only and delayed bridges, paste ordering, busy/error/timeout recovery, Show/Hide, existing metadata/search/counters and the packaged Zipline bridge. Live API probes are public and read-only. The revised form still needs a real Android retest.

YouTube Music search, ListenBrainz playlists and optional scrobbling are preserved. Older releases and installable files remain available for rollback.
