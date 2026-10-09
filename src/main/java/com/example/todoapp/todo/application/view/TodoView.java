package com.example.todoapp.todo.application.view;

import com.example.todoapp.todo.domain.model.Todo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 화면에 표시할 할일 정보. 도메인 객체를 템플릿에 직접 전달하지 않는다. */
@Getter
@AllArgsConstructor
public final class TodoView {

    private final Integer id;
    private final String title;
    private final String detail;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static TodoView from(Todo todo) {
        return new TodoView(todo.getId(), todo.getTitle(), todo.getDetail(),
                todo.getCreatedAt(), todo.getUpdatedAt());
    }
}
