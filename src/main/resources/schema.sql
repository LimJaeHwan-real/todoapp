-- 평문 비밀번호는 저장하지 않는다. 로그인 아이디는 대소문자를 구분한다.
CREATE TABLE IF NOT EXISTS users (
    id serial PRIMARY KEY,
    username varchar(50) NOT NULL UNIQUE,
    password_hash varchar(100),
    provider varchar(10),
    provider_user_id varchar(255),
    CONSTRAINT uq_users_social_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT users_login_method CHECK (
        (provider IS NULL AND provider_user_id IS NULL AND password_hash IS NOT NULL)
        OR (provider IS NOT NULL AND provider IN ('google', 'naver') AND provider_user_id IS NOT NULL
            AND length(provider_user_id) > 0 AND password_hash IS NULL)
    ),
    CONSTRAINT users_bcrypt_hash CHECK (
        password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
    )
);

-- 기존 할일과 계정은 서버 재시작 시에도 유지한다.
CREATE TABLE IF NOT EXISTS todos (
    ID serial PRIMARY KEY ,
    todo varchar(255) NOT NULL ,
    detail text,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    author_id integer,
    CONSTRAINT fk_todos_author FOREIGN KEY (author_id) REFERENCES users(id)
);
