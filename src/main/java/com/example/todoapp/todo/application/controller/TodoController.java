package com.example.todoapp.todo.application.controller;

import com.example.todoapp.todo.application.form.TodoForm;
import com.example.todoapp.todo.application.view.TodoView;
import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("todos", todoService.findAll().stream().map(TodoView::from).toList());
        return "todos/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        requirePositiveId(id);
        model.addAttribute("todo", TodoView.from(todoService.findById(id)));
        return "todos/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("todoForm", new TodoForm());
        return form(model, null);
    }

    @PostMapping
    public String create(@ModelAttribute TodoForm todoForm, BindingResult bindingResult, Model model) {
        validateTitle(todoForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return form(model, null);
        }
        Integer id = todoService.create(todoForm.getTitle().strip(), todoForm.getDetail());
        return "redirect:/todos/" + id;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        requirePositiveId(id);
        model.addAttribute("todoForm", TodoForm.from(todoService.findById(id)));
        return form(model, id);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Integer id, @ModelAttribute TodoForm todoForm,
                         BindingResult bindingResult, Model model) {
        requirePositiveId(id);
        todoService.findById(id);
        validateTitle(todoForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return form(model, id);
        }
        todoService.update(id, todoForm.getTitle().strip(), todoForm.getDetail());
        return "redirect:/todos/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        requirePositiveId(id);
        todoService.deleteById(id);
        return "redirect:/todos";
    }

    private static void requirePositiveId(Integer id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateTitle(TodoForm form, BindingResult bindingResult) {
        try {
            Todo.create(form.getTitle(), form.getDetail());
        } catch (IllegalArgumentException exception) {
            bindingResult.rejectValue("title", "title.invalid", exception.getMessage());
        }
    }

    private static String form(Model model, Integer id) {
        model.addAttribute("todoId", id);
        model.addAttribute("editing", id != null);
        return "todos/form";
    }
}
