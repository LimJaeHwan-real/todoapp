-- 기존 PostgreSQL DB에 제목 검색과 목록 이동용 인덱스를 추가한다.
-- 대상 DB를 확인하고 직접 실행한다. 서버 시작 시 자동 실행하지 않는다.
-- CONCURRENTLY는 쓰기를 계속 허용하지만 작업 시간과 디스크 공간이 필요하다.
-- BEGIN/COMMIT 등 트랜잭션 블록 안에서 실행하지 않는다.
-- 기존 게시글과 계정을 변경하거나 삭제하지 않는다.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_todos_title_trgm ON todos USING GIN (todo public.gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_todos_created_id ON todos ((COALESCE(created_at, '-infinity'::timestamp)) DESC, id DESC);
