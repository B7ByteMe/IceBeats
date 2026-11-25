# 🛠️ Bagian 1: Daftar Lengkap Tools & Persiapan Lingkungan

Dokumen ini merangkum seluruh perangkat lunak (tools), runtime, dan akun pengembang yang diperlukan untuk mengelola, mengedit, dan mem-build project **IceBeats**.

---

## 📋 Daftar Software & Tools yang Diperlukan

| No | Nama Tool | Versi / Tipe | Fungsi Utama | Link Unduhan Resmi |
|---|---|---|---|---|
| 1 | **JDK (Java Development Kit)** | **Java 17 (LTS)** *(Wajib)* | Mengompilasi kode Kotlin/Java & menjalankan Gradle | [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17) atau [Azul Zulu 17](https://www.azul.com/downloads/?version=java-17-lts) |
| 2 | **Android Studio** | Versi Terbaru (Ladybug / Koala) | IDE resmi Android, emulator, editor UI, dan Asset Studio (ganti icon) | [Download Android Studio](https://developer.android.com/studio) |
| 3 | **Git for Windows** | Versi Terbaru | Version control untuk menyimpan histori kode dan push ke GitHub | [Download Git](https://git-scm.com/download/win) |
| 4 | **PowerShell** | Versi 5.1+ (Bawaan Windows) | Menjalankan build script `gradlew.bat` dan script resize icon | Bawaan Windows 10/11 |
| 5 | **VS Code / Cline** *(Opsional)* | Versi Terbaru | Editor alternatif ringan jika tidak memakai Android Studio | [Download VS Code](https://code.visualstudio.com/) |

---

## 🌐 Daftar Akun Cloud / Layanan yang Dibutuhkan

Jika Anda ingin mengaktifkan sinkronisasi cloud, backup, dan login multi-platform:

1. **GitHub Account**: Untuk menyimpan repository kode dan build APK otomatis via GitHub Actions ([Daftar GitHub](https://github.com/)).
2. **Supabase Account**: Backend database PostgreSQL, penyimpanan cloud backup, dan authentication ([Daftar Supabase](https://supabase.com/)).
3. **Google Cloud Console**: Untuk Google OAuth Client ID & sinkronisasi Google Sign-In ([Google Cloud Console](https://console.cloud.google.com/)).
4. **Meta for Developers (Facebook)**: Untuk fitur login Facebook ([Meta Developers](https://developers.facebook.com/)).
5. **GitLab Account**: Untuk fitur login OAuth GitLab ([GitLab](https://gitlab.com/)).

---

## ⚙️ Panduan Instalasi & Konfigurasi Lingkungan

### 1. Memasang JDK 17 & Setting JAVA_HOME
Proyek IceBeats dikonfigurasi secara eksplisit menggunakan **Java 17**. Jika menggunakan Java versi lain (misal Java 8 atau Java 21+ tanpa penyesuaian), proses build akan gagal.

#### Langkah-langkah:
1. Unduh installer JDK 17 (pilih versi `.msi` untuk Windows x64).
2. Jalankan instalasi hingga selesai (lokasi standar biasanya di `C:\Program Files\Eclipse Adoptium\jdk-17...`).
3. Buka **System Environment Variables** di Windows:
   - Tekan tombol `Windows + R`, ketik `sysdm.cpl`, lalu tekan Enter.
   - Pilih tab **Advanced** -> klik **Environment Variables...**.
   - Di bagian **System variables**, klik **New**:
     - **Variable name**: `JAVA_HOME`
     - **Variable value**: `C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot` (sesuaikan dengan folder instalasi Anda).
   - Di variabel `Path`, tambahkan baris baru: `%JAVA_HOME%\bin`.
4. Verifikasi di terminal PowerShell:
   ```powershell
   java -version
   ```
   *Output harus menunjukkan versi Java 17.*

---

### 2. Memasang Android Studio & Android SDK
Android Studio menyediakan SDK Android yang dibutuhkan Gradle saat proses kompilasi.

1. Install Android Studio dengan opsi standar.
2. Buka Android Studio -> **More Actions** -> **SDK Manager**.
3. Di tab **SDK Platforms**, pastikan tercentang:
   - **Android 15 (VanillaIceCream) - API Level 35**
   - **Android 14 (UpsideDownCake) - API Level 34**
4. Di tab **SDK Tools**, pastikan tercentang:
   - **Android SDK Build-Tools** (versi 35 atau 36)
   - **Android SDK Platform-Tools**
   - **Android SDK Command-line Tools (latest)**
5. Catat lokasi SDK (biasanya di `C:\Users\NamaAnda\AppData\Local\Android\Sdk`).

---

### 3. Konfigurasi `local.properties` Proyek
Supaya perintah Gradle di terminal mengenali lokasi Android SDK Anda:

1. Buat atau periksa file `local.properties` di root folder proyek:
   ```properties
   sdk.dir=C\:\\Users\\NamaAnda\\AppData\\Local\\Android\\Sdk
   ```
   *(Ganti garis miring ganda `\\` sesuai lokasi SDK di komputer Anda).*

---

### 4. Menyiapkan Git di Komputer
Buka terminal dan konfigurasi identitas pengembang Anda:
```bash
git config --global user.name "Nama Anda"
git config --global user.email "emailanda@example.com"
```

---

> ➡️ **Lanjut ke Bagian 2:** [Panduan Setup Database Supabase](file:///d:/IceBeats/IceBeats/panduan/02_SETUP_DATABASE_SUPABASE.md)
