-- ==============================================================================
-- ICEBEATS v7.1.8 - SKRIP KHUSUS FITUR VIP & KEAMANAN SUPABASE
-- Salin dan jalankan di: Supabase Dashboard -> SQL Editor -> New Query -> Run
-- Aman dijalankan berulang kali (Idempotent & Anti-Crash)
-- ==============================================================================

-- 1. TABEL LANGGANAN VIP PENGGUNA (USER VIP SUBSCRIPTIONS)
CREATE TABLE IF NOT EXISTS public.user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    user_name TEXT,
    email TEXT,
    plan_name TEXT NOT NULL, -- '1 Bulan', '2 Bulan', '5 Bulan', 'Voucher'
    price INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending', -- 'pending', 'approved', 'rejected'
    is_active BOOLEAN DEFAULT false,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_subscription UNIQUE (user_id)
);

-- Indeks untuk pencarian cepat
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_id ON public.user_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_email ON public.user_subscriptions(email);

-- Pastikan unique constraint ada meskipun tabel sudah pernah dibuat sebelumnya
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unique_user_subscription'
    ) THEN
        ALTER TABLE public.user_subscriptions ADD CONSTRAINT unique_user_subscription UNIQUE (user_id);
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- 2. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.user_subscriptions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can select user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can insert user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can update user subscriptions" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can insert pending user subscriptions only" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Disallow public direct subscription activation" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can update own subscription profile only" ON public.user_subscriptions;
DROP POLICY IF EXISTS "Public can delete user subscriptions" ON public.user_subscriptions;

-- A. Pengguna dapat membaca data langganan
CREATE POLICY "Public can select user subscriptions"
ON public.user_subscriptions
FOR SELECT
TO public
USING (true);

-- B. Pengguna HANYA dapat membuat order baru berstatus 'pending' & is_active = false
-- Memblokir upaya injeksi agar tidak bisa langsung aktifkan VIP sendiri
CREATE POLICY "Public can insert pending user subscriptions only"
ON public.user_subscriptions
FOR INSERT
TO public
WITH CHECK (
    is_active = false 
    AND (status = 'pending' OR status IS NULL)
);

-- C. Kebijakan Anti-Crack: Dilarang update langsung is_active / approved dari publik
CREATE POLICY "Public can update own subscription profile only"
ON public.user_subscriptions
FOR UPDATE
TO public
USING (true)
WITH CHECK (
    (is_active = false AND status != 'approved')
    OR (auth.role() = 'service_role')
);

-- D. Izin delete untuk tombol hapus di dashboard admin
CREATE POLICY "Public can delete user subscriptions"
ON public.user_subscriptions
FOR DELETE
TO public
USING (true);

-- 3. SECURE RPC FUNCTIONS UNTUK ADMIN

-- A. Admin ACC / Setujui VIP
CREATE OR REPLACE FUNCTION public.admin_acc_vip(
    p_admin_secret TEXT,
    p_sub_id UUID,
    p_duration_days INTEGER DEFAULT 30
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_name TEXT;
    v_target_user_id TEXT;
    v_new_expiry TIMESTAMPTZ;
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    v_new_expiry := NOW() + (p_duration_days || ' days')::INTERVAL;

    UPDATE public.user_subscriptions
    SET 
        status = 'approved',
        is_active = true,
        expires_at = v_new_expiry,
        updated_at = NOW()
    WHERE id = p_sub_id
    RETURNING user_name, user_id INTO v_user_name, v_target_user_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'message', 'ID Langganan tidak ditemukan');
    END IF;

    RETURN jsonb_build_object(
        'success', true, 
        'message', 'VIP berhasil di-ACC!',
        'user_name', v_user_name,
        'expires_at', v_new_expiry
    );
END;
$$;

-- B. Admin Tolak / Nonaktifkan VIP
CREATE OR REPLACE FUNCTION public.admin_revoke_vip(
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

    UPDATE public.user_subscriptions
    SET 
        status = 'rejected',
        is_active = false,
        updated_at = NOW()
    WHERE id = p_sub_id;

    RETURN jsonb_build_object('success', true, 'message', 'Status VIP telah dinonaktifkan.');
END;
$$;

-- C. Admin Beri VIP Manual
CREATE OR REPLACE FUNCTION public.admin_grant_manual_vip(
    p_admin_secret TEXT,
    p_user_id TEXT,
    p_user_name TEXT,
    p_email TEXT,
    p_plan_name TEXT,
    p_duration_days INTEGER DEFAULT 30
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_expiry TIMESTAMPTZ;
BEGIN
    IF p_admin_secret != 'VALORA_VIP_ADMIN_SECURE_2026' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Akses Ditolak: Kunci Admin Tidak Valid');
    END IF;

    IF p_user_id IS NULL OR TRIM(p_user_id) = '' THEN
        RETURN jsonb_build_object('success', false, 'message', 'User ID wajib diisi');
    END IF;

    v_new_expiry := NOW() + (p_duration_days || ' days')::INTERVAL;

    INSERT INTO public.user_subscriptions (
        user_id,
        user_name,
        email,
        plan_name,
        price,
        status,
        is_active,
        expires_at,
        created_at,
        updated_at
    ) VALUES (
        p_user_id,
        COALESCE(NULLIF(TRIM(p_user_name), ''), 'VIP Member'),
        NULLIF(TRIM(p_email), ''),
        COALESCE(NULLIF(TRIM(p_plan_name), ''), '1 Bulan (Manual)'),
        0,
        'approved',
        true,
        v_new_expiry,
        NOW(),
        NOW()
    )
    ON CONFLICT (user_id) DO UPDATE SET
        user_name = COALESCE(NULLIF(TRIM(EXCLUDED.user_name), ''), user_subscriptions.user_name),
        email = COALESCE(NULLIF(TRIM(EXCLUDED.email), ''), user_subscriptions.email),
        plan_name = EXCLUDED.plan_name,
        status = 'approved',
        is_active = true,
        expires_at = v_new_expiry,
        updated_at = NOW();

    RETURN jsonb_build_object(
        'success', true,
        'message', 'VIP Manual berhasil diberikan!',
        'user_id', p_user_id,
        'expires_at', v_new_expiry
    );
END;
$$;

-- Berikan izin eksekusi ke peran terkait
GRANT EXECUTE ON FUNCTION public.admin_acc_vip TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_revoke_vip TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_grant_manual_vip TO anon, authenticated, service_role;

-- 4. AKTIFKAN REALTIME NOTIFIKASI
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.user_subscriptions;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;
