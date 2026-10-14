package com.example.todoapp.todo.application.view;

import lombok.Getter;

@Getter
public final class TodoPagination {

    private final int currentPage;
    private final long totalPages;
    private final int startPage;
    private final long endPage;

    public TodoPagination(int requestedPage, long totalItems) {
        this.totalPages = Math.max(1, (totalItems + 9) / 10);
        this.currentPage = (int) Math.min(requestedPage, totalPages);
        this.startPage = (currentPage - 1) / 10 * 10 + 1;
        this.endPage = Math.min(startPage + 9L, totalPages);
    }
}
