# IceBeats

<div align="center">
  <img src="https://raw.githubusercontent.com/B7ByteMe/IceBeats/refs/heads/main/icon2.png" alt="IceBeats Preview" width="200"/>
  
  <h3>Advanced YouTube Music Client with Material Design 3 for Android</h3>
  
  [![Latest Release](https://img.shields.io/github/v/release/B7ByteMe/IceBeats?style=flat-square&logo=github&color=0D1117&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/releases)
  [![License](https://img.shields.io/github/license/B7ByteMe/IceBeats?style=flat-square&logo=gnu&color=2B3137&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE)
  [![Android](https://img.shields.io/badge/Platform-Android%206.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white&labelColor=161B22)](https://www.android.com)
  [![Stars](https://img.shields.io/github/stars/B7ByteMe/IceBeats?style=flat-square&logo=github&color=yellow&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/stargazers)
  [![Forks](https://img.shields.io/github/forks/B7ByteMe/IceBeats?style=flat-square&logo=github&color=blue&labelColor=161B22)](https://github.com/B7ByteMe/IceBeats/network/members)
</div>

---

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [Key Features](#key-features)
- [Documentation](#documentation)
- [Installation](#installation)
- [Building from Source](#building-from-source)
- [Contributing](#contributing)
- [Acknowledgments](#acknowledgments)
- [License](#license)

---

## Overview

**IceBeats** is an open-source YouTube Music client specifically designed for Android devices. It delivers a superior user experience with a modern interface implementing Material Design 3, offering advanced functionalities to explore, play, and manage musical content without the limitations of the official application.

### Key Benefits

- **Ad-free Experience**: Enjoy uninterrupted music streaming
- **Enhanced Performance**: Optimized for smooth playback and navigation
- **Privacy-focused**: No data collection or tracking
- **Customizable Interface**: Personalize your music experience
- **Offline Capabilities**: Download and play music without internet connection

> **Note**: IceBeats is an independent project and is not affiliated, sponsored, or endorsed by YouTube or Google.

---

## Technology Stack

The application is built using a modern Android development stack:

- **Programming Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Local Database**: [Room SQLite](https://developer.android.com/training/data-storage/room)
- **Network Client**: [Ktor](https://ktor.io/) & OkHttp
- **Media Playback**: [Jetpack Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)

---

## Key Features

### Playback & Streaming
- Full playback of YouTube Music catalog
- Audio streaming at up to 256kbps AAC
- Background playback and cache support
- Gapless playback and audio normalization

### Customization
- Dynamic coloring based on album art (Material You)
- Multiple theme configurations (Light, Dark, OLED Black)
- Customizable home screen tabs and layout options

### Management
- Local database for playlists, library, and favorites
- Auto-sync options and custom backup creation
- Listening history tracking and statistics

---

## Documentation

For detailed information about configuration, advanced features, and usage guides, consult our official documentation:

<div align="center">
  
📄 **[Official Website](https://icebeats.pages.dev/)**

</div>

---

## Installation

### System Requirements

| Component | Minimum Requirement |
|:----------|:--------------------|
| Operating System | Android 6.0 (Marshmallow) or higher |
| Storage Space | 10 MB available |
| Network | Internet connection for streaming |
| RAM | 2 GB recommended |

### Installation Methods

#### Option 1: GitHub Releases (Recommended)

1. Navigate to the [Releases](https://github.com/B7ByteMe/IceBeats/releases) section on GitHub
2. Download the APK file from the latest stable version
3. Enable "Install from unknown sources" in your device's security settings
4. Open the downloaded APK file to complete installation

#### Option 2: Official Website

1. Visit the official [IceBeats website](https://icebeats.pages.dev/)
2. Select the download option for Android
3. Follow the installation instructions provided

---

## Building from Source

### Prerequisites

<table>
<tr>
<th>Tool</th>
<th>Recommended Version</th>
<th>Purpose</th>
</tr>
<tr>
<td>Gradle</td>
<td>8.0 or higher</td>
<td>Build automation</td>
</tr>
<tr>
<td>Kotlin</td>
<td>1.9 or higher</td>
<td>Programming language</td>
</tr>
<tr>
<td>Android Studio</td>
<td>Ladybug or newer</td>
<td>IDE and development environment</td>
</tr>
<tr>
<td>JDK</td>
<td>21</td>
<td>Java runtime environment</td>
</tr>
<tr>
<td>Android SDK</td>
<td>API level 35/36</td>
<td>Android development tools</td>
</tr>
</table>

### Firebase & Google API Key Configuration

> [!IMPORTANT]
> For security and privacy reasons, the official `google-services.json` configuration file and Google API Key are not included in this repository. To compile and run the application, you must configure them:

#### A. Firebase Configuration (google-services.json)
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Create a new Firebase project (or use an existing one) and click **Add app** (Android).
3. Register your app using the package name **`com.valora.icebeats`**.
4. Download the generated **`google-services.json`** file.
5. Place the **`google-services.json`** file directly in the **`app/`** folder of this project (i.e., `app/google-services.json`).

#### B. Google API Key Configuration (local.properties)
1. Open the **`local.properties`** file in the root directory of this project.
2. Add your Google API Key under the property name `google.api.key`:
   ```properties
   google.api.key=YOUR_GOOGLE_API_KEY_HERE
   ```

### Environment Setup

```bash
# Clone the repository
git clone https://github.com/B7ByteMe/IceBeats.git

# Navigate to project directory
cd IceBeats

# Update submodules (if any)
git submodule update --init --recursive
```

### Build Methods

#### Android Studio Build

1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate and select the IceBeats directory
4. Wait for project synchronization and indexing
5. Select Build → Build Bundle(s) / APK(s) → Build APK(s)

#### Command Line Build

```bash
# Build production release
./gradlew assembleRelease

# Build debug version
./gradlew assembleDebug

# Full build with tests
./gradlew build

# Run unit tests
./gradlew test

# Clean build
./gradlew clean
```

> **Note**: Compiled APK files will be located in the `app/build/outputs/apk/` directory.

---

## Contributing

### Code of Conduct

All participants in this project must adhere to our code of conduct that promotes an inclusive, respectful, and constructive environment. Please review the [complete Code of Conduct](CODE_OF_CONDUCT.md) before contributing.

### Translation

Help translate IceBeats into your language or improve existing translations on GitHub or by translating raw locale resource XMLs.

### Development Workflow

1. **Issue Review**: Check [open issues](https://github.com/B7ByteMe/IceBeats/issues) or create a new one describing the problem or feature
2. **Fork Repository**: Create a personal fork of the repository
3. **Feature Branch**: Create a branch for your feature (`git checkout -b feature/new-feature`)
4. **Implementation**: Implement changes following project coding conventions
5. **Testing**: Ensure code passes all tests (`./gradlew test`)
6. **Commit**: Make commits with descriptive messages (`git commit -m 'feat: add new feature'`)
7. **Push Changes**: Upload changes to your fork (`git push origin feature/new-feature`)
8. **Pull Request**: Open a PR detailing changes and referencing corresponding issue

> **Development Guidelines**: Review our [contribution guidelines](CONTRIBUTING.md) for detailed information about development process, code standards, and workflow.

---

## Acknowledgments

Special thanks to the following contributors, projects, and supporters:

- **z-huang (InnerTune)** - The foundational project of this music player
- **d0x-dev (AirBeats)** - The original fork and source of redesign components
- **drkvenom786** - UI/UX design references
- **Community translators** - Making IceBeats accessible worldwide
- **Beta testers** - Helping improve stability and usability

---

## License

**Copyright © 2025-2026 Valora · Zyxone**

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU General Public License](https://github.com/B7ByteMe/IceBeats/blob/main/LICENSE) for more details.

<div align="center">
  
[![GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white)](https://www.gnu.org/licenses/gpl-3.0)

</div>

> **Important**: Any unauthorized commercial use of this software or its derivatives constitutes a violation of the license terms.

---

<div align="center">
  <p><strong>© 2025-2026 Open Source Projects</strong></p>
  <p>Developed and maintained with passion by <a href="https://github.com/B7ByteMe">Valora · Zyxone</a></p>
  
  <br>
  
  [![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/B7ByteMe/IceBeats)
  
</div>
