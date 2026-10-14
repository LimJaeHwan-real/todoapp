package com.example.todoapp.todo.domain.model;

import java.time.LocalDateTime;

/** 등록일과 ID로 목록에서 마지막으로 읽은 위치를 나타낸다. */
public record TodoCursor(LocalDateTime createdAt, int id) {

    public TodoCursor {
        if (id <= 0) {
            throw new IllegalArgumentException("올바른 목록 위치가 필요합니다.");
        }
        if (createdAt != null && (createdAt.getYear() < -4712 || createdAt.getYear() > 294276
                || createdAt.getNano() % 1000 != 0)) {
            throw new IllegalArgumentException("올바른 목록 날짜가 필요합니다.");
        }
    }

    public static TodoCursor from(Todo todo) {
        return new TodoCursor(todo.getCreatedAt(), todo.getId());
    }
}
