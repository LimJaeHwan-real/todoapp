package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.model.TodoCursor;
import com.example.todoapp.todo.domain.model.TodoSlice;
import com.example.todoapp.todo.domain.model.TodoSearchKeyword;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoServiceImpl implements TodoService {

    private final TodoRepository todoRepository;

    @Override
    public TodoSlice search(String keyword, TodoCursor cursor, boolean backwards) {
        String normalizedKeyword = TodoSearchKeyword.normalize(keyword);
        if (backwards && cursor == null) {
            throw new IllegalArgumentException("이전 목록 위치가 필요합니다.");
        }
        // ponytail: 전체 개수 대신 한 건 더 읽어 다음 결과가 있는지만 판단한다.
        List<Todo> rows = todoRepository.search(normalizedKeyword, cursor, backwards, 11);
        List<Todo> items = rows.subList(0, Math.min(10, rows.size()));
        if (backwards) {
            items = items.reversed();
        }
        return new TodoSlice(items, rows.size() > 10);
    }

    @Override
    public long count() {
        return todoRepository.count();
    }

    @Override
    public List<Todo> findPage(int page) {
        // ponytail: 요청대로 페이지 크기를 10개로 고정한다.
        return todoRepository.findPage(10, (page - 1) * 10L);
    }

    @Override
    public List<Todo> findAll() {
        return todoRepository.findAll();
    }

    @Override
    public Todo findById(Integer id) {
        return todoRepository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    }

    @Override
    @Transactional
    public Integer create(String title, String detail, Integer authorId) {
        return todoRepository.insert(Todo.create(title, detail, authorId));
    }

    @Override
    public Todo findByIdForEdit(Integer id, Integer memberId) {
        Todo todo = findById(id);
        if (!todo.isOwnedBy(memberId)) {
            throw new TodoAccessDeniedException();
        }
        return todo;
    }

    @Override
    @Transactional
    public void update(Integer id, String title, String detail, Integer memberId) {
        Todo updated = findByIdForEdit(id, memberId).update(title, detail);
        if (todoRepository.update(updated) == 0) {
            throw new TodoNotFoundException(id);
        }
    }

    @Override
    @Transactional
    public void deleteById(Integer id, Integer memberId) {
        findByIdForEdit(id, memberId);
        if (todoRepository.deleteById(id, memberId) == 0) {
            throw new TodoNotFoundException(id);
        }
    }
}
