package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.model.TodoCursor;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoSearchServiceTests {
    private final TodoRepository repository = mock(TodoRepository.class);
    private final TodoService service = new TodoServiceImpl(repository);
    private final LocalDateTime date = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Test
    void extraRowProvidesNextPageWithoutCountingOrReturningElevenItems() {
        when(repository.search("스프링", null, false, 11)).thenReturn(rows(11));
        var slice = service.search(" 스프링 ", null, false);
        assertThat(slice.items()).extracting(Todo::getId).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(slice.hasMore()).isTrue();
        verify(repository, never()).count();
        verify(repository, never()).findPage(anyInt(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10})
    void finalPageDoesNotInventMoreResults(int size) {
        when(repository.search("", null, false, 11)).thenReturn(rows(size));
        var slice = service.search("   ", null, false);
        assertThat(slice.items()).hasSize(size);
        assertThat(slice.hasMore()).isFalse();
    }

    @Test
    void previousPageTrimsLookaheadBeforeReversingClosestRows() {
        var cursor = new TodoCursor(null, 1);
        when(repository.search("스프링", cursor, true, 11)).thenReturn(rows(11));
        var slice = service.search("스프링", cursor, true);
        assertThat(slice.items()).extracting(Todo::getId).containsExactly(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(slice.hasMore()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"공", "공부", "가 나", "%%%", "😀😀😀"})
    void unindexableShortKeywordsNeverReachDatabase(String keyword) {
        assertThatThrownBy(() -> service.search(keyword, null, false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void overlongSearchAndMissingPreviousCursorAreRejected() {
        assertThatThrownBy(() -> service.search("가".repeat(256), null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("스프링", null, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private List<Todo> rows(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> new Todo(id, "스프링 " + id, null, date, date)).toList();
    }
}
