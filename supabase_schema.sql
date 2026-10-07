-- ==============================================================================
-- ICEBEATS SUPABASE DATABASE SCHEMA & RLS POLICIES (v7.0.9)
-- Jalankan skrip ini di: Supabase Dashboard -> SQL Editor -> New Query -> Run
-- Aman dijalankan berulang kali (Idempotent & Anti-Crash)
-- ==============================================================================

-- Aktifkan ekstensi UUID & Kriptografi
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

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

-- Pastikan semua kolom tersedia jika tabel dibuat di versi terdahulu
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS profile_url TEXT;
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS total_listen_ms BIGINT DEFAULT 0;
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS weekly_listen_ms BIGINT DEFAULT 0;
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS last_updated_at BIGINT DEFAULT 0;
ALTER TABLE public.user_stats ADD COLUMN IF NOT EXISTS fcm_token TEXT;

-- Validasi Constraint Keamanan (Anti-Injeksi dan Nilai Positif)
ALTER TABLE public.user_stats 
    DROP CONSTRAINT IF EXISTS check_name_length,
    DROP CONSTRAINT IF EXISTS check_name_no_xss,
    DROP CONSTRAINT IF EXISTS check_total_listen_ms_positive,
    DROP CONSTRAINT IF EXISTS check_weekly_listen_ms_positive;

-- Sanitasi baris lama agar tidak memicu error saat penambahan constraint
UPDATE public.user_stats 
SET name = SUBSTRING(REGEXP_REPLACE(COALESCE(name, 'Pengguna IceBeats'), '[<>;]', '', 'g') FROM 1 FOR 50) 
WHERE name LIKE '%<%' OR name LIKE '%>%' OR name LIKE '%;%' OR char_length(name) > 50;

UPDATE public.user_stats 
SET name = 'Pengguna IceBeats' 
WHERE name IS NULL OR char_length(trim(name)) = 0;

UPDATE public.user_stats SET total_listen_ms = 0 WHERE total_listen_ms IS NULL OR total_listen_ms < 0;
UPDATE public.user_stats SET weekly_listen_ms = 0 WHERE weekly_listen_ms IS NULL OR weekly_listen_ms < 0;

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

-- ==============================================================================
-- 5. SKRIP PEMBERSIHAN DATA DUPLIKAT & PENCEGAHAN (RUN SECARA BERKALA / SEKALI)
-- ==============================================================================

-- A. Bersihkan duplikat di user_stats berdasarkan email:
-- Hapus entri lama jika ada email yang sama, sisakan hanya yang total_listen_ms terbesar (dan id terbesar jika seri)
DELETE FROM public.user_stats a
USING public.user_stats b
WHERE a.id <> b.id
  AND a.email IS NOT NULL 
  AND a.email <> '' 
  AND a.email = b.email 
  AND (
    a.total_listen_ms < b.total_listen_ms 
    OR (a.total_listen_ms = b.total_listen_ms AND a.id < b.id)
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



-- ==============================================================================
-- 6. TABEL SESI QR LOGIN DESKTOP (DESKTOP QR AUTH SESSIONS)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.auth_qr_sessions (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'pending',
    user_id UUID,
    user_email TEXT,
    user_name TEXT,
    user_avatar TEXT,
    auth_token TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '5 minutes')
);

ALTER TABLE public.auth_qr_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public access to auth_qr_sessions" ON public.auth_qr_sessions;
CREATE POLICY "Public access to auth_qr_sessions"
ON public.auth_qr_sessions
FOR ALL
TO public
USING (true)
WITH CHECK (true);


-- ==============================================================================
-- 7. IZIN HAPUS & PENGUNCI SKOR TERTINGGI (ANTI-RESET DARI HP)
-- ==============================================================================

-- A. Izinkan operasi DELETE untuk publik/admin
DROP POLICY IF EXISTS "Public can delete user_stats" ON public.user_stats;
CREATE POLICY "Public can delete user_stats"
ON public.user_stats
FOR DELETE
TO public
USING (true);

-- B. Trigger Pengunci Skor: Mencegah APK HP menimpa/me-reset jam dengar yang sudah tinggi
CREATE OR REPLACE FUNCTION public.protect_user_stats_highscore()
RETURNS TRIGGER AS $$
BEGIN
    -- Jika nilai total_listen_ms yang baru LEBIH KECIL dari yang sudah ada di database,
    -- jangan turunkan! Tetap pertahankan nilai yang tertinggi dari server.
    IF NEW.total_listen_ms < OLD.total_listen_ms THEN
        NEW.total_listen_ms := OLD.total_listen_ms;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_user_stats ON public.user_stats;
CREATE TRIGGER trg_protect_user_stats
BEFORE UPDATE ON public.user_stats
FOR EACH ROW
EXECUTE FUNCTION public.protect_user_stats_highscore();


-- ==============================================================================
-- 8. FITUR CHAT ANTAR PENGGUNA & BERBAGI MUSIK (v7.0.9)
-- ==============================================================================

-- A. TABEL PERCAKAPAN (CONVERSATIONS)
CREATE TABLE IF NOT EXISTS public.chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id TEXT NOT NULL,
    user2_id TEXT NOT NULL,
    last_message TEXT DEFAULT '',
    last_message_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_conversation_users UNIQUE (user1_id, user2_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_user1 ON public.chat_conversations(user1_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_conversations_user2 ON public.chat_conversations(user2_id, last_message_at DESC);

ALTER TABLE public.chat_conversations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can select conversations" ON public.chat_conversations;
DROP POLICY IF EXISTS "Public can insert conversations" ON public.chat_conversations;
DROP POLICY IF EXISTS "Public can update conversations" ON public.chat_conversations;

CREATE POLICY "Public can select conversations"
ON public.chat_conversations
FOR SELECT
TO public
USING (true);

CREATE POLICY "Public can insert conversations"
ON public.chat_conversations
FOR INSERT
TO public
WITH CHECK (true);

CREATE POLICY "Public can update conversations"
ON public.chat_conversations
FOR UPDATE
TO public
USING (true)
WITH CHECK (true);


-- B. TABEL PESAN (MESSAGES) & BERBAGI MUSIK
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.chat_conversations(id) ON DELETE CASCADE,
    sender_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    message_type TEXT NOT NULL DEFAULT 'text', -- 'text' atau 'music'
    content TEXT NOT NULL,
    media_data JSONB, -- { song_id, title, artist_name, thumbnail_url, duration }
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conv ON public.chat_messages(conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_receiver ON public.chat_messages(receiver_id, is_read);

ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can select messages" ON public.chat_messages;
DROP POLICY IF EXISTS "Public can insert messages" ON public.chat_messages;
DROP POLICY IF EXISTS "Public can update messages" ON public.chat_messages;

CREATE POLICY "Public can select messages"
ON public.chat_messages
FOR SELECT
TO public
USING (true);

CREATE POLICY "Public can insert messages"
ON public.chat_messages
FOR INSERT
TO public
WITH CHECK (
    char_length(content) >= 1
    AND char_length(content) <= 3000
);

CREATE POLICY "Public can update messages"
ON public.chat_messages
FOR UPDATE
TO public
USING (true)
WITH CHECK (true);


-- C. AKTIFKAN SUPABASE REALTIME (AGAR PESAN LANGSUNG MUNCUL TANPA REFRESH)
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_messages;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_conversations;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;



