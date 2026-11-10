# MusicBrainz + ListenBrainz for Spotube V6 — 0.1.2

Download the attached **`.smplug`** file and import it in **Spotube 6.0.0 → Settings → Plugins**. Select **musicbrainz-listenbrainz-v6** under **In use → Metadata**, then sign in with your ListenBrainz user token.

This release switches plugin text to English and fixes a sign-in form that could disappear while leaving the plugin waiting for a token. Obtain your token in your regular browser, then return to the Spotube form. Closing the form ends the pending attempt; switching apps keeps it available. Token validation times out after 20 seconds and supports retry.

The full suite passes **29 tests**, including the host manifest decoders, form JavaScript, background/cancel/retry scenarios and loading through the actual Zipline bridge. Public API probes are read-only. The revised login flow still needs a real Android retest.

This plugin provides playlists and metadata. Keep an audio source enabled in Spotube to play music. Scrobbling is optional. Community project maintained by Meiday14, independent of Spotube and MetaBrainz.
