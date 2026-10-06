package com.example.todoapp.todo.infrastructure.repository;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.todo.infrastructure.mapper.TodoMapper;
import com.example.todoapp.todo.infrastructure.mapper.TodoRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoRepositoryImplTests {

    private final TodoMapper mapper = mock(TodoMapper.class);
    private final TodoRepository repository = new TodoRepositoryImpl(mapper);
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 9, 0);
    private final LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 6, 10, 30);

    @Test
    void findAllPreservesFieldsAndMapperOrder() {
        when(mapper.findAll()).thenReturn(List.of(row(2, "두 번째"), row(1, "첫 번째")));

        List<Todo> todos = repository.findAll();

        assertThat(todos).extracting(Todo::getId).containsExactly(2, 1);
        assertThat(todos.getFirst()).extracting(Todo::getTitle, Todo::getDetail,
                Todo::getCreatedAt, Todo::getUpdatedAt)
                .containsExactly("두 번째", "상세 내용", createdAt, updatedAt);
    }

    @Test
    void findAllReturnsEmptyListWhenNoRowsExist() {
        when(mapper.findAll()).thenReturn(List.of());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findByIdReconstructsExistingTodoWithoutChangingDates() {
        when(mapper.findById(7)).thenReturn(row(7, "기존 할일"));

        assertThat(repository.findById(7)).hasValueSatisfying(todo ->
                assertThat(todo).extracting(Todo::getId, Todo::getTitle, Todo::getDetail,
                        Todo::getCreatedAt, Todo::getUpdatedAt)
                        .containsExactly(7, "기존 할일", "상세 내용", createdAt, updatedAt));
    }

    @Test
    void findByIdReturnsEmptyWhenRowIsMissing() {
        when(mapper.findById(99)).thenReturn(null);

        assertThat(repository.findById(99)).isEmpty();
    }

    @Test
    void findByIdPreservesNullableDetailAndDates() {
        TodoRow row = new TodoRow();
        row.setId(3);
        row.setTitle("날짜 없는 할일");
        when(mapper.findById(3)).thenReturn(row);

        assertThat(repository.findById(3)).hasValueSatisfying(todo ->
                assertThat(todo).extracting(Todo::getDetail, Todo::getCreatedAt, Todo::getUpdatedAt)
                        .containsExactly(null, null, null));
    }

    @Test
    void insertReturnsGeneratedIdAndPreservesOriginalDomainObject() {
        Todo todo = new Todo(null, "새 할일", "상세 내용", createdAt, updatedAt);
        doAnswer(invocation -> {
            TodoRow row = invocation.getArgument(0);
            assertThat(row).extracting(TodoRow::getId, TodoRow::getTitle, TodoRow::getDetail,
                    TodoRow::getCreatedAt, TodoRow::getUpdatedAt)
                    .containsExactly(null, "새 할일", "상세 내용", createdAt, updatedAt);
            row.setId(42);
            return 1;
        }).when(mapper).insert(any(TodoRow.class));

        assertThat(repository.insert(todo)).isEqualTo(42);
        assertThat(todo.getId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void updatePassesChangedFieldsAndReturnsAffectedRows(int affectedRows) {
        doAnswer(invocation -> {
            TodoRow row = invocation.getArgument(0);
            assertThat(row).extracting(TodoRow::getId, TodoRow::getTitle, TodoRow::getDetail,
                    TodoRow::getCreatedAt, TodoRow::getUpdatedAt)
                    .containsExactly(7, "수정한 할일", "수정 내용", createdAt, updatedAt);
            return affectedRows;
        }).when(mapper).update(any(TodoRow.class));

        assertThat(repository.update(new Todo(7, "수정한 할일", "수정 내용", createdAt, updatedAt)))
                .isEqualTo(affectedRows);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deleteByIdReturnsAffectedRows(int affectedRows) {
        when(mapper.deleteById(7)).thenReturn(affectedRows);

        assertThat(repository.deleteById(7)).isEqualTo(affectedRows);
    }

    @Test
    void databaseFailureIsPropagatedInsteadOfBecomingAnEmptyList() {
        when(mapper.findAll()).thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        assertThatThrownBy(repository::findAll).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private TodoRow row(Integer id, String title) {
        TodoRow row = new TodoRow();
        row.setId(id);
        row.setTitle(title);
        row.setDetail("상세 내용");
        row.setCreatedAt(createdAt);
        row.setUpdatedAt(updatedAt);
        return row;
    }
}
