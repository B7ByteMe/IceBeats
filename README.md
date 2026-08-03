<p align="center">
  <img src="https://raw.githubusercontent.com/B7ByteMe/IceBeats/refs/heads/main/icon2.png" width="200" alt="IceBeats Logo" />
</p>

<h1 align="center">IceBeats</h1>

<p align="center">
  <b>Advanced YouTube Music Client for Android</b><br/>
  <i>Built & maintained by Valora · Zyxone</i>
</p>

<div align="center">

[![Latest Release](https://img.shields.io/github/v/release/B7ByteMe/IceBeats?style=for-the-badge&logo=github&color=0D1117&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/releases)
[![License](https://img.shields.io/github/license/B7ByteMe/IceBeats?style=for-the-badge&logo=gnu&color=2B3137&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android%206.0+-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white&labelColor=161B22)](https://www.android.com)
[![Stars](https://img.shields.io/github/stars/B7ByteMe/IceBeats?style=for-the-badge&logo=github&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/stargazers)

</div>

---

## ✨ Features

- 🎵 **YouTube Music Integration** — Full access to your favorite tracks
- 🎨 **Premium Dark UI** — Clean charcoal design, easy on the eyes
- 🎯 **Advanced Playback Controls** — Fine-tune your music experience
- 🔐 **Privacy-Focused** — Your data stays yours
- ⚡ **Fast & Smooth** — Optimized performance with fluid animations
- 🌙 **Pure Dark Mode** — Sleek dark theme throughout
- 📱 **Android 6.0+** — Broad device compatibility
- 👤 **Custom Avatar System** — Personalize your profile
- 🎤 **Lyrics Support** — Sync lyrics while you listen
- 🔁 **Listen Together** — Share sessions with friends

---

## 👤 Developer

<table>
  <tr>
    <td align="center">
      <b>Valora · Zyxone</b><br/>
      Android Developer<br/>
      <a href="https://github.com/B7ByteMe">GitHub</a>
    </td>
  </tr>
</table>

> Based on the open-source [InneTube](https://github.com/z-huang/InnerTune) project.
> Original fork from [AirBeats](https://github.com/d0x-dev/AirBeats) — heavily modified and redesigned.

---

## 🛠️ Build from Source

### Prerequisites

| Requirement | Version |
|------------|---------|
| **Java Development Kit (JDK)** | 21 |
| **Android Studio** | Ladybug or newer |
| **Git** | Latest |

### 🔑 Required Configuration

> [!IMPORTANT]
> The Firebase config file (`google-services.json`) and Google API Key are **not** included in the repo for security. You must add these manually.

#### Step 1 — Firebase Setup (`google-services.json`)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create or select a project
3. Add an Android app with package: `com.valora.icebeats`
4. Download `google-services.json`
5. Place it at `app/google-services.json`

#### Step 2 — Google API Key (`local.properties`)

Add your key to `local.properties`:
```properties
google.api.key=YOUR_GOOGLE_API_KEY_HERE
```

### 🚀 Build Steps

```bash
# Clone the repo
git clone https://github.com/B7ByteMe/IceBeats.git

# Enter the project
cd IceBeats

# Build debug APK
./gradlew assembleDebug

# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Build Variants

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing key)
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug
```

---

## 📜 License

**Copyright © 2025 Valora · Zyxone**

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

---

## 💬 Support

- 🐛 Found a bug? Open an [Issue](https://github.com/B7ByteMe/IceBeats/issues/new)
- 💡 Have a suggestion? We'd love to hear it!

---

<div align="center">

### Made with ❤️ by Valora · Zyxone

⭐ If you enjoy IceBeats, consider giving a star! ⭐

[⬆ Back to Top](#icebeats)

</div>
