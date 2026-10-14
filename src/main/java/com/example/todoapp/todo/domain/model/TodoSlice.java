package com.example.todoapp.todo.domain.model;

import java.util.List;

/** 전체 개수를 세지 않고 요청 방향에 추가 결과가 있는지 알려 준다. */
public record TodoSlice(List<Todo> items, boolean hasMore) {
    public TodoSlice {
        items = List.copyOf(items);
    }
}
