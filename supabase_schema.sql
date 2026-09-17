-- ==============================================================================
-- ICEBEATS SUPABASE DATABASE SCHEMA & RLS POLICIES
-- Jalankan skrip ini di: Supabase Dashboard -> SQL Editor -> New Query -> Run
-- ==============================================================================

-- 1. TABEL LAGU FAVORIT (USER FAVORITES)
CREATE TABLE IF NOT EXISTS public.user_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    song_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist_name TEXT,
    album_name TEXT,
    thumbnail_url TEXT,
    duration INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_song UNIQUE (user_id, song_id)
);

-- Aktifkan Row Level Security (RLS)
ALTER TABLE public.user_favorites ENABLE ROW LEVEL SECURITY;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Users can read their own favorites" ON public.user_favorites;
DROP POLICY IF EXISTS "Users can insert or update their own favorites" ON public.user_favorites;

-- Policy: Pengguna hanya dapat membaca data favorit miliknya sendiri
CREATE POLICY "Users can read their own favorites"
ON public.user_favorites
FOR SELECT
USING (auth.uid() = user_id);

-- Policy: Pengguna dapat menambah atau memperbarui favorit miliknya sendiri
CREATE POLICY "Users can insert or update their own favorites"
ON public.user_favorites
FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);


-- 2. TABEL PLAYLIST PENGGUNA (USER PLAYLISTS)
CREATE TABLE IF NOT EXISTS public.user_playlists (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    playlist_id TEXT NOT NULL,
    name TEXT NOT NULL,
    song_count INTEGER DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_playlist UNIQUE (user_id, playlist_id)
);

-- Aktifkan Row Level Security (RLS)
ALTER TABLE public.user_playlists ENABLE ROW LEVEL SECURITY;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Users can read their own playlists" ON public.user_playlists;
DROP POLICY IF EXISTS "Users can insert or update their own playlists" ON public.user_playlists;

-- Policy: Pengguna hanya dapat membaca playlist miliknya sendiri
CREATE POLICY "Users can read their own playlists"
ON public.user_playlists
FOR SELECT
USING (auth.uid() = user_id);

-- Policy: Pengguna dapat menambah atau memperbarui playlist miliknya sendiri
CREATE POLICY "Users can insert or update their own playlists"
ON public.user_playlists
FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);


-- 2b. TABEL LAGU DI DALAM PLAYLIST (USER PLAYLIST SONGS)
CREATE TABLE IF NOT EXISTS public.user_playlist_songs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    playlist_id TEXT NOT NULL,
    song_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist_name TEXT,
    album_name TEXT,
    thumbnail_url TEXT,
    duration INTEGER DEFAULT 0,
    position INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_playlist_song UNIQUE (user_id, playlist_id, song_id)
);

-- Aktifkan Row Level Security (RLS)
ALTER TABLE public.user_playlist_songs ENABLE ROW LEVEL SECURITY;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Users can read their own playlist songs" ON public.user_playlist_songs;
DROP POLICY IF EXISTS "Users can insert or update their own playlist songs" ON public.user_playlist_songs;

-- Policy: Pengguna hanya dapat membaca lagu playlist miliknya sendiri
CREATE POLICY "Users can read their own playlist songs"
ON public.user_playlist_songs
FOR SELECT
USING (auth.uid() = user_id);

-- Policy: Pengguna dapat menambah, memperbarui, atau menghapus lagu playlist miliknya
CREATE POLICY "Users can insert or update their own playlist songs"
ON public.user_playlist_songs
FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);


-- 2c. TABEL RIWAYAT PUTAR LAGU (USER STATS & HISTORY)
CREATE TABLE IF NOT EXISTS public.user_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    song_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    album_name TEXT,
    thumbnail_url TEXT,
    play_time BIGINT NOT NULL DEFAULT 0,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_song_event UNIQUE (user_id, song_id, timestamp)
);

-- Index performa untuk mempercepat restore riwayat dan stats
CREATE INDEX IF NOT EXISTS idx_user_events_user_timestamp ON public.user_events (user_id, timestamp DESC);

-- Aktifkan Row Level Security (RLS)
ALTER TABLE public.user_events ENABLE ROW LEVEL SECURITY;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Users can read their own events" ON public.user_events;
DROP POLICY IF EXISTS "Users can insert or update their own events" ON public.user_events;

-- Policy: Pengguna hanya dapat membaca riwayat event miliknya sendiri
CREATE POLICY "Users can read their own events"
ON public.user_events
FOR SELECT
USING (auth.uid() = user_id);

-- Policy: Pengguna dapat menambah atau memperbarui riwayat event miliknya sendiri
CREATE POLICY "Users can insert or update their own events"
ON public.user_events
FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);


-- 2d. TABEL ARTIS FAVORIT (USER FAVORITE ARTISTS)
CREATE TABLE IF NOT EXISTS public.user_favorite_artists (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    artist_id TEXT NOT NULL,
    name TEXT NOT NULL,
    thumbnail_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_artist UNIQUE (user_id, artist_id)
);

ALTER TABLE public.user_favorite_artists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read their own artists" ON public.user_favorite_artists;
DROP POLICY IF EXISTS "Users can insert or update their own artists" ON public.user_favorite_artists;

CREATE POLICY "Users can read their own artists"
ON public.user_favorite_artists FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert or update their own artists"
ON public.user_favorite_artists FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);


-- 2e. TABEL ALBUM TERSIMPAN (USER SAVED ALBUMS)
CREATE TABLE IF NOT EXISTS public.user_saved_albums (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    album_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist_name TEXT,
    year INT,
    thumbnail_url TEXT,
    song_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_album UNIQUE (user_id, album_id)
);

ALTER TABLE public.user_saved_albums ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read their own albums" ON public.user_saved_albums;
DROP POLICY IF EXISTS "Users can insert or update their own albums" ON public.user_saved_albums;

CREATE POLICY "Users can read their own albums"
ON public.user_saved_albums FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert or update their own albums"
ON public.user_saved_albums FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);


-- 3. STORAGE BUCKET UNTUK FILE BACKUP
INSERT INTO storage.buckets (id, name, public)
VALUES ('backups', 'backups', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Users can upload backups" ON storage.objects;
DROP POLICY IF EXISTS "Users can read backups" ON storage.objects;
DROP POLICY IF EXISTS "Users can update backups" ON storage.objects;
DROP POLICY IF EXISTS "Public can upload backups" ON storage.objects;
DROP POLICY IF EXISTS "Public can read backups" ON storage.objects;
DROP POLICY IF EXISTS "Public can update backups" ON storage.objects;

-- Policy Storage: Izinkan aplikasi mengupload file backup (Insert)
CREATE POLICY "Public can upload backups"
ON storage.objects
FOR INSERT
TO public
WITH CHECK (bucket_id = 'backups');

-- Policy Storage: Izinkan aplikasi mendownload file backup (Select)
CREATE POLICY "Public can read backups"
ON storage.objects
FOR SELECT
TO public
USING (bucket_id = 'backups');

-- Policy Storage: Izinkan aplikasi memperbarui file backup (Update)
CREATE POLICY "Public can update backups"
ON storage.objects
FOR UPDATE
TO public
USING (bucket_id = 'backups');


-- 4. TABEL GLOBAL STATS (LEADERBOARD)
CREATE TABLE IF NOT EXISTS public.user_stats (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    profile_url TEXT,
    email TEXT,
    total_listen_ms BIGINT DEFAULT 0,
    weekly_listen_ms BIGINT DEFAULT 0,
    last_updated_at BIGINT DEFAULT 0,
    fcm_token TEXT
);

-- Validasi Constraint Keamanan (Anti-Injeksi dan Nilai Tidak Masuk Akal)
ALTER TABLE public.user_stats 
    DROP CONSTRAINT IF EXISTS check_name_length,
    DROP CONSTRAINT IF EXISTS check_name_no_xss,
    DROP CONSTRAINT IF EXISTS check_total_listen_ms_positive,
    DROP CONSTRAINT IF EXISTS check_weekly_listen_ms_positive;

ALTER TABLE public.user_stats
    ADD CONSTRAINT check_name_length CHECK (char_length(name) >= 1 AND char_length(name) <= 50),
    ADD CONSTRAINT check_name_no_xss CHECK (name NOT LIKE '%<%' AND name NOT LIKE '%>%' AND name NOT LIKE '%;%'),
    ADD CONSTRAINT check_total_listen_ms_positive CHECK (total_listen_ms >= 0),
    ADD CONSTRAINT check_weekly_listen_ms_positive CHECK (weekly_listen_ms >= 0);

-- Aktifkan Row Level Security (RLS)
ALTER TABLE public.user_stats ENABLE ROW LEVEL SECURITY;

-- Hapus policy lama jika ada
DROP POLICY IF EXISTS "Public can read user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can insert or update user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can insert user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can update user_stats" ON public.user_stats;

-- Policy 1: Publik dapat membaca leaderboard (SELECT)
CREATE POLICY "Public can read user_stats"
ON public.user_stats
FOR SELECT
TO public
USING (true);

-- Policy 2: Publik dapat menambah data (INSERT) dengan validasi
CREATE POLICY "Public can insert user_stats"
ON public.user_stats
FOR INSERT
TO public
WITH CHECK (
    char_length(name) >= 1 
    AND char_length(name) <= 50 
    AND total_listen_ms >= 0 
    AND weekly_listen_ms >= 0
);

-- Policy 3: Publik dapat memperbarui data (UPDATE) dengan validasi
CREATE POLICY "Public can update user_stats"
ON public.user_stats
FOR UPDATE
TO public
USING (true)
WITH CHECK (
    char_length(name) >= 1 
    AND char_length(name) <= 50 
    AND total_listen_ms >= 0 
    AND weekly_listen_ms >= 0
);

-- CATATAN: Operasi DELETE sengaja TIDAK diizinkan untuk publik/anonim
-- guna mencegah penghapusan massal papan peringkat oleh penyerang.


-- ==============================================================================
-- 5. SKRIP PEMBERSIHAN DATA DUPLIKAT & PENCEGAHAN (RUN SECARA BERKALA / SEKALI)
-- ==============================================================================

-- A. Bersihkan duplikat di user_stats:
-- Hapus entri lama jika ada nama atau email yang sama, sisakan hanya yang total_listen_ms terbesar
DELETE FROM public.user_stats a
USING public.user_stats b
WHERE a.id <> b.id
  AND (
    (a.email IS NOT NULL AND a.email <> '' AND a.email = b.email AND a.total_listen_ms <= b.total_listen_ms)
    OR (a.name = b.name AND a.total_listen_ms < b.total_listen_ms)
  );

-- Buat Unique Index untuk Email di user_stats (1 email hanya punya 1 baris rank)
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_stats_unique_email 
ON public.user_stats (email) 
WHERE email IS NOT NULL AND email <> '';

-- B. Bersihkan duplikat di user_events:
-- Jika lagu yang sama tercatat berulang kali dalam interval waktu 1 menit untuk user yang sama
DELETE FROM public.user_events a
USING public.user_events b
WHERE a.id > b.id
  AND a.user_id = b.user_id
  AND a.song_id = b.song_id
  AND DATE_TRUNC('minute', a.timestamp) = DATE_TRUNC('minute', b.timestamp);


