package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;
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
    public List<Todo> findAll() {
        return todoRepository.findAll();
    }

    @Override
    public Todo findById(Integer id) {
        return todoRepository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    }

    @Override
    @Transactional
    public Integer create(String title, String detail) {
        return todoRepository.insert(Todo.create(title, detail));
    }

    @Override
    @Transactional
    public void update(Integer id, String title, String detail) {
        Todo updated = findById(id).update(title, detail);
        if (todoRepository.update(updated) == 0) {
            throw new TodoNotFoundException(id);
        }
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        if (todoRepository.deleteById(id) == 0) {
            throw new TodoNotFoundException(id);
        }
    }
}
