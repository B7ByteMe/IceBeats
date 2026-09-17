<p align="center">
  <img src="https://raw.githubusercontent.com/B7ByteMe/IceBeats/refs/heads/main/assets/ic_launcher-playstore.png" width="120" alt="IceBeats Logo" />
</p>

<h1 align="center">IceBeats</h1>

<p align="center">
  <b>Advanced YouTube Music Client for Android</b><br/>
  <i>Built & maintained by <a href="https://github.com/B7ByteMe">Valora · Zyxone</a></i>
</p>

<div align="center">

[![Latest Release](https://img.shields.io/github/v/release/B7ByteMe/IceBeats?style=for-the-badge&logo=github&color=0D1117&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/B7ByteMe/IceBeats/total?style=for-the-badge&logo=github&color=0D1117&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/releases)
[![License](https://img.shields.io/github/license/B7ByteMe/IceBeats?style=for-the-badge&logo=gnu&color=2B3137&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=161B22)](https://www.android.com)
[![Stars](https://img.shields.io/github/stars/B7ByteMe/IceBeats?style=for-the-badge&logo=github&color=FFD700&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/stargazers)
[![Forks](https://img.shields.io/github/forks/B7ByteMe/IceBeats?style=for-the-badge&logo=github&color=4A90E2&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/network/members)
[![Issues](https://img.shields.io/github/issues/B7ByteMe/IceBeats?style=for-the-badge&logo=github&color=E74C3C&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/issues)

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Technology Stack](#-technology-stack)
- [Project Structure](#-project-structure)
- [Module Overview](#-module-overview)
- [Screenshots](#-screenshots)
- [Installation](#-installation)
- [Building from Source](#-building-from-source)
- [Contributing](#-contributing)
- [Acknowledgments](#-acknowledgments)
- [License](#-license)

---

## 🎵 Overview

**IceBeats** is a feature-rich, open-source YouTube Music client for Android. Designed from the ground up with a focus on **premium user experience**, it brings 15+ custom player themes, multi-source lyrics, offline download, Discord Rich Presence, music recognition, and a full Material You design system — all without ads.

> **Version**: `6.0.3` (versionCode 173)  
> **Package**: `com.valora.icebeats`  
> **Website**: [icebeats.pages.dev](https://icebeats.pages.dev/)  
> **Repository**: [github.com/B7ByteMe/IceBeats](https://github.com/B7ByteMe/IceBeats)

> ⚠️ IceBeats is an independent project and is **not** affiliated, sponsored, or endorsed by YouTube or Google.

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

## Project Structure

```
IceBeats/
│
├── app/                                    # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── ic_launcher-playstore.png
│   │   │   ├── assets/
│   │   │   │   └── splash_anim.json        # Lottie splash animation
│   │   │   ├── java/com/valora/icebeats/
│   │   │   │   ├── App.kt                  # Application class
│   │   │   │   ├── MainActivity.kt         # Main entry activity
│   │   │   │   ├── MusicWidget.kt          # Home screen widget
│   │   │   │   ├── MyFirebaseMessagingService.kt
│   │   │   │   │
│   │   │   │   ├── constants/              # App-wide constants
│   │   │   │   ├── db/                     # Room database layer
│   │   │   │   │   ├── DatabaseDao.kt      # All DB queries
│   │   │   │   │   ├── MusicDatabase.kt    # DB instance & migrations
│   │   │   │   │   ├── Converters.kt       # Type converters
│   │   │   │   │   └── entities/           # DB entity models
│   │   │   │   │
│   │   │   │   ├── di/                     # Hilt DI modules
│   │   │   │   ├── extensions/             # Kotlin extension functions
│   │   │   │   ├── jiosaavn/               # JioSaavn API integration
│   │   │   │   │   └── JioSaavnApi.kt
│   │   │   │   │
│   │   │   │   ├── lyrics/                 # Multi-source lyrics engine
│   │   │   │   │   ├── LyricsHelper.kt
│   │   │   │   │   ├── LyricsProvider.kt   # Base provider interface
│   │   │   │   │   ├── IceBeatsLyricsUtils.kt
│   │   │   │   │   ├── LyricsEntry.kt
│   │   │   │   │   ├── LyricsUtils.kt
│   │   │   │   │   ├── BetterLyricsProvider.kt
│   │   │   │   │   ├── KuGouLyricsProvider.kt
│   │   │   │   │   ├── LrcLibLyricsProvider.kt
│   │   │   │   │   ├── YouTubeLyricsProvider.kt
│   │   │   │   │   └── YouTubeSubtitleLyricsProvider.kt
│   │   │   │   │
│   │   │   │   ├── models/                 # Data models
│   │   │   │   │   ├── MediaMetadata.kt
│   │   │   │   │   ├── ItemsPage.kt
│   │   │   │   │   ├── PersistQueue.kt
│   │   │   │   │   ├── PersistPlayerState.kt
│   │   │   │   │   └── SimilarRecommendation.kt
│   │   │   │   │
│   │   │   │   ├── playback/               # Music playback engine
│   │   │   │   │   ├── MusicService.kt     # Core media service
│   │   │   │   │   ├── PlayerConnection.kt # Player state bridge
│   │   │   │   │   ├── MediaLibrarySessionCallback.kt
│   │   │   │   │   ├── ExoDownloadService.kt # Offline downloads
│   │   │   │   │   ├── DynamicIslandService.kt # Floating mini-player
│   │   │   │   │   ├── DownloadUtil.kt
│   │   │   │   │   ├── SleepTimer.kt
│   │   │   │   │   └── queues/             # Queue implementations
│   │   │   │   │
│   │   │   │   ├── ui/                     # UI layer
│   │   │   │   │   ├── activities/
│   │   │   │   │   │   └── DebugActivity.kt
│   │   │   │   │   ├── component/          # Reusable Compose components
│   │   │   │   │   ├── menu/               # Context menu components
│   │   │   │   │   ├── theme/              # Material You theming
│   │   │   │   │   ├── utils/              # UI utility functions
│   │   │   │   │   ├── player/             # Player themes (15+)
│   │   │   │   │   │   ├── Player.kt       # Main player screen
│   │   │   │   │   │   ├── MiniPlayer.kt
│   │   │   │   │   │   ├── Queue.kt
│   │   │   │   │   │   ├── ApplePlayer.kt
│   │   │   │   │   │   ├── IosStyledPlayer.kt
│   │   │   │   │   │   ├── FrostPlayer.kt
│   │   │   │   │   │   ├── GalaxyPlayer.kt
│   │   │   │   │   │   ├── FuturisticPlayer.kt
│   │   │   │   │   │   ├── CloudGlowPlayer.kt
│   │   │   │   │   │   ├── GroovePlayer.kt
│   │   │   │   │   │   ├── MinimalPlayer.kt
│   │   │   │   │   │   ├── ColourfullPlayer.kt
│   │   │   │   │   │   ├── FoldPlayer.kt
│   │   │   │   │   │   ├── PaperPlayer.kt
│   │   │   │   │   │   ├── PopsyPlayer.kt
│   │   │   │   │   │   ├── AlternateQueue.kt
│   │   │   │   │   │   ├── IceBeatsLyricsScreen.kt
│   │   │   │   │   │   └── AlwaysOnDisplayScreen.kt (via screens/)
│   │   │   │   │   └── screens/            # App screens
│   │   │   │   │       ├── HomeScreen.kt
│   │   │   │   │       ├── NeonHomeScreen.kt
│   │   │   │   │       ├── PlayfulHomeScreen.kt
│   │   │   │   │       ├── ExploreScreen.kt
│   │   │   │   │       ├── NeonExploreScreen.kt
│   │   │   │   │       ├── PlayfulExploreScreen.kt
│   │   │   │   │       ├── SpotifyStyleScreens.kt
│   │   │   │   │       ├── AlbumScreen.kt
│   │   │   │   │       ├── NewReleaseScreen.kt
│   │   │   │   │       ├── YouTubeBrowseScreen.kt
│   │   │   │   │       ├── HistoryScreen.kt
│   │   │   │   │       ├── StatsScreen.kt
│   │   │   │   │       ├── InsightScreen.kt
│   │   │   │   │       ├── YearInMusicScreen.kt
│   │   │   │   │       ├── ListenTogetherScreen.kt
│   │   │   │   │       ├── AlwaysOnDisplayScreen.kt
│   │   │   │   │       ├── AccountScreen.kt
│   │   │   │   │       ├── LoginScreen.kt
│   │   │   │   │       ├── ContributorProfileScreen.kt
│   │   │   │   │       ├── NavigationBuilder.kt
│   │   │   │   │       ├── Screens.kt
│   │   │   │   │       ├── apple/           # Apple-style screens
│   │   │   │   │       ├── artist/          # Artist screens
│   │   │   │   │       ├── library/         # Library screens
│   │   │   │   │       ├── musicrecognition/ # Shazam recognition
│   │   │   │   │       ├── onboarding/      # First-launch onboarding
│   │   │   │   │       ├── playlist/        # Playlist screens
│   │   │   │   │       ├── search/          # Search screens
│   │   │   │   │       └── settings/        # Settings screens
│   │   │   │   │
│   │   │   │   ├── viewmodels/             # MVVM ViewModels (24)
│   │   │   │   │   ├── HomeViewModel.kt
│   │   │   │   │   ├── AlbumViewModel.kt
│   │   │   │   │   ├── ArtistViewModel.kt
│   │   │   │   │   ├── LibraryViewModels.kt
│   │   │   │   │   ├── LocalPlaylistViewModel.kt
│   │   │   │   │   ├── OnlinePlaylistViewModel.kt
│   │   │   │   │   ├── OnlineSearchViewModel.kt
│   │   │   │   │   ├── LocalSearchViewModel.kt
│   │   │   │   │   ├── HistoryViewModel.kt
│   │   │   │   │   ├── StatsViewModel.kt
│   │   │   │   │   ├── YearInMusicViewModel.kt
│   │   │   │   │   ├── ExploreViewModel.kt
│   │   │   │   │   ├── NewReleaseViewModel.kt
│   │   │   │   │   ├── BackupRestoreViewModel.kt
│   │   │   │   │   ├── AccountViewModel.kt
│   │   │   │   │   └── ... (24 total)
│   │   │   │   │
│   │   │   │   └── worker/
│   │   │   │       └── DailyBackupWorker.kt # Auto daily backup
│   │   │   │
│   │   │   └── res/                        # Android resources
│   │   │       ├── drawable/               # Vector icons & drawables
│   │   │       ├── font/                   # Custom fonts
│   │   │       ├── layout/                 # XML layouts
│   │   │       ├── mipmap-hdpi/            # App icon (hdpi)
│   │   │       ├── mipmap-mdpi/            # App icon (mdpi)
│   │   │       ├── mipmap-xhdpi/           # App icon (xhdpi)
│   │   │       ├── mipmap-xxhdpi/          # App icon (xxhdpi)
│   │   │       ├── mipmap-xxxhdpi/         # App icon (xxxhdpi)
│   │   │       ├── mipmap-anydpi-v26/      # Adaptive icon
│   │   │       ├── values/                 # Strings, colors, styles
│   │   │       ├── values-af-rZA/          # Afrikaans
│   │   │       ├── values-ar/              # Arabic
│   │   │       ├── values-be/              # Belarusian
│   │   │       ├── values-bn-rIN/          # Bengali
│   │   │       ├── values-ca/              # Catalan
│   │   │       ├── values-cs/              # Czech
│   │   │       ├── values-da-rDK/          # Danish
│   │   │       ├── values-DE/              # German
│   │   │       ├── values-el-rGR/          # Greek
│   │   │       ├── values-en-rUS/          # English (US)
│   │   │       ├── values-es/              # Spanish
│   │   │       ├── values-fa-rIR/          # Persian
│   │   │       ├── values-fr-rFR/          # French
│   │   │       ├── values-hi/              # Hindi
│   │   │       ├── values-hu/              # Hungarian
│   │   │       ├── values-id/              # Indonesian
│   │   │       ├── values-it/              # Italian
│   │   │       ├── values-iw-rIL/          # Hebrew
│   │   │       ├── values-ja-rJP/          # Japanese
│   │   │       ├── values-ko-rKR/          # Korean
│   │   │       ├── values-ml/              # Malayalam
│   │   │       ├── values-ne/              # Nepali
│   │   │       ├── values-nl/              # Dutch
│   │   │       ├── values-no-rNO/          # Norwegian
│   │   │       ├── values-or-rIN/          # Odia
│   │   │       ├── values-pa/              # Punjabi
│   │   │       ├── values-pl/              # Polish
│   │   │       ├── values-pt-rBR/          # Portuguese (Brazil)
│   │   │       ├── values-ro-rRO/          # Romanian
│   │   │       ├── values-ru-rRU/          # Russian
│   │   │       ├── values-sr-rSP/          # Serbian
│   │   │       ├── values-sv-rSE/          # Swedish
│   │   │       ├── values-tr/              # Turkish
│   │   │       ├── values-uk-rUA/          # Ukrainian
│   │   │       ├── values-vi/              # Vietnamese
│   │   │       ├── values-zh-rCN/          # Chinese (Simplified)
│   │   │       └── xml/                    # App config XMLs
│   │   │
│   │   └── debug/                          # Debug-only resources
│   ├── schemas/                            # Room DB migration schemas
│   ├── proguard-rules.pro                  # ProGuard / R8 rules
│   ├── build.gradle.kts                    # App-level build config
│   └── google-services.json               # Firebase config (local only)
│
├── innertube/                              # YouTube InnerTube API client
├── kugou/                                  # KuGou lyrics provider module
├── lrclib/                                 # LRCLib lyrics provider module
├── kizzy/                                  # Discord Rich Presence module
├── jossredconnect/                         # Custom connectivity module
├── shazamkit/                              # Music recognition module
├── betterlyrics/                           # BetterLyrics provider module
├── material-color-utilities/              # Google Material color engine
│
├── assets/                                 # Root-level public assets
│   ├── ic_launcher-playstore.png          # Play Store icon
│   └── login_bg_video.mp4                 # Login background video
│
├── fastlane/                               # Fastlane deployment automation
│   └── metadata/                          # Store listing metadata
├── .github/                               # GitHub configuration
│   ├── workflows/                         # GitHub Actions CI/CD
│   └── ISSUE_TEMPLATE/                    # Bug/feature templates
│
├── gradle/                                # Gradle wrapper & libs catalog
├── build.gradle.kts                       # Root build configuration
├── settings.gradle.kts                    # Module declarations
├── gradle.properties                      # Gradle JVM settings
├── local.properties                       # Local secrets (not committed)
├── gradlew / gradlew.bat                  # Gradle wrapper scripts
├── version.json                           # Current version: 6.0.3
├── lint.xml                               # Android lint rules
├── crowdin.yml                            # Crowdin translation config
├── LICENSE                                # GNU GPL v3.0
├── README.md                              # Main documentation
├── README.en.md                           # Extended English docs
├── CONTRIBUTING.md                        # How to contribute
├── CODE_OF_CONDUCT.md                     # Community guidelines
└── SECURITY.md                            # Security policy
```

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

## Installation

### System Requirements

| Requirement | Minimum |
|---|---|
| Android Version | 7.0 Nougat (API 24) |
| RAM | 2 GB recommended |
| Storage | ~50 MB |
| Internet | Required for streaming |

### Option 1 — GitHub Releases (Recommended)

1. Go to [**Releases**](https://github.com/B7ByteMe/IceBeats/releases/latest)
2. Download the latest `.apk` file
3. Enable **"Install from unknown sources"** in device settings
4. Open the APK to install

### Option 2 — Official Website

1. Visit [**icebeats.pages.dev**](https://icebeats.pages.dev/)
2. Download the APK for Android
3. Follow the on-screen installation steps

---

## Building from Source

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Ladybug or newer |
| JDK | 21 |
| Android SDK | API 35 / 36 |
| Git | Latest |

### Required Configuration

> [!IMPORTANT]
> `google-services.json` and API keys are **not** included in the repo for security. You must configure them before building.

#### Step 1 — Firebase (`google-services.json`)

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Create a project → Add Android app
3. Package name: `com.valora.icebeats`
4. Download `google-services.json`
5. Place it at `app/google-services.json`

#### Step 2 — Local Keys (`local.properties`)

```properties
# Google YouTube Data API Key
google.api.key=YOUR_GOOGLE_API_KEY_HERE

# Stats API (optional)
stats.api.key=YOUR_STATS_KEY
stats.base.url=YOUR_STATS_URL

# Auth API (optional)
auth.api.base.url=YOUR_AUTH_URL
```

### Build Commands

```bash
# 1. Clone the repo
git clone https://github.com/B7ByteMe/IceBeats.git
cd IceBeats

# 2. Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# 3. Build release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/

# 4. Install directly to device
./gradlew installDebug

# 5. Run unit tests
./gradlew test

# 6. Clean build
./gradlew clean
```

---

## Contributing

All contributions are welcome! Please read the guidelines first:

- [**CONTRIBUTING.md**](https://github.com/B7ByteMe/IceBeats/blob/main/CONTRIBUTING.md) — Development workflow & code standards
- [**CODE_OF_CONDUCT.md**](https://github.com/B7ByteMe/IceBeats/blob/main/CODE_OF_CONDUCT.md) — Community guidelines
- [**SECURITY.md**](https://github.com/B7ByteMe/IceBeats/blob/main/SECURITY.md) — Reporting vulnerabilities

### Quick Start

```bash
# 1. Fork the repo on GitHub
# 2. Clone your fork
git clone https://github.com/YOUR_USERNAME/IceBeats.git

# 3. Create a feature branch
git checkout -b feature/your-feature-name

# 4. Make changes, then commit
git commit -m "feat: describe your change"

# 5. Push and open a Pull Request
git push origin feature/your-feature-name
```

**Found a bug?** → [Open an Issue](https://github.com/B7ByteMe/IceBeats/issues/new)  
**Have an idea?** → [Start a Discussion](https://github.com/B7ByteMe/IceBeats/issues/new)  
**Want to translate?** → See [crowdin.yml](https://github.com/B7ByteMe/IceBeats/blob/main/crowdin.yml)

---

## Acknowledgments

- **[z-huang / InnerTune](https://github.com/z-huang/InnerTune)** — Foundational open-source base
- **[d0x-dev / AirBeats](https://github.com/d0x-dev/AirBeats)** — Original fork, redesign components
- **Community Translators** — 40+ languages powered by Crowdin
- **Beta Testers** — Stability and usability feedback

---

## 📜 License

**Copyright © 2025–2026 [Valora · Zyxone](https://github.com/B7ByteMe)**

This project is licensed under the **GNU General Public License v3.0**.  
See the full license: [LICENSE](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE)

> Any unauthorized commercial use of this software or its derivatives constitutes a violation of the license terms.

<div align="center">

[![GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white)](https://www.gnu.org/licenses/gpl-3.0)

---

### Made with by [Valora · Zyxone](https://github.com/B7ByteMe)

[![GitHub](https://img.shields.io/badge/GitHub-B7ByteMe-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/B7ByteMe)
[![Repo](https://img.shields.io/badge/Repo-IceBeats-0D1117?style=for-the-badge&logo=github&logoColor=white)](https://github.com/B7ByteMe/IceBeats)
[![Website](https://img.shields.io/badge/Website-icebeats.pages.dev-4A90E2?style=for-the-badge&logo=cloudflare&logoColor=white)](https://icebeats.pages.dev/)

If you enjoy IceBeats, please give it a star!

[⬆ Back to Top](#icebeats)

</div>
