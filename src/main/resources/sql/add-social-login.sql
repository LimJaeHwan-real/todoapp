-- 기존 users 데이터는 유지한다. 서버를 중지한 상태에서 대상 DB를 확인하고 1회 실행한다.
BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS provider varchar(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_user_id varchar(255);
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_social_identity ON users (provider, provider_user_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_login_method' AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_login_method CHECK (
            (provider IS NULL AND provider_user_id IS NULL AND password_hash IS NOT NULL)
            OR (provider IS NOT NULL AND provider IN ('google', 'naver') AND provider_user_id IS NOT NULL
                AND length(provider_user_id) > 0 AND password_hash IS NULL)
        );
    END IF;
END $$;

COMMIT;
