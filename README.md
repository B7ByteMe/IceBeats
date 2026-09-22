<p align="center">
  <img src="https://raw.githubusercontent.com/B7ByteMe/IceBeats/refs/heads/main/assets/ic_launcher-playstore.png" width="120" alt="IceBeats Logo" />
</p>

<h1 align="center">IceBeats</h1>

<p align="center">
  <b>Advanced YouTube Music Client for Android</b><br/>
  <i>Built & maintained by <a href="https://github.com/B7ByteMe">Valora · Zyxone</a></i>
</p>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Module Overview](#module-overview)
- [Screenshots](#screenshots)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**IceBeats** is a feature-rich, open-source YouTube Music client for Android. Designed from the ground up with a focus on **premium user experience**, it brings 15+ custom player themes, multi-source lyrics, offline download, Discord Rich Presence, music recognition, and a full Material You design system — all without ads.

> **Version**: `6.0.3` (versionCode 173)  
> **Package**: `com.valora.icebeats`  
> **Website**: [icebeats.pages.dev](https://icebeats.pages.dev/)  
> **Repository**: [github.com/B7ByteMe/IceBeats](https://github.com/B7ByteMe/IceBeats)

> **Disclaimer**: IceBeats is an independent project and is **not** affiliated, sponsored, or endorsed by YouTube or Google.

---

## Features

### Playback & Streaming
- Full access to the entire YouTube Music catalog
- Audio streaming up to **256kbps AAC**
- Background playback with persistent notification
- Gapless playback and audio normalization
- Sleep timer and playback speed control
- Queue management with drag-to-reorder

### Player Themes (15+)
| Theme | Style |
|---|---|
| **Apple / iOS Styled** | Apple Music-inspired layout |
| **Frost** | Frosted glass with blur effects |
| **Galaxy** | Deep space visuals |
| **Futuristic** | Sci-fi HUD design |
| **Cloud Glow** | Soft ambient glow |
| **Groove** | Vibrant & colorful |
| **Minimal** | Clean, distraction-free |
| **Neon** | Glowing neon aesthetics |
| **Paper** | Flat Material-style |
| **Colourful** | Dynamic color gradients |
| **Fold** | Origami-inspired folds |
| **Popsy** | Playful bubbly style |
| **Alternate Queue** | Side-panel queue layout |
| **Always-on Display** | Lock screen player |
| **Standard** | Classic default player |

### Lyrics System (Multi-Source)
- **YouTube** — built-in YouTube lyrics
- **YouTube Subtitle** — auto-captions as lyrics
- **KuGou** — Chinese lyrics database
- **LRCLib** — community lyrics database
- **BetterLyrics** — curated lyrics provider
- Synchronized (word-by-word) and line-by-line modes

### Library & Management
- Local Room database for playlists, favorites, and history
- Offline download via ExoPlayer DownloadService
- Auto-sync and cloud backup (Google Drive)
- Listening stats, history, and Year in Music recap

### Social & Extra Features
- **Listen Together** — share music sessions with friends
- **Discord Rich Presence** (via Kizzy)
- **Music Recognition** — identify songs (Shazam-powered)
- **JioSaavn integration** — alternate source fallback
- **Home Screen Widget**
- **Dynamic Island–style notification** (floating mini-player)
- **40+ language translations** (via Crowdin)

### Privacy & Customization
- No ads, no data collection, no tracking
- Material You dynamic color theming
- Multiple home layouts: Standard, Neon, Playful, Spotify-style
- RTL support, OLED black mode
- Android Auto / Car support

---

## Technology Stack

| Category | Technology | Version |
|---|---|---|
| **Language** | [Kotlin](https://kotlinlang.org/) | Latest |
| **UI** | [Jetpack Compose](https://developer.android.com/jetpack/compose) | Latest |
| **Architecture** | MVVM + Clean Architecture | — |
| **DI** | [Hilt](https://dagger.dev/hilt/) | Latest |
| **Database** | [Room SQLite](https://developer.android.com/training/data-storage/room) | Latest |
| **Media** | [Jetpack Media3 / ExoPlayer](https://developer.android.com/guide/topics/media/media3) | `1.8.0` |
| **Networking** | [Ktor](https://ktor.io/) + OkHttp | Latest |
| **Image Loading** | [Coil](https://coil-kt.github.io/coil/) | `2.7.0` |
| **Firebase** | Auth, Realtime DB, FCM | BOM `34.11.0` |
| **Async** | Kotlin Coroutines + Flow | Latest |
| **Min SDK** | Android 7.0 (API 24) | — |
| **Target SDK** | Android 15 (API 35) | — |
| **Compile SDK** | Android 16 (API 36) | — |
| **JDK** | Java 21 | — |
| **Build System** | Gradle `8.13` with KTS | — |

---

## Module Overview

| Module | Purpose |
|---|---|
| [`app`](https://github.com/B7ByteMe/IceBeats/tree/main/app) | Main Android application |
| [`innertube`](https://github.com/B7ByteMe/IceBeats/tree/main/innertube) | YouTube InnerTube API client — fetches songs, albums, artists, recommendations |
| [`kugou`](https://github.com/B7ByteMe/IceBeats/tree/main/kugou) | KuGou lyrics provider |
| [`lrclib`](https://github.com/B7ByteMe/IceBeats/tree/main/lrclib) | LRCLib community lyrics |
| [`betterlyrics`](https://github.com/B7ByteMe/IceBeats/tree/main/betterlyrics) | BetterLyrics provider |
| [`kizzy`](https://github.com/B7ByteMe/IceBeats/tree/main/kizzy) | Discord Rich Presence integration |
| [`shazamkit`](https://github.com/B7ByteMe/IceBeats/tree/main/shazamkit) | Music recognition (Shazam-based) |
| [`jossredconnect`](https://github.com/B7ByteMe/IceBeats/tree/main/jossredconnect) | Custom network connectivity module |
| [`material-color-utilities`](https://github.com/B7ByteMe/IceBeats/tree/main/material-color-utilities) | Google Material color quantization |

---

## Screenshots

> Available on the [official website](https://icebeats.pages.dev/) and [GitHub Releases](https://github.com/B7ByteMe/IceBeats/releases).

---

## Contributing

All contributions are welcome! Please read the guidelines first:

- [**CONTRIBUTING.md**](https://github.com/B7ByteMe/IceBeats/blob/main/CONTRIBUTING.md) — Development workflow & code standards
- [**CODE_OF_CONDUCT.md**](https://github.com/B7ByteMe/IceBeats/blob/main/CODE_OF_CONDUCT.md) — Community guidelines
- [**SECURITY.md**](https://github.com/B7ByteMe/IceBeats/blob/main/SECURITY.md) — Reporting vulnerabilities

**Found a bug?** → [Open an Issue](https://github.com/B7ByteMe/IceBeats/issues/new)  
**Have an idea?** → [Start a Discussion](https://github.com/B7ByteMe/IceBeats/issues/new)  
**Want to translate?** → See [crowdin.yml](https://github.com/B7ByteMe/IceBeats/blob/main/crowdin.yml)

---

## License

**Copyright © 2025–2026 [Valora · Zyxone](https://github.com/B7ByteMe)**

This project is licensed under the **GNU General Public License v3.0**.  
See the full license: [LICENSE](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE)

> Any unauthorized commercial use of this software or its derivatives constitutes a violation of the license terms.

---

<div align="center">

### Developed by [Valora · Zyxone](https://github.com/B7ByteMe)

<p>
  <a href="https://github.com/B7ByteMe">GitHub</a> · 
  <a href="https://github.com/B7ByteMe/IceBeats">Repository</a> · 
  <a href="https://icebeats.pages.dev/">Website</a>
</p>

[Back to Top](#icebeats)

</div>
