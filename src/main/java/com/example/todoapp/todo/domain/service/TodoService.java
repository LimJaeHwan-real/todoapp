package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;

import java.util.List;

/**
 * 할일의 조회와 생성·수정·삭제 기능을 제공한다.
 */
public interface TodoService {

    List<Todo> findAll();

    /**
     * @throws TodoNotFoundException 대상이 없는 경우
     */
    Todo findById(Integer id);

    /**
     * @return 저장 후 생성된 기본키
     * @throws IllegalArgumentException 제목이 유효하지 않은 경우
     */
    Integer create(String title, String detail, Integer authorId);

    /** 작성자만 수정 화면에 사용할 정보를 조회할 수 있다. */
    Todo findByIdForEdit(Integer id, Integer memberId);

    /**
     * @throws TodoNotFoundException 대상이 없거나 수정 시 사라진 경우
     * @throws IllegalArgumentException 제목이 유효하지 않은 경우
     */
    void update(Integer id, String title, String detail, Integer memberId);

    /**
     * @throws TodoNotFoundException 삭제된 대상이 없는 경우
     */
    void deleteById(Integer id, Integer memberId);
}
