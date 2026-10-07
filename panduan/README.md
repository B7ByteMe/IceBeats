# 📚 Dokumentasi & Panduan Lengkap Pengembangan IceBeats

Selamat datang di pusat dokumentasi resmi modifikasi, integrasi backend, dan build aplikasi **IceBeats**. Semua tutorial telah disusun secara runut dalam folder ini agar mudah diikuti oleh developer maupun klien.

---

## 📑 Daftar Isi Modul Panduan

| Modul | Judul Panduan | Deskripsi Ringkas |
|---|---|---|
| **[Modul 1](file:///d:/IceBeats/IceBeats/panduan/01_DAFTAR_TOOLS_DAN_PERSIAPAN.md)** | **Daftar Tools & Persiapan Lingkungan** | Spesifikasi JDK 17, Android Studio, SDK Android, Git, dan setting environment variable Windows. |
| **[Modul 2](file:///d:/IceBeats/IceBeats/panduan/02_SETUP_DATABASE_SUPABASE.md)** | **Setup Database & Storage Supabase** | Pembuatan project cloud, eksekusi SQL schema (`supabase_schema.sql`), storage bucket `backups`, dan konfigurasi API key. |
| **[Modul 3](file:///d:/IceBeats/IceBeats/panduan/03_TUTORIAL_OAUTH_LOGIN.md)** | **Integrasi Login OAuth Lengkap** | Langkah demi langkah setup Google Sign-In, Facebook Login, GitHub OAuth, dan GitLab OAuth beserta deep link `icebeats://auth-callback`. |
| **[Modul 4](file:///d:/IceBeats/IceBeats/panduan/04_REBRANDING_DAN_CUSTOM_UI.md)** | **Kustomisasi Branding & Tampilan (UI)** | Mengganti nama aplikasi, icon launcher adaptif, script resize icon, warna tema, teks bahasa, dan susunan layar Jetpack Compose. |
| **[Modul 5](file:///d:/IceBeats/IceBeats/panduan/05_PUSH_GITHUB_DAN_BUILD_APK.md)** | **Push ke GitHub & Build Menjadi APK** | Manajemen git, build otomatis cloud via GitHub Actions (`.github/workflows/build-apk.yml`), dan kompilasi lokal via `gradlew.bat`. |

---

## ⚡ Quick Start Checklist (Ringkasan Cepat)

1. [ ] Install **JDK 17** & pasang `JAVA_HOME`.
2. [ ] Siapkan Android SDK (API Level 35) dan buat file `local.properties`.
3. [ ] Buat project baru di **Supabase**, jalankan [supabase_schema.sql](file:///d:/IceBeats/IceBeats/supabase_schema.sql), lalu buat bucket storage `backups`.
4. [ ] Tempelkan `SUPABASE_URL` dan `SUPABASE_ANON_KEY` ke [SupabaseConfig.kt](file:///d:/IceBeats/IceBeats/app/src/main/java/com/valora/icebeats/supabase/SupabaseConfig.kt).
5. [ ] Tambahkan callback URL Supabase ke penyedia OAuth (Google, Facebook, GitHub, GitLab) dan tambahkan `icebeats://auth-callback` di Redirect URLs Supabase.
6. [ ] Ganti nama aplikasi di [app_name.xml](file:///d:/IceBeats/IceBeats/app/src/main/res/values/app_name.xml) dan icon di folder `mipmap-*`.
7. [ ] Push ke repository GitHub Anda:
   ```bash
   git init && git add . && git commit -m "Initial commit" && git push -u origin main
   ```
8. [ ] Ambil file APK hasil build dari tab **GitHub Actions** atau jalankan:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
