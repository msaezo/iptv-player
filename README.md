# TUTV

A Chillio-style IPTV player for **Android phones, Android TV and Fire TV**.

- Reads any **M3U / M3U8** playlist (URL or local file) and splits it into **Live TV, Movies and Series**
- **Language filter** for movies & series (Spanish / English by default, configurable)
- **Live TV ordering**: Spain → UK → US → rest; favourites, recents, channel zapping with ▲/▼
- **One title, many versions**: different languages and qualities of the same film/series merged under one card
- **Chillio-style home** with TMDB collections (Trending, In cinemas, Best Picture winners, On Netflix/Prime/Disney+/Max/Movistar+, genres…) showing only what's in your playlist
- **Ratings**: TMDB on cards; Rotten Tomatoes, IMDb and Metacritic (via OMDb) on detail pages
- Continue watching, resume, auto-next episode, audio/subtitle track picker, "open in VLC" fallback

## Install
Every push to `main` builds a signed APK and publishes it under **Releases**:
`https://github.com/msaezo/iptv-player/releases/latest/download/IPTVPlayer.apk`

On Fire TV: install the *Downloader* app, allow it to install unknown apps, and enter the URL above.

## Setup in the app
Settings → paste your M3U URL → *Save & load*. Optionally add free TMDB and OMDb keys for collections and ratings.

This product uses the TMDB API but is not endorsed or certified by TMDB.
