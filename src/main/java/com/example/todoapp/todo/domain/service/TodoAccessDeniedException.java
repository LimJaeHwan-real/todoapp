package com.example.todoapp.todo.domain.service;

public class TodoAccessDeniedException extends RuntimeException {
    public TodoAccessDeniedException() {
        super("본인이 작성한 항목만 수정·삭제할 수 있습니다.");
    }
}
