-- 빈 할일 테이블에만 예시를 넣어 재시작 시 중복 등록을 방지한다.
INSERT INTO todos (todo, detail, created_at, updated_at)
SELECT seed.todo, seed.detail, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('스프링 공부하기', '스프링 부트의 요청 처리 흐름과 계층별 역할을 정리합니다.'),
    ('할 일 CRUD 실습하기', '할 일을 등록하고 목록 조회, 상세 조회, 수정, 삭제를 실습합니다.'),
    ('DB 초기화 확인하기', '서버를 재시작해도 기존 할일과 계정이 유지되는지 확인합니다.')
) AS seed(todo, detail)
WHERE NOT EXISTS (SELECT 1 FROM todos);
