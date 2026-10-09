package com.example.todoapp.todo.domain.service;

/**
 * 조회·수정·삭제할 할일이 없을 때 발생한다.
 */
public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(Integer id) {
        super("할일을 찾을 수 없습니다. id=" + id);
    }
}
