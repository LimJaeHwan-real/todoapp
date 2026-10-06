package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoServiceImplTests {

    private final MemoryRepository repository = new MemoryRepository();
    private final TodoService service = new TodoServiceImpl(repository);
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 9, 0);
    private final LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void findAllReturnsEmptyListWhenNoTodosExist() {
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void findAllPreservesRepositoryOrderAndStoredDates() {
        repository.todos.put(7, todo(7));
        repository.todos.put(3, todo(3));

        List<Todo> todos = service.findAll();

        assertThat(todos).extracting(Todo::getId).containsExactly(7, 3);
        assertThat(todos).extracting(Todo::getCreatedAt).containsOnly(createdAt);
        assertThat(todos).extracting(Todo::getUpdatedAt).containsOnly(updatedAt);
    }

    @Test
    void findByIdReturnsStoredTodoWithoutChangingDates() {
        repository.todos.put(7, todo(7));

        assertThat(service.findById(7)).extracting(Todo::getId, Todo::getTitle,
                        Todo::getDetail, Todo::getCreatedAt, Todo::getUpdatedAt)
                .containsExactly(7, "기존 제목", "기존 내용", createdAt, updatedAt);
    }

    @Test
    void findByIdThrowsDomainExceptionWhenTodoIsMissing() {
        assertThatThrownBy(() -> service.findById(99))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    void createPersistsValidatedTodoAndReturnsGeneratedId() {
        LocalDateTime before = LocalDateTime.now();

        Integer id = service.create(" 새 할일 ", null);
        Todo stored = repository.todos.get(id);

        assertThat(id).isEqualTo(1);
        assertThat(stored.getTitle()).isEqualTo("새 할일");
        assertThat(stored.getDetail()).isNull();
        assertThat(stored.getCreatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
    }

    @Test
    void createDoesNotPersistInvalidTitle() {
        assertThatThrownBy(() -> service.create(" ", "내용"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.todos).isEmpty();
    }

    @Test
    void updateChangesOnlySelectedTodoAndPreservesCreationDate() {
        repository.todos.put(7, todo(7));
        repository.todos.put(8, todo(8));
        LocalDateTime before = LocalDateTime.now();

        service.update(7, " 수정 제목 ", "수정 내용");

        Todo stored = repository.todos.get(7);
        assertThat(stored).extracting(Todo::getId, Todo::getTitle, Todo::getDetail, Todo::getCreatedAt)
                .containsExactly(7, "수정 제목", "수정 내용", createdAt);
        assertThat(stored.getUpdatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(repository.todos.get(8)).extracting(Todo::getTitle, Todo::getUpdatedAt)
                .containsExactly("기존 제목", updatedAt);
    }

    @Test
    void updateDoesNotPersistInvalidTitle() {
        repository.todos.put(7, todo(7));

        assertThatThrownBy(() -> service.update(7, "가".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.todos.get(7)).extracting(Todo::getTitle, Todo::getUpdatedAt)
                .containsExactly("기존 제목", updatedAt);
    }

    @Test
    void updateThrowsDomainExceptionWhenTodoIsMissing() {
        assertThatThrownBy(() -> service.update(99, "수정 제목", null))
                .isInstanceOf(TodoNotFoundException.class);
        assertThat(repository.todos).isEmpty();
    }

    @Test
    void updateThrowsDomainExceptionIfTodoDisappearsAfterLookup() {
        TodoRepository disappearingRepository = mock(TodoRepository.class);
        when(disappearingRepository.findById(7)).thenReturn(Optional.of(todo(7)));
        TodoService disappearingService = new TodoServiceImpl(disappearingRepository);

        assertThatThrownBy(() -> disappearingService.update(7, "수정 제목", null))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    void deleteByIdRemovesOnlySelectedTodo() {
        repository.todos.put(7, todo(7));
        repository.todos.put(8, todo(8));

        service.deleteById(7);

        assertThat(repository.todos).containsOnlyKeys(8);
    }

    @Test
    void deleteByIdThrowsDomainExceptionWhenNoRowIsDeleted() {
        assertThatThrownBy(() -> service.deleteById(99))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    void databaseFailureIsPropagatedWithoutBeingConvertedToMissingTodoOrInvalidTitle() {
        TodoRepository failingRepository = mock(TodoRepository.class);
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(failingRepository.findById(7)).thenThrow(failure);
        TodoService failingService = new TodoServiceImpl(failingRepository);

        assertThatThrownBy(() -> failingService.findById(7)).isSameAs(failure);
        assertThatThrownBy(() -> failingService.update(7, "수정 제목", null)).isSameAs(failure);
    }

    private Todo todo(int id) {
        return new Todo(id, "기존 제목", "기존 내용", createdAt, updatedAt);
    }

    private static class MemoryRepository implements TodoRepository {

        private final Map<Integer, Todo> todos = new LinkedHashMap<>();
        private int sequence;

        @Override
        public List<Todo> findAll() {
            return new ArrayList<>(todos.values());
        }

        @Override
        public Optional<Todo> findById(Integer id) {
            return Optional.ofNullable(todos.get(id));
        }

        @Override
        public Integer insert(Todo todo) {
            int id = ++sequence;
            todos.put(id, new Todo(id, todo.getTitle(), todo.getDetail(),
                    todo.getCreatedAt(), todo.getUpdatedAt()));
            return id;
        }

        @Override
        public int update(Todo todo) {
            return todos.replace(todo.getId(), todo) == null ? 0 : 1;
        }

        @Override
        public int deleteById(Integer id) {
            return todos.remove(id) == null ? 0 : 1;
        }
    }
}
