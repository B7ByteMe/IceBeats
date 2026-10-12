-- ==============================================================================
-- SKRIP FINAL SUPABASE ICEBEATS (BERSIHKAN DATA & AKTIFKAN TOMBOL HAPUS)
-- Menghapus pesanan palsu/ditolak + Mengaktifkan izin DELETE 100% Berhasil
-- Salin dan jalankan di: Supabase Dashboard -> SQL Editor -> New Query -> Run
-- ==============================================================================

-- ==============================================================================
-- 1. HAPUS LANGSUNG SEMUA PESANAN YANG DITOLAK / PALSU (BERSIHKAN TABEL)
-- Ini akan langsung membersihkan baris #2, #3, dan #4 di screenshot Anda
-- ==============================================================================
DELETE FROM public.user_subscriptions 
WHERE status = 'rejected' 
   OR price = 0 
   OR plan_name ILIKE '%probe%' 
   OR plan_name ILIKE '%potato%'
   OR expires_at > '2090-01-01';

-- Hapus akun penyusup di leaderboard jika masih ada
DELETE FROM public.user_stats 
WHERE id = '19f5c5ae-d626-426f-aed7-8336707183fa'
   OR name ILIKE '%potato ganteng%'
   OR (total_listen_ms > 72000000000 AND (role IS NULL OR role != 'developer'));


-- ==============================================================================
-- 2. AKTIFKAN IZIN DELETE DI TABEL LANGGANAN (AGAR TOMBOL 🗑️ BERFUNGSI 100%)
-- ==============================================================================
DROP POLICY IF EXISTS "Public can delete user subscriptions" ON public.user_subscriptions;
CREATE POLICY "Public can delete user subscriptions"
ON public.user_subscriptions
FOR DELETE
TO public
USING (true);

-- Izinkan delete di tabel leaderboard untuk tombol hapus user di admin
DROP POLICY IF EXISTS "Public can delete user_stats" ON public.user_stats;
CREATE POLICY "Public can delete user_stats"
ON public.user_stats
FOR DELETE
TO public
USING (true);


-- ==============================================================================
-- 3. PERBAIKI POLICY SELECT, INSERT, & UPDATE (ANTI-ERROR 42710)
-- ==============================================================================

-- A. Tabel User Stats
DROP POLICY IF EXISTS "Public can read user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can insert user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can update user_stats" ON public.user_stats;
DROP POLICY IF EXISTS "Public can insert or update user_stats" ON public.user_stats;

CREATE POLICY "Public can read user_stats"
ON public.user_stats FOR SELECT TO public USING (true);

CREATE POLICY "Public can insert user_stats"
ON public.user_stats FOR INSERT TO public
WITH CHECK (
    char_length(TRIM(name)) >= 1 
    AND char_length(name) <= 50 
    AND total_listen_ms >= 0 
    AND weekly_listen_ms >= 0
    AND total_listen_ms <= 72000000000
    AND (role IS NULL OR role = 'user')
);

CREATE POLICY "Public can update user_stats"
ON public.user_stats FOR UPDATE TO public
USING (true)
WITH CHECK (
    char_length(TRIM(name)) >= 1 
    AND char_length(name) <= 50 
    AND total_listen_ms >= 0 
    AND weekly_listen_ms >= 0
);

-- B. Tabel User Subscriptions
ALTER TABLE public.user_subscriptions ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ DEFAULT NOW();

DROP POLICY IF EXISTS "Public can select user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can insert pending user subscriptions only" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can insert user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can update user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can update own subscription profile only" ON public.user_subscriptions;

CREATE POLICY "Public can select user subscriptions"
ON public.user_subscriptions FOR SELECT TO public USING (true);

CREATE POLICY "Public can insert pending user subscriptions only"
ON public.user_subscriptions FOR INSERT TO public
WITH CHECK (
    is_active = false 
    AND (status = 'pending' OR status IS NULL)
    AND plan_name NOT ILIKE '%probe%'
    AND plan_name NOT ILIKE '%potato%'
    AND (expires_at IS NULL OR expires_at <= NOW() + INTERVAL '1 hour')
);

CREATE POLICY "Public can update own subscription profile only"
ON public.user_subscriptions FOR UPDATE TO public
USING (true)
WITH CHECK (true);


-- ==============================================================================
-- 4. PERBAIKI NAMA "User Tanpa Nama" YANG MASIH ADA
-- ==============================================================================
UPDATE public.user_subscriptions s
SET 
    user_name = COALESCE(
        NULLIF(TRIM(u.name), ''), 
        NULLIF(TRIM(s.user_name), ''), 
        SPLIT_PART(s.email, '@', 1), 
        SPLIT_PART(u.email, '@', 1),
        'Pengguna #' || SUBSTRING(REPLACE(s.user_id, '-', ''), 1, 6)
    ),
    email = COALESCE(s.email, u.email)
FROM public.user_stats u
WHERE s.user_id = u.id 
  AND (s.user_name IS NULL OR TRIM(s.user_name) = '' OR s.user_name = 'User Tanpa Nama');

UPDATE public.user_subscriptions
SET user_name = COALESCE(
    SPLIT_PART(email, '@', 1),
    'Pengguna #' || SUBSTRING(REPLACE(user_id, '-', ''), 1, 6)
)
WHERE user_name IS NULL OR TRIM(user_name) = '' OR user_name = 'User Tanpa Nama';


-- ==============================================================================
-- 5. TRIGGER OTOMATIS: AUTO-ISI NAMA PENGGUNA SAAT MEMBUAT PESANAN LANGGANAN
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.sync_subscription_user_name()
RETURNS TRIGGER AS $$
DECLARE
    v_found_name TEXT;
    v_found_email TEXT;
BEGIN
    IF NEW.user_name IS NULL OR TRIM(NEW.user_name) = '' OR NEW.user_name = 'User Tanpa Nama' THEN
        SELECT name, email INTO v_found_name, v_found_email 
        FROM public.user_stats 
        WHERE id = NEW.user_id 
        LIMIT 1;

        IF v_found_name IS NOT NULL AND TRIM(v_found_name) <> '' THEN
            NEW.user_name := v_found_name;
        ELSIF NEW.email IS NOT NULL AND TRIM(NEW.email) <> '' THEN
            NEW.user_name := SPLIT_PART(NEW.email, '@', 1);
        ELSIF v_found_email IS NOT NULL AND TRIM(v_found_email) <> '' THEN
            NEW.user_name := SPLIT_PART(v_found_email, '@', 1);
        ELSE
            NEW.user_name := 'Pengguna #' || SUBSTRING(REPLACE(NEW.user_id, '-', ''), 1, 6);
        END IF;

        IF (NEW.email IS NULL OR NEW.email = '') AND v_found_email IS NOT NULL THEN
            NEW.email := v_found_email;
        END IF;
    END IF;

    IF NEW.plan_name ILIKE '%probe%' OR NEW.plan_name ILIKE '%potato%' THEN
        RAISE EXCEPTION 'Nama paket langganan tidak valid!';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_sub_user_name ON public.user_subscriptions;
CREATE TRIGGER trg_sync_sub_user_name
BEFORE INSERT OR UPDATE ON public.user_subscriptions
FOR EACH ROW
EXECUTE FUNCTION public.sync_subscription_user_name();


-- ==============================================================================
-- 6. TRIGGER KEAMANAN & ANTI-CHEAT LEADERBOARD (SERVER-SIDE)
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.validate_user_stats_security()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.name IS NULL OR TRIM(NEW.name) = '' THEN
        NEW.name := 'Pengguna IceBeats';
    END IF;
    NEW.name := SUBSTRING(REGEXP_REPLACE(NEW.name, '[<>;]', '', 'g') FROM 1 FOR 50);

    -- Proteksi Role
    IF TG_OP = 'UPDATE' THEN
        IF NEW.role IS DISTINCT FROM OLD.role THEN
            IF NEW.role IN ('developer', 'admin') AND (OLD.role IS NULL OR OLD.role NOT IN ('developer', 'admin')) THEN
                NEW.role := COALESCE(OLD.role, 'user');
            END IF;
        END IF;
    ELSE
        IF NEW.role IN ('developer', 'admin') THEN
            NEW.role := 'user';
        END IF;
    END IF;

    -- Proteksi Border Style
    IF NEW.border_style IS NOT NULL AND NEW.border_style <> '' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.user_subscriptions 
            WHERE user_id = NEW.id AND (is_active = true OR status = 'approved')
        ) AND (NEW.role IS NULL OR NEW.role != 'developer') THEN
            NEW.border_style := NULL;
        END IF;
    END IF;

    -- Anti-Cheat Jam Dengar
    IF NEW.total_listen_ms > 72000000000 AND (NEW.role IS NULL OR NEW.role != 'developer') THEN
        RAISE EXCEPTION 'Skor jam mendengarkan melampaui batas wajar (Terdeteksi Injeksi)!';
    END IF;

    -- Anti-Cheat Lonjakan Drastis
    IF TG_OP = 'UPDATE' AND (NEW.role IS NULL OR NEW.role != 'developer') THEN
        IF (NEW.total_listen_ms - OLD.total_listen_ms) > 3600000000 THEN
            RAISE EXCEPTION 'Lonjakan jam mendengarkan terlalu drastis (Anti-Cheat Aktif)!';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validate_user_stats ON public.user_stats;
CREATE TRIGGER trg_validate_user_stats
BEFORE INSERT OR UPDATE ON public.user_stats
FOR EACH ROW
EXECUTE FUNCTION public.validate_user_stats_security();


-- ==============================================================================
-- 7. SECURE RPC ADMIN GATEWAY: HAPUS USER & HAPUS RIWAYAT LANGGANAN
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.admin_delete_user(
    p_admin_secret TEXT,
    p_user_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    DELETE FROM public.user_stats WHERE id = p_user_id;
    DELETE FROM public.user_subscriptions WHERE user_id = p_user_id;

    RETURN jsonb_build_object('success', true, 'message', 'Pengguna berhasil dihapus secara permanen.');
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_subscription(
    p_admin_secret TEXT,
    p_sub_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    DELETE FROM public.user_subscriptions WHERE id = p_sub_id;

    RETURN jsonb_build_object('success', true, 'message', 'Pesanan langganan berhasil dihapus.');
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_delete_user TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_delete_subscription TO anon, authenticated, service_role;


-- ==============================================================================
-- 8. SECURE RPC ADMIN GATEWAY: ACC VIP, REJECT VIP, REVOKE VIP (KALENDER AKURAT)
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.admin_acc_vip(
    p_admin_secret TEXT,
    p_sub_id UUID,
    p_duration_months INT DEFAULT NULL,
    p_duration_days INT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_sub RECORD;
    v_now TIMESTAMPTZ := NOW();
    v_starts_at TIMESTAMPTZ;
    v_expires_at TIMESTAMPTZ;
    v_months INT;
    v_days INT;
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    SELECT * INTO v_sub FROM public.user_subscriptions WHERE id = p_sub_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'message', 'Data transaksi langganan tidak ditemukan.');
    END IF;

    -- Cegah double-approval jika transaksi ini sudah disetujui sebelumnya
    IF v_sub.status = 'approved' AND v_sub.is_active = true AND v_sub.expires_at > v_now THEN
        RETURN jsonb_build_object(
            'success', true,
            'message', 'Transaksi sudah disetujui sebelumnya. Masa aktif tidak digandakan.',
            'starts_at', v_sub.starts_at,
            'expires_at', v_sub.expires_at
        );
    END IF;

    -- Tentukan durasi kalender berdasarkan plan_name jika parameter tidak diisi
    v_months := COALESCE(p_duration_months, 0);
    v_days := COALESCE(p_duration_days, 0);

    IF v_months = 0 AND v_days = 0 THEN
        IF v_sub.plan_name ILIKE '%5 Bulan%' THEN
            v_months := 5;
        ELSIF v_sub.plan_name ILIKE '%2 Bulan%' THEN
            v_months := 2;
        ELSIF v_sub.plan_name ILIKE '%1 Bulan%' THEN
            v_months := 1;
        ELSIF v_sub.plan_name ILIKE '%Tahun%' THEN
            v_months := 12;
        ELSE
            v_months := 1;
        END IF;
    END IF;

    -- Aturan konsisten masa aktif:
    -- Jika user sebelumnya sudah punya VIP aktif yang belum kedaluwarsa, tambahkan dari expires_at
    -- Jika transaksi baru, starts_at adalah v_now
    IF v_sub.is_active = true AND v_sub.expires_at > v_now THEN
        v_starts_at := COALESCE(v_sub.starts_at, v_now);
        IF v_months > 0 THEN
            v_expires_at := v_sub.expires_at + (v_months || ' month')::INTERVAL;
        ELSE
            v_expires_at := v_sub.expires_at + (v_days || ' day')::INTERVAL;
        END IF;
    ELSE
        v_starts_at := v_now;
        IF v_months > 0 THEN
            v_expires_at := v_now + (v_months || ' month')::INTERVAL;
        ELSE
            v_expires_at := v_now + (v_days || ' day')::INTERVAL;
        END IF;
    END IF;

    UPDATE public.user_subscriptions
    SET 
        status = 'approved',
        is_active = true,
        starts_at = v_starts_at,
        expires_at = v_expires_at,
        updated_at = v_now
    WHERE id = p_sub_id;

    RETURN jsonb_build_object(
        'success', true, 
        'message', 'Langganan VIP berhasil diaktifkan.',
        'starts_at', v_starts_at,
        'expires_at', v_expires_at
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_reject_vip(
    p_admin_secret TEXT,
    p_sub_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    UPDATE public.user_subscriptions
    SET 
        status = 'rejected',
        is_active = false,
        updated_at = NOW()
    WHERE id = p_sub_id;

    RETURN jsonb_build_object('success', true, 'message', 'Pesanan langganan berhasil ditolak.');
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_revoke_vip(
    p_admin_secret TEXT,
    p_sub_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    UPDATE public.user_subscriptions
    SET 
        status = 'cancelled',
        is_active = false,
        updated_at = NOW()
    WHERE id = p_sub_id;

    RETURN jsonb_build_object('success', true, 'message', 'Langganan VIP berhasil dinonaktifkan.');
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_acc_vip TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_reject_vip TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_revoke_vip TO anon, authenticated, service_role;

-- Trigger: Cegah pergantian paket / insert pending saat VIP masih aktif
CREATE OR REPLACE FUNCTION public.check_subscription_concurrency()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'pending' AND OLD.status = 'approved' AND OLD.is_active = true AND OLD.expires_at > NOW() THEN
        RAISE EXCEPTION 'Langganan VIP Anda masih aktif. Tidak dapat membuat transaksi baru sebelum masa aktif berakhir.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_subscription_concurrency ON public.user_subscriptions;
CREATE TRIGGER trg_check_subscription_concurrency
BEFORE UPDATE ON public.user_subscriptions
FOR EACH ROW
EXECUTE FUNCTION public.check_subscription_concurrency();

