package com.example.todoapp.todo.domain.repository;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.model.TodoCursor;

import java.util.List;
import java.util.Optional;

/**
 * 할일 저장소의 계약. 구현체와 DB 매핑은 인프라 계층에서 제공한다.
 */
public interface TodoRepository {

    long count();

    /** 기존 정렬 순서로 offset개를 건너뛰고 limit개까지 조회한다. */
    List<Todo> findPage(int limit, long offset);

    /** 제목 부분 검색. 이전 방향이면 등록일·ID 오름차순으로 가장 가까운 결과부터 반환한다. */
    List<Todo> search(String keyword, TodoCursor cursor, boolean backwards, int limit);

    /**
     * 등록일 내림차순, 기본키 내림차순으로 조회한다. 등록일이 없는 항목은 뒤에 둔다.
     *
     * @return 항목이 없으면 빈 목록
     */
    List<Todo> findAll();

    /**
     * @param id 조회할 할일의 기본키
     * @return 대상이 없으면 Optional.empty()
     */
    Optional<Todo> findById(Integer id);

    /**
     * @param todo 등록할 할일. 기본키는 DB에서 생성한다.
     * @return 저장 후 생성된 기본키
     */
    Integer insert(Todo todo);

    /**
     * 기본키와 작성자가 일치하는 할일의 제목, 내용, 수정일을 변경한다. 작성자와 등록일은 유지한다.
     *
     * @param todo 기본키와 변경할 정보를 담은 할일
     * @return 변경된 행 수. 대상이 없으면 0, 성공하면 1
     */
    int update(Todo todo);

    /**
     * @param id 삭제할 할일의 기본키
     * @param authorId 로그인한 회원의 기본키. 작성자가 일치하는 행만 삭제한다.
     * @return 삭제된 행 수. 대상이 없으면 0, 성공하면 1
     */
    int deleteById(Integer id, Integer authorId);
}
