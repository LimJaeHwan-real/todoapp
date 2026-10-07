-- 테이블 초기화 후 기본 글 3개 등록
INSERT INTO todos (todo, detail, created_at, updated_at)
VALUES
    ('스프링 공부하기', '스프링 부트의 요청 처리 흐름과 계층별 역할을 정리합니다.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('할 일 CRUD 실습하기', '할 일을 등록하고 목록 조회, 상세 조회, 수정, 삭제를 실습합니다.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DB 초기화 확인하기', '서버를 재시작하면 기존 글이 삭제되고 기본 글 3개가 다시 생성되는지 확인합니다.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
