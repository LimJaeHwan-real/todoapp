package com.example.todoapp.todo.application.form;

import com.example.todoapp.todo.domain.model.Todo;
import lombok.Getter;
import lombok.Setter;

/** 브라우저에서 제출하는 입력만 담는다. */
@Getter
@Setter
public class TodoForm {

    private String title;
    private String detail;

    public static TodoForm from(Todo todo) {
        TodoForm form = new TodoForm();
        form.setTitle(todo.getTitle());
        form.setDetail(todo.getDetail());
        return form;
    }
}
