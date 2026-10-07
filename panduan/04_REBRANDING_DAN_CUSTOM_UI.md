# 🎨 Bagian 4: Panduan Kustomisasi Nama, Icon Launcher & Tampilan Halaman (UI)

Panduan ini membahas langkah-langkah praktis untuk merombak tampilan identitas visual aplikasi sesuai merek (branding) Anda sendiri.

---

## 1. Mengubah Nama Aplikasi (App Name & Application ID)

### A. Nama Tampilan pada Launcher Android (Display Name)
Nama yang muncul di layar beranda atau laci aplikasi ponsel Anda:

📂 **File:** `app/src/main/res/values/app_name.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Ganti 'IceBeats' dengan nama aplikasi baru Anda -->
    <string name="app_name" translatable="false">NamaBaruAnda</string>
</resources>
```

---

### B. Mengubah Application ID (Package Name)
Application ID adalah identifier unik di sistem Android. Mengubah ID ini memungkinkan aplikasi Anda diinstal secara berdampingan tanpa menimpa versi lama:

📂 **File:** `app/build.gradle.kts` (sekitar baris 35-40)

```kotlin
android {
    namespace = "com.valora.icebeats" // Biarkan untuk menghindari refactor 200+ file Kotlin

    defaultConfig {
        // Ganti ID unik aplikasi Anda (gunakan huruf kecil dan tanda titik):
        applicationId = "com.merekanda.musicplayer"
        
        minSdk = 26
        targetSdk = 35
        versionCode = 176
        versionName = "1.0.0" // Nomor versi aplikasi Anda
        ...
    }
}
```

---

## 2. Mengubah Icon Aplikasi (Launcher & Adaptive Icons)

Icon aplikasi disimpan dalam 5 folder resolusi di `app/src/main/res/`:
* `mipmap-mdpi/` (48x48 px)
* `mipmap-hdpi/` (72x72 px)
* `mipmap-xhdpi/` (96x96 px)
* `mipmap-xxhdpi/` (144x144 px)
* `mipmap-xxxhdpi/` (192x192 px)

Setiap folder berisi 3 file:
1. `ic_launcher.png` (Icon standar)
2. `ic_launcher_round.png` (Icon bulat)
3. `ic_launcher_foreground.png` (Layer depan icon adaptive)

### Cara 1: Menggunakan Android Studio (Paling Direkomendasikan)
1. Buka project di **Android Studio**.
2. Pada panel navigasi Project (kiri), klik kanan folder **`app/src/main/res`**.
3. Pilih **New** ➔ **Image Asset**.
4. Di bagian **Icon Type**, pilih **Launcher Icons (Adaptive and Legacy)**.
5. Pada tab **Foreground Layer**:
   - Pilih **Asset type: Image**.
   - Klik ikon folder untuk memilih file logo baru Anda (PNG transparan minimal 512x512 px).
   - Atur slider **Resize** agar logo pas di dalam batas lingkaran aman.
6. Pada tab **Background Layer**:
   - Pilih **Asset type: Color** (atau Image jika ada gambar background sendiri).
   - Pilih warna yang sesuai dengan tema logo Anda.
7. Klik **Next** ➔ **Finish**. Android Studio akan otomatis mengenerate seluruh ukuran icon di semua folder `mipmap-*`.

### Cara 2: Menggunakan Script Otomatis `resize_icon.ps1`
Jika Anda tidak menggunakan Android Studio, project ini sudah memiliki script PowerShell instan:

1. Siapkan logo baru Anda (misal simpan di `C:\logo_baru.png`).
2. Buka file `resize_icon.ps1` di root folder proyek:
   ```powershell
   # Ubah baris ke-2 dengan path logo baru Anda:
   $srcPath = "C:\logo_baru.png"
   ```
3. Buka PowerShell di folder project, lalu jalankan:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\resize_icon.ps1
   ```
   *Script akan otomatis mengubah ukuran dan menimpa icon lama di seluruh folder mipmap.*

---

## 3. Mengubah Tampilan Halaman (UI Jetpack Compose)

Seluruh komponen tampilan antarmuka (UI) berada di dalam folder:
📂 `app/src/main/java/com/valora/icebeats/ui/`

### A. Mengubah Informasi Developer di Layar "Tentang / About"
📂 **File:** `app/src/main/java/com/valora/icebeats/ui/screens/settings/AboutScreen.kt`
* Anda dapat mengganti:
  - Nama pengembang / studio
  - Tautan GitHub atau media sosial
  - Deskripsi dan versi rilis aplikasi
  - Tautan donasi (jika ada)

### B. Mengubah Tema Warna Utama Aplikasi
📂 **File:** `app/src/main/java/com/valora/icebeats/ui/theme/Color.kt`
* Ganti kode heksadesimal warna:
  ```kotlin
  val PrimaryColor = Color(0xFF00E5FF)       // Warna tombol utama & aksen
  val SecondaryColor = Color(0xFF7C4DFF)     // Warna aksen sekunder
  val BackgroundDark = Color(0xFF0D1117)     // Warna latar belakang mode gelap
  val SurfaceDark = Color(0xFF161B22)        // Warna latar kartu/komponen
  ```

### C. Mengubah Tata Letak Halaman Utama (Home Screen)
📂 **File:** `app/src/main/java/com/valora/icebeats/ui/screens/home/HomeScreen.kt`
* Mengatur urutan section: Quick Picks, Rekomendasi Lagu, New Releases, dan Charts.

### D. Mengubah Tema Pemutar Musik (Player Screen)
📂 **Folder:** `app/src/main/java/com/valora/icebeats/ui/screens/player/themes/`
* Tersedia lebih dari 15 gaya desain pemutar musik (misalnya iOS style, Frost, Neon, Futuristic). Anda dapat menyesuaikan elemen visual pemutar musik di folder ini.

### E. Mengubah Teks & Pesan Bahasa
📂 **File:** `app/src/main/res/values/strings.xml`
* File ini berisi seluruh teks antarmuka: label tombol, judul tab, pesan toast, dan dialog konfirmasi. Cukup gunakan `Ctrl+F` untuk mencari kalimat yang ingin diganti.

---

> ➡️ **Lanjut ke Bagian 5:** [Panduan Push ke GitHub & Build Menjadi APK](file:///d:/IceBeats/IceBeats/panduan/05_PUSH_GITHUB_DAN_BUILD_APK.md)
