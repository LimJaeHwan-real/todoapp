-- 기존 항목은 작성자 없는 상태로 보존한다. 자동 초기화와 분리하여 한 번 적용한다.
ALTER TABLE todos ADD COLUMN IF NOT EXISTS author_id integer;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'todos'::regclass AND conname = 'fk_todos_author'
    ) THEN
        ALTER TABLE todos ADD CONSTRAINT fk_todos_author
            FOREIGN KEY (author_id) REFERENCES users(id);
    END IF;
END $$;
