# MusicBrainz + ListenBrainz for Spotube V6

Community metadata and optional scrobbling plugin for **Spotube 6.0.0**. Search songs on YouTube Music, access your ListenBrainz playlists (including private playlists), and browse MusicBrainz artists and albums.

Maintained by [Meiday14](https://github.com/Meiday14). Independent of Spotube and the MetaBrainz Foundation.

Download the installable plugin from [Releases](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/releases). Repository visibility is managed separately and is not changed by the build workflow.

## Install on Android

1. Download the `.smplug` file from this repository's **Releases** page. Do not extract it or install it as an APK.
2. In Spotube **6.0.0**, open **Settings → Plugins** and import the file. Review the network, plugin storage and WebView permissions.
3. Under **In use → Metadata**, select **musicbrainz-listenbrainz-v6**. To also submit your listening history, select this plugin under **In use → Scrobbling**. Scrobbling is optional and is not required to browse your playlists.
4. Open [ListenBrainz settings](https://listenbrainz.org/settings/) in your regular browser. Sign in with your MusicBrainz account, approve access if prompted, and copy your **User token**.
5. Return to Spotube, choose **Sign in**, paste the token and confirm. You can also keep the Spotube form open while switching to your browser to get the token.
6. Open **Library → Playlists**. Playlists created or imported into your ListenBrainz account are loaded from the service. Generated playlists are also available from **My ListenBrainz account** on the home page.
7. Keep **NewPipe (built-in)** enabled as the audio source in Spotube. YouTube search results carry the original video ID, which NewPipe can play directly. ListenBrainz playlist tracks continue to use the audio source's music matching.



Updating from an earlier version: import 0.1.5 and accept the replacement. The plugin name, author identifier, storage keys and permissions stay the same, so the saved login and local favourites can be retained by Spotube. There is no need to reinstall the app.

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

## Playlist counts in 0.1.4

ListenBrainz account-list responses omit recordings and their count: even a non-empty playlist is returned with an empty track array. The plugin now fetches lightweight recording identifiers with **fetch_metadata=false** to obtain the count for library, picker, featured and public search entries that lack a supplied count. Duplicates count as separate playlist entries.

At most four count requests run concurrently. Counts are cached by account for two minutes and invalidated after playlist changes; a fully loaded playlist can also fill the count cache. Each optional count request and the whole list-count batch stop after eight seconds, keeping completed results and allowing playlists to remain usable. On a temporary count-read failure the last known count is retained; without a known count Spotube may still display zero until the next successful read, since its plugin contract requires a numeric count.

## Search behaviour (since 0.1.3)

The main search and **Tracks** filter search the public YouTube Music song catalogue. They do not call ListenBrainz playlist search. A slow public playlist search therefore cannot discard song results. No Google login or additional token is needed.

The **Playlists** filter explicitly searches public ListenBrainz playlists, with a 12-second limit and a readable retry message. Your own playlists remain under **Library**. The **Artists** and **Albums** filters still use MusicBrainz.

Search results can be played and scrobbled immediately. Adding a YouTube song to a ListenBrainz playlist or loved tracks requires a MusicBrainz recording ID. On that explicit action only, the plugin asks ListenBrainz to match the title and artist. Both must match after case, punctuation and accent normalization; otherwise it explains that the song can play but cannot be saved to ListenBrainz. All IDs are resolved before any playlist or feedback write, including batches beyond 100 tracks. The source YouTube edition is not stored in a ListenBrainz playlist; playback later uses its MusicBrainz recording.

YouTube result artist pages offer a song selection and device-local artist favourites. Their MusicBrainz album discography and related artists are not inferred. Song details are cached on the device (up to 256 recent tracks), with public oEmbed as a fallback for uncached video IDs.

## Sign-in behaviour in 0.1.5

The token form stays inside Spotube. Its settings address is copyable, and instructions explain how to retrieve the token in your regular browser. The token is masked initially; the eye button with **Show / Hide** lets you inspect it without changing or submitting it.

- **Cancel** or native **Back** ends the pending login. Closing the form does not open another form automatically.
- Switching apps or receiving a delayed/null response from a background WebView does not cancel sign-in.
- Concurrent login calls share a single active attempt. Messages from older windows are ignored.
- **Time to retrieve the token:** the form session lasts up to **10 minutes from opening**, including time spent in your external browser. The 20-second token check does not run while you log into the website, copy or paste the token.
- **After pressing Sign in:** the plugin asks ListenBrainz to validate the pasted token. This network validation has a **20-second** limit and allows another attempt in the same form.
- Local HTML pages can use the existing native WebView bridge directly when Spotube's convenience wrapper or ready event is absent. A bounded startup retry and paste/input/focus/return events recheck readiness; no token is submitted automatically.
- A token submission disables the button while validation is pending. A native error restores it; a local 30-second reply watchdog also allows explicit retry if Android drops the native reply. It does not shorten the time available to retrieve the token.

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
- Spotube 6.0.0 has no Remove from playlist track-menu action wired to the plugin API. This plugin implements removal, but cannot add a native menu button. Use the playlist page on listenbrainz.org to remove entries until the application exposes that action.
- Automatic plugin update checks are not implemented. Install updates from Releases or through Spotube's repository installation controls.
- Installation and ListenBrainz playlist playback were confirmed by an Android user before this search update. YouTube search and playlist additions in 0.1.3 were also confirmed by the Android user. Version 0.1.4 counters are verified with live public playlists and the real Zipline bridge; an Android device retest of the counter display is still needed. The 0.1.5 sign-in fixes are covered by the actual form JavaScript and packaged Zipline tests; the revised Android sign-in form also needs a device retest.

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

The full suite has **54 tests**, covering host manifest decoding, private account authorization with simulated responses, pagination, token handling, lifecycle cancellation, background WebViews, concurrent sign-in calls, stale messages, UI JavaScript (native-only/late bridge, paste ordering, Show/Hide, explicit retry, dropped replies and background return), QuickJS compilation, YouTube parsing/continuations, credential isolation, saved-status checks, cache restoration, bounded search timeouts, MusicBrainz matching before writes, missing-count account-list responses, bounded concurrent count reads, cache/account isolation and invalidation after mutations, and the real Zipline bridge. Live probes use public read-only endpoints (YouTube search uses a read-only POST); account mutations are tested only with simulated services.

The JavaScript target is explicitly **ES5** because QuickJS rejects some ES generator control-flow patterns produced by Kotlin 2.4.21's default target. Generated JavaScript is not patched manually.

## Publish and discovery

Add the GitHub topic **`spotube-zipline-plugin`** to the public repository so Spotube V6 can discover it. Useful additional topics: `spotube-plugin`, `spotube-metadata-plugin`, `listenbrainz`, `musicbrainz`.

The included workflow tests pushes/PRs and builds a downloadable artifact. A push to `main` that changes `plugin.json`, a matching `v*` Git tag, or an explicit manual publish run creates a repository release and attaches `plugin.smplug`. Release visibility follows repository visibility. A source ZIP alone does not make a plugin installable.

## Rollback

Published releases and their version tags are retained. Download **plugin.smplug** from the desired [release](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/releases), import it and accept Spotube's replacement/downgrade prompt. The plugin identity and login storage remain the same; do not uninstall the app or remove the plugin first.

- **0.1.2:** English text and revised sign-in lifecycle.
- **0.1.3:** YouTube Music song search and title/artist matching for ListenBrainz saves.
- **0.1.4:** Playlist count correction.
- **0.1.5:** Paste/bridge readiness fix, token Show/Hide.

For a source-code rollback, use the tagged source as a base for a new version. Do not move old release tags or replace old release assets.

## Credits and licence

- Functional reference and logo: [KRTirtho's V5 plugin](https://github.com/KRTirtho/spotube-plugin-musicbrainz-listenbrainz), commit `2fe53e9d65021465374346dd2d0a5e70a468141d`, by Kingkor Roy Tirtho.
- YouTube Music search protocol reference: [piper-plumber/spotube-plugin-youtube-music](https://github.com/piper-plumber/spotube-plugin-youtube-music), commit `f225368f4fb670b7760db248f162736e00f33d70`, AGPL-3.0, whose InnerTube client is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist). This project implements a focused JSON song-search client and relies on Spotube for audio.
- Host contracts and manifest enum decoders: [Spotube v6.0.0](https://github.com/team-spotube/spotube/tree/v6.0.0), commit `8925b1b9351c3af2fd672db898d8d899badc1cb9`. Adapted enum decoders are included in the test sources with their original copyright notice.
- [Spotube plugin development](https://docs.spotube.cc/creating-a-plugin/), [ListenBrainz API](https://listenbrainz.readthedocs.io/en/latest/users/api/index.html), [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API).

This implementation is distributed under **AGPL-3.0-or-later**; see [LICENSE](LICENSE). Names, logos and metadata retain their respective rights. This is a community plugin, not an official release by Spotube or MetaBrainz.
