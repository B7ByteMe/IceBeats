# 🗄️ Bagian 2: Panduan Lengkap Setup Database & Storage Supabase

Aplikasi **IceBeats** menggunakan **Supabase** (layanan PostgreSQL open-source) sebagai backend cloud untuk:
* Menyimpan daftar lagu favorit pengguna (`user_favorites`).
* Menyimpan playlist kustom pengguna (`user_playlists` & `playlist_songs`).
* Sinkronisasi riwayat putar dan statistik mendengarkan (`user_stats`).
* Penyimpanan file cadangan cloud backup (`backups` storage bucket).
* Otentikasi pengguna (Email, Google, Facebook, GitHub, GitLab).

---

## 1. Mendaftar & Membuat Project Baru di Supabase

1. Kunjungi situs resmi: [https://supabase.com/](https://supabase.com/)
2. Klik tombol **Start your project** dan login menggunakan akun GitHub Anda.
3. Di halaman Dashboard, klik **New Project**.
4. Isi data project:
   - **Name**: `IceBeats Database` (atau nama aplikasi Anda)
   - **Database Password**: Buat password yang kuat dan catat di tempat aman.
   - **Region**: Pilih region terdekat (misal: `Southeast Asia (Singapore)` agar latency sangat rendah).
   - **Pricing Plan**: Pilih **Free Plan**.
5. Klik **Create new project** dan tunggu 1-2 menit hingga status database menjadi `Active`.

---

## 2. Mengambil URL & API Key Proyek

Untuk menghubungkan aplikasi ke project Supabase baru Anda:

1. Di menu sidebar kiri dashboard Supabase, klik ikon **Settings (Project Settings)** di pojok kiri bawah.
2. Pilih submenu **API**.
3. Di panel **Project API keys**, Anda akan melihat 2 kunci penting:
   * **Project URL**: contoh `https://abcdefghijkl.supabase.co`
   * **anon public key**: string token panjang berawalan `eyJhbGciOi...`
4. Buka file proyek Anda di:
   📂 `app/src/main/java/com/valora/icebeats/supabase/SupabaseConfig.kt`
5. Ganti nilai konfigurasi dengan URL dan Anon Key Anda:
   ```kotlin
   package com.valora.icebeats.supabase

   object SupabaseConfig {
       const val SUPABASE_URL = "https://abcdefghijkl.supabase.co"
       const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsIn..."
   }
   ```

---

## 3. Menjalankan Skrip Schema Database (`supabase_schema.sql`)

Project ini sudah dilengkapi dengan skrip SQL lengkap di file:
📂 [supabase_schema.sql](file:///d:/IceBeats/IceBeats/supabase_schema.sql)

Skrip ini akan otomatis membuat tabel-tabel berikut beserta keamanan **Row Level Security (RLS)**:
* `public.user_favorites`
* `public.user_playlists`
* `public.playlist_songs`
* `public.user_stats`
* `public.user_playback_events`
* `public.user_artist_bookmarks`
* `public.user_album_bookmarks`

### Langkah Eksekusi:
1. Buka file `supabase_schema.sql` di proyek Anda, lalu **Salin Seluruh Isinya (Ctrl+A -> Ctrl+C)**.
2. Di dashboard Supabase, buka menu **SQL Editor** pada sidebar kiri.
3. Klik **New query**.
4. Tempelkan (Paste) seluruh skrip SQL ke dalam editor.
5. Klik tombol **Run** (atau tekan `Ctrl + Enter`).
6. Pastikan muncul notifikasi **Success. No rows returned**.

---

## 4. Membuat Storage Bucket untuk Fitur Cloud Backup

Aplikasi IceBeats memiliki fitur cadangan cloud (`CloudBackupClient.kt`) yang mengunggah file zip backup ke Supabase Storage. Anda harus membuat bucket bernama `backups`:

1. Di dashboard Supabase, buka menu **Storage** pada sidebar kiri.
2. Klik tombol **New bucket**.
3. Isi konfigurasi:
   - **Bucket name**: `backups` *(Huruf kecil semua, jangan salah eja)*
   - **Public bucket**: Aktifkan centang **Public** (agar aplikasi dapat mengunduh file cadangan saat restore).
4. Klik **Save**.
5. Klik tab **Policies** pada bucket `backups` -> tambahkan kebijakan agar user yang sudah login dapat membaca dan mengunggah:
   - Pilih template **Enable read/insert access for authenticated users** atau jalankan query berikut di SQL Editor:
   ```sql
   -- Izinkan akses bucket backups untuk publik/pengguna terautentikasi
   CREATE POLICY "Public Access" ON storage.objects FOR SELECT USING (bucket_id = 'backups');
   CREATE POLICY "Authenticated Upload" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'backups');
   ```

---

## 5. Mengatur Redirect URL untuk Autentikasi Aplikasi Android

Supabase memerlukan daftar alamat kembalian (Redirect URL) yang sah agar aplikasi Android dapat menerima token login dari browser:

1. Di dashboard Supabase, buka menu **Authentication** -> **URL Configuration**.
2. Di bagian **Site URL**, biarkan default atau isi domain Anda (contoh `https://icebeats.pages.dev` atau `http://localhost:3000`).
3. Di bagian **Redirect URLs**, klik **Add URL**, lalu tambahkan URL skema deep link aplikasi:
   ```
   icebeats://auth-callback
   ```
4. Klik **Save**.

> [!IMPORTANT]
> URL `icebeats://auth-callback` ini wajib ditambahkan persis seperti di atas. Skema ini sesuai dengan filter intent di [AndroidManifest.xml](file:///d:/IceBeats/IceBeats/app/src/main/AndroidManifest.xml) baris 94-100.

---

> ➡️ **Lanjut ke Bagian 3:** [Panduan Integrasi Login OAuth (Google, Facebook, GitHub, GitLab)](file:///d:/IceBeats/IceBeats/panduan/03_TUTORIAL_OAUTH_LOGIN.md)
