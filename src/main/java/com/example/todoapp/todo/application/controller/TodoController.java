package com.example.todoapp.todo.application.controller;

import com.example.todoapp.todo.application.form.TodoForm;
import com.example.todoapp.todo.application.view.TodoView;
import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.service.TodoService;
import com.example.todoapp.todo.domain.service.TodoAccessDeniedException;
import com.example.todoapp.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@Controller
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final UserRepository userRepository;

    @InitBinder("todoForm")
    public void todoFields(WebDataBinder binder) {
        binder.setAllowedFields("title", "detail");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("todos", todoService.findAll().stream().map(TodoView::from).toList());
        return "todos/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model, Principal principal) {
        requirePositiveId(id);
        Todo todo = todoService.findById(id);
        model.addAttribute("todo", TodoView.from(todo));
        model.addAttribute("canEdit", todo.isOwnedBy(currentMemberId(principal)));
        return "todos/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("todoForm", new TodoForm());
        return form(model, null);
    }

    @PostMapping
    public String create(@ModelAttribute TodoForm todoForm, BindingResult bindingResult, Model model, Principal principal) {
        validateTitle(todoForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return form(model, null);
        }
        Integer id = todoService.create(todoForm.getTitle().strip(), todoForm.getDetail(), currentMemberId(principal));
        return "redirect:/todos/" + id;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model, Principal principal) {
        requirePositiveId(id);
        model.addAttribute("todoForm", TodoForm.from(todoService.findByIdForEdit(id, currentMemberId(principal))));
        return form(model, id);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Integer id, @ModelAttribute TodoForm todoForm,
                         BindingResult bindingResult, Model model, Principal principal) {
        requirePositiveId(id);
        Integer memberId = currentMemberId(principal);
        todoService.findByIdForEdit(id, memberId);
        validateTitle(todoForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return form(model, id);
        }
        todoService.update(id, todoForm.getTitle().strip(), todoForm.getDetail(), memberId);
        return "redirect:/todos/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, Principal principal) {
        requirePositiveId(id);
        todoService.deleteById(id, currentMemberId(principal));
        return "redirect:/todos";
    }

    private Integer currentMemberId(Principal principal) {
        if (principal == null) {
            throw new TodoAccessDeniedException();
        }
        return userRepository.findByUsername(principal.getName())
                .map(com.example.todoapp.user.domain.model.User::getId)
                .orElseThrow(TodoAccessDeniedException::new);
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
