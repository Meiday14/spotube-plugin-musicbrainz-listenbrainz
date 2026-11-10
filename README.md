# MusicBrainz + ListenBrainz for Spotube V6

Community metadata and optional scrobbling plugin for **Spotube 6.0.0**. Search songs on YouTube Music, access your ListenBrainz playlists (including private playlists), and browse MusicBrainz artists and albums.

Maintained by [Meiday14](https://github.com/Meiday14). Independent of Spotube and the MetaBrainz Foundation.

Download the installable plugin from [Releases](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/releases). Repository visibility is managed separately and is not changed by the build workflow.

## Install on Android

1. Download the `.smplug` file from this repository's **Releases** page. Do not extract it or install it as an APK.
2. In Spotube **6.0.0**, open **Settings → Plugins** and import the file. Review the network, plugin storage and WebView permissions.
3. Under **In use → Metadata**, select **musicbrainz-listenbrainz-v6**.
4. Open [ListenBrainz settings](https://listenbrainz.org/settings/) in your regular browser. Sign in with your MusicBrainz account, approve access if prompted, and copy your **User token**.
5. Return to Spotube, choose **Sign in**, paste the token and confirm. You can also keep the Spotube form open while switching to your browser to get the token.
6. Open **Library → Playlists**. Playlists created or imported into your ListenBrainz account are loaded from the service. Generated playlists are also available from **My ListenBrainz account** on the home page.
7. Keep **NewPipe (built-in)** enabled as the audio source in Spotube. YouTube search results carry the original video ID, which NewPipe can play directly. ListenBrainz playlist tracks continue to use the audio source's music matching.

To also submit your listening history, select this plugin under **In use → Scrobbling**. Scrobbling is optional and is not required to browse your playlists.

Updating from 0.1.1 or 0.1.2: import 0.1.3 and accept the replacement. The plugin name, author identifier, storage keys and permissions stay the same, so the saved login and local favourites can be retained by Spotube. There is no need to reinstall the app.

See the [Spotube plugin installation guide](https://docs.spotube.cc/installing-plugins/) for the host application's installation controls.

## Features

- ListenBrainz token validation, session restoration and sign-out.
- Account playlists, private playlists, generated playlists and public playlist search.
- Paginated playlists and tracks, preserving order and duplicate recordings beyond the first 20 items.
- General and track search on YouTube Music, with continuation pagination and direct video IDs for the built-in NewPipe source.
- MusicBrainz artist and album search in their dedicated filters; album tracks in medium/track order from one edition.
- Loved tracks synced with ListenBrainz recording feedback.
- Optional scrobbling through the Spotube V6 API.
- Playlist creation, editing and track additions/removals through the ListenBrainz API.

## Search behaviour in 0.1.3

The main search and **Tracks** filter search the public YouTube Music song catalogue. They do not call ListenBrainz playlist search. A slow public playlist search therefore cannot discard song results. No Google login or additional token is needed.

The **Playlists** filter explicitly searches public ListenBrainz playlists, with a 12-second limit and a readable retry message. Your own playlists remain under **Library**. The **Artists** and **Albums** filters still use MusicBrainz.

Search results can be played and scrobbled immediately. Adding a YouTube song to a ListenBrainz playlist or loved tracks requires a MusicBrainz recording ID. On that explicit action only, the plugin asks ListenBrainz to match the title and artist. Both must match after case, punctuation and accent normalization; otherwise it explains that the song can play but cannot be saved to ListenBrainz. All IDs are resolved before any playlist or feedback write, including batches beyond 100 tracks. The source YouTube edition is not stored in a ListenBrainz playlist; playback later uses its MusicBrainz recording.

YouTube result artist pages offer a song selection and device-local artist favourites. Their MusicBrainz album discography and related artists are not inferred. Song details are cached on the device (up to 256 recent tracks), with public oEmbed as a fallback for uncached video IDs.

## Sign-in behaviour (unchanged since 0.1.2)

The token form now stays inside Spotube. Its settings address is copyable, and instructions explain how to retrieve the token in your regular browser, avoiding a navigation that replaces the form.

- **Cancel** or native **Back** ends the pending login. Closing the form does not open another form automatically.
- Switching apps or receiving a delayed/null response from a background WebView does not cancel sign-in.
- Concurrent login calls share a single active attempt. Messages from older windows are ignored.
- Token validation times out after **20 seconds** and allows another attempt in the same form.
- An unused form times out after **10 minutes**.

Spotube 6.0.0 does not expose a WebView-close event to plugins. During sign-in only, the plugin checks ownership every two seconds with a read-only script. It treats only the host's explicit “WebView is not open for this plugin” error as a close. If the window is closed before the bridge is ready, detection may take up to about twelve seconds. Ownership checks stop when sign-in ends.

## Data and permissions

The user token is sent in the Authorization header only to the official ListenBrainz API and saved in this plugin's device storage. It is not placed in URLs or log messages. No MusicBrainz password is requested by this plugin.

The plugin declares no cookie-reading domains and does not read or script the ListenBrainz/MusicBrainz login websites. WebView access is used for the plugin's own token form. Network access is used for official Brainz APIs, public YouTube Music song search, public YouTube oEmbed and artwork links. Song queries are sent to YouTube Music; the ListenBrainz token and Google cookies are never included in those requests. Persistent storage holds the saved login, local favourites, recent song details and positive MusicBrainz mappings.

## Current limits

- Official MusicBrainz and ListenBrainz instances only; no automatic V5 credential migration. YouTube Music search uses its public web-client protocol, which may change.
- Saved albums, saved artists and public playlist bookmarks are local to the device and separated by account. Account playlists and loved tracks are fetched from ListenBrainz.
- Playlist artwork uploads and enabling collaboration are unsupported through this plugin. Configure collaborators on listenbrainz.org.
- Artist track lists are catalogue selections, not official popularity charts. MusicBrainz related artists use shared genres. YouTube result artist pages have no inferred album discography.
- Playlist changes may need several API requests and are not atomic. An error is returned if a step fails.
- Automatic plugin update checks are not implemented. Install updates from Releases or through Spotube's repository installation controls.
- Installation and ListenBrainz playlist playback were confirmed by an Android user before this search update. Version 0.1.3 search is verified with public live queries and the real Zipline bridge; an Android device retest is still needed.

## Build and test

Requires **JDK 21**. The wrapper downloads Gradle and official Maven dependencies.

Windows PowerShell:

```powershell
.\gradlew.bat jvmTest
# Also run public, read-only API probes:
$env:BRAINZ_LIVE_TESTS = '1'
.\gradlew.bat jvmTest
```

Linux/macOS:

```sh
sh ./gradlew jvmTest
BRAINZ_LIVE_TESTS=1 sh ./gradlew jvmTest
```

`jvmTest` builds the actual `.smplug` and tests its loading through Zipline. The output is `build/distributions/plugin-production.smplug`. To build only the package, use `gradlew.bat packageProductionPlugin` or `sh ./gradlew packageProductionPlugin`.

The full suite has **40 tests**, covering host manifest decoding, private account authorization with simulated responses, pagination, token handling, lifecycle cancellation, background WebViews, concurrent sign-in calls, stale messages, UI JavaScript, QuickJS compilation, YouTube parsing/continuations, credential isolation, saved-status checks, cache restoration, bounded search timeouts, MusicBrainz matching before writes and the real Zipline bridge. Live probes use public read-only endpoints (YouTube search uses a read-only POST); account mutations are tested only with simulated services.

The JavaScript target is explicitly **ES5** because QuickJS rejects some ES generator control-flow patterns produced by Kotlin 2.4.21's default target. Generated JavaScript is not patched manually.

## Publish and discovery

Add the GitHub topic **`spotube-zipline-plugin`** to the public repository so Spotube V6 can discover it. Useful additional topics: `spotube-plugin`, `spotube-metadata-plugin`, `listenbrainz`, `musicbrainz`.

The included workflow tests pushes/PRs and builds a downloadable artifact. A push to `main` that changes `plugin.json`, a matching `v*` Git tag, or an explicit manual publish run creates a repository release and attaches `plugin.smplug`. Release visibility follows repository visibility. A source ZIP alone does not make a plugin installable.

## Credits and licence

- Functional reference and logo: [KRTirtho's V5 plugin](https://github.com/KRTirtho/spotube-plugin-musicbrainz-listenbrainz), commit `2fe53e9d65021465374346dd2d0a5e70a468141d`, by Kingkor Roy Tirtho.
- YouTube Music search protocol reference: [piper-plumber/spotube-plugin-youtube-music](https://github.com/piper-plumber/spotube-plugin-youtube-music), commit `f225368f4fb670b7760db248f162736e00f33d70`, AGPL-3.0, whose InnerTube client is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist). This project implements a focused JSON song-search client and relies on Spotube for audio.
- Host contracts and manifest enum decoders: [Spotube v6.0.0](https://github.com/team-spotube/spotube/tree/v6.0.0), commit `8925b1b9351c3af2fd672db898d8d899badc1cb9`. Adapted enum decoders are included in the test sources with their original copyright notice.
- [Spotube plugin development](https://docs.spotube.cc/creating-a-plugin/), [ListenBrainz API](https://listenbrainz.readthedocs.io/en/latest/users/api/index.html), [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API).

This implementation is distributed under **AGPL-3.0-or-later**; see [LICENSE](LICENSE). Names, logos and metadata retain their respective rights. This is a community plugin, not an official release by Spotube or MetaBrainz.
