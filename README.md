# MusicBrainz + ListenBrainz for Spotube V6

A community plugin for **Spotube 6.0.0**: search songs on YouTube Music and keep your playlists and listening history on ListenBrainz.

**[Download the plugin](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/releases/latest)** · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/Meiday14/spotube-plugin-musicbrainz-listenbrainz/issues)

## Features

- YouTube Music song search; MusicBrainz artist and album search.
- ListenBrainz playlists, including private and generated playlists.
- Playlist creation, editing and track additions; loved tracks synced with ListenBrainz.
- Optional scrobbling to save your listening history.

## Install

1. Download **plugin.smplug** from Releases and import it in **Settings → Plugins**. Do not extract it.
2. Select **musicbrainz-listenbrainz-v6** under **In use → Metadata**. For listening history, also select it under **Scrobbling**.
3. Keep **NewPipe (built-in)** enabled as the audio source.
4. Open [ListenBrainz settings](https://listenbrainz.org/settings/) in your regular browser, sign in with your MusicBrainz account and copy your **User token**.
5. Return to Spotube, choose **Sign in**, paste the token and confirm. Your playlists appear under **Library → Playlists**.

To update, import the new file and accept replacement, then restart Spotube. Existing prototype users may see a downgrade prompt because public version numbering has restarted; see the changelog.

## Known limits

- Saving a YouTube result to ListenBrainz requires a matching MusicBrainz recording. Songs can still play without a match.
- Spotube 6.0.0 has no **Remove from playlist** button. Remove entries on listenbrainz.org.
- Saved albums, artists and public playlist bookmarks are local to the device. Artwork uploads and collaborator settings are managed on ListenBrainz.

## Privacy

Your token is stored on your device and sent only to ListenBrainz. Song searches go to YouTube Music; no Google login is required.

Build instructions: [DEVELOPMENT.md](DEVELOPMENT.md).

Maintained by [Meiday14](https://github.com/Meiday14). Independent of Spotube and MetaBrainz. **[AGPL-3.0-or-later](LICENSE)** · [Credits](NOTICE)
