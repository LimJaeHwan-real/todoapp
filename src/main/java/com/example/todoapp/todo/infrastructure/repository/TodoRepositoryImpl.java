package com.example.todoapp.todo.infrastructure.repository;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.todo.infrastructure.mapper.TodoMapper;
import com.example.todoapp.todo.infrastructure.mapper.TodoRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도메인 저장소 계약을 구현하고 DB 행과 도메인 객체를 변환한다.
 */
@Repository
@RequiredArgsConstructor
public class TodoRepositoryImpl implements TodoRepository {

    private final TodoMapper todoMapper;

    @Override
    public List<Todo> findAll() {
        return todoMapper.findAll().stream().map(TodoRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<Todo> findById(Integer id) {
        return Optional.ofNullable(todoMapper.findById(id)).map(TodoRepositoryImpl::toDomain);
    }

    @Override
    public Integer insert(Todo todo) {
        TodoRow row = toRow(todo);
        todoMapper.insert(row);
        return row.getId();
    }

    @Override
    public int update(Todo todo) {
        return todoMapper.update(toRow(todo));
    }

    @Override
    public int deleteById(Integer id, Integer authorId) {
        return todoMapper.deleteById(id, authorId);
    }

    private static Todo toDomain(TodoRow row) {
        return new Todo(row.getId(), row.getTitle(), row.getDetail(), row.getCreatedAt(), row.getUpdatedAt(), row.getAuthorId());
    }

    private static TodoRow toRow(Todo todo) {
        TodoRow row = new TodoRow();
        row.setId(todo.getId());
        row.setTitle(todo.getTitle());
        row.setDetail(todo.getDetail());
        row.setCreatedAt(todo.getCreatedAt());
        row.setUpdatedAt(todo.getUpdatedAt());
        row.setAuthorId(todo.getAuthorId());
        return row;
    }
}
