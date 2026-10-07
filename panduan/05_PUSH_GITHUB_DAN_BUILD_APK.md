# 🚀 Bagian 5: Panduan Push ke GitHub & Build Menjadi APK

Panduan ini mencakup cara mengunggah kode sumber ke **GitHub** dan mem-build project menjadi file **APK siap instal** menggunakan dua metode: secara otomatis di cloud (**GitHub Actions**) atau secara lokal di laptop (**Gradle Wrapper / Android Studio**).

---

## 1. Persiapan Sebelum Push ke GitHub

Sebelum mengunggah, bersihkan cache build lokal agar repository tidak membengkak:

1. Buka terminal (PowerShell) di folder proyek:
   ```powershell
   .\gradlew.bat clean
   ```
2. Pastikan file `.gitignore` sudah mengabaikan folder-folder berat:
   * `.gradle/`
   * `build/` dan `*/build/`
   * `.idea/`
   * `.kotlin/`
   * `local.properties` (agar path SDK pribadi tidak terunggah)

---

## 2. Langkah Push ke Repository GitHub Baru

### A. Buat Repository Baru di GitHub
1. Buka [GitHub New Repository](https://github.com/new).
2. Beri nama repository (misal: `MyMusicApp`).
3. Pilih visibilitas **Public** atau **Private**.
4. **Jangan centang** "Initialize this repository with a README" (karena kita sudah punya file lokal).
5. Klik **Create repository**.
6. Salin URL repository Anda (contoh: `https://github.com/USERNAME/MyMusicApp.git`).

### B. Eksekusi Perintah Git di Terminal
Buka PowerShell di folder project dan jalankan baris demi baris:

```bash
# 1. Inisialisasi Git lokal jika belum ada
git init

# 2. Tambahkan semua file proyek
git add .

# 3. Buat commit pertama
git commit -m "feat: initial custom release"

# 4. Ganti nama branch menjadi main
git branch -M main

# 5. Hubungkan ke repository GitHub Anda
# (Jika sebelumnya sudah ada remote origin, hapus dulu dengan: git remote remove origin)
git remote add origin https://github.com/USERNAME/MyMusicApp.git

# 6. Unggah kode ke GitHub
git push -u origin main
```

---

## 3. Metode Build APK

### 🌟 Metode 1: Build Otomatis di Cloud via GitHub Actions (Rekomendasi)

Proyek ini telah memiliki file alur kerja di:
📂 [.github/workflows/build-apk.yml](file:///d:/IceBeats/IceBeats/.github/workflows/build-apk.yml)

Begitu Anda melakukan `git push`, server GitHub Actions akan otomatis mengompilasi APK tanpa membebani RAM atau CPU laptop Anda.

#### Cara Mengunduh Hasil APK dari GitHub:
1. Buka repository Anda di browser GitHub.
2. Klik tab **Actions** di menu atas.
3. Klik alur kerja (**Workflow run**) paling atas yang bertuliskan *"Build Android APK"*.
4. Tunggu sampai proses build selesai (muncul ikon centang hijau ✅).
5. Scroll ke bagian paling bawah halaman pada bagian **Artifacts**.
6. Klik **App-Debug-APK** untuk mendownload file `.zip`.
7. Ekstrak zip tersebut, dan Anda akan mendapatkan file **`app-debug.apk`** yang langsung bisa dikirim ke HP Android Anda!

---

### 💻 Metode 2: Build APK Secara Lokal di Komputer (PowerShell)

Jika Anda ingin langsung membuat file APK di laptop tanpa koneksi internet:

#### A. Membuat APK Debug (Cepat & Langsung Bisa Diinstal)
```powershell
.\gradlew.bat assembleDebug
```
* **Lokasi file hasil:**
  📁 `app/build/outputs/apk/debug/app-debug.apk`

#### B. Membuat APK Release (Optimal & Terkompresi)
```powershell
.\gradlew.bat assembleRelease
```
* **Lokasi file hasil:**
  📁 `app/build/outputs/apk/release/app-release-unsigned.apk` (atau `app-release.apk`)

---

### 📱 Metode 3: Build Melalui Menu Android Studio
1. Buka proyek di Android Studio.
2. Tunggu proses **Gradle Sync** selesai.
3. Di bilah menu atas, klik **Build** ➔ **Build Bundle(s) / APK(s)** ➔ **Build APK(s)**.
4. Ketika proses selesai, akan muncul pop-up notifikasi di pojok kanan bawah bertuliskan *"APK(s) generated successfully"*.
5. Klik link **locate** untuk langsung membuka folder letak file APK tersebut di Windows Explorer.

---

## ⚠️ Solusi Masalah Umum (Troubleshooting)

| Gejala Error | Penyebab | Solusi |
|---|---|---|
| `'JAVA_HOME' is not set` | Path Java belum disetel | Pasang JDK 17 dan atur environment variable `JAVA_HOME`. |
| `SDK location not found` | Path Android SDK hilang | Buat file `local.properties` dan isi `sdk.dir=C\:\\Users\\...\\AppData\\Local\\Android\\Sdk`. |
| `OutOfMemoryError: Java heap space` | Memori RAM Gradle kurang | Tambahkan `org.gradle.jvmargs=-Xmx4096m` di file `gradle.properties`. |
| `Permission denied (gradlew)` | Izin eksekusi di Linux/GitHub hilang | Jalankan `git update-index --chmod=+x gradlew` lalu commit & push ulang. |

---

Selamat! Anda kini telah menguasai alur penuh dari kustomisasi nama, icon, database, login multi-platform, hingga menghasilkan file APK final.
