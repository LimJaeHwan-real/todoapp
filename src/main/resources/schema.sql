-- 테이블이 존재하면 삭제
DROP TABLE IF EXISTS todos;

-- 테이블 만들기
CREATE TABLE todos (
    ID serial PRIMARY KEY ,
    todo varchar(255) NOT NULL ,
    detail text,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);
