package com.example.todoapp.todo.domain.model;

import java.time.LocalDateTime;

/**
 * 리포지토리가 주고받는 할일 도메인 객체.
 * 등록 전에는 id가 null이고, DB의 todo 컬럼은 title에 대응한다.
 */
public final class Todo {

    private final Integer id;
    private final String title;
    private final String detail;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Todo(Integer id, String title, String detail,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.detail = detail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
