package com.example.todoapp.todo.application.controller;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.todo.domain.service.TodoServiceImpl;
import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TodoController.class, properties = "spring.test.mockmvc.print=NONE")
@Import({SecurityConfiguration.class, TodoServiceImpl.class})
@WithMockUser("member")
class TodoOwnershipControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private TodoRepository todos;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(users.findByUsername("member")).thenReturn(Optional.of(new User(1, "member", null)));
        when(users.findByUsername("other")).thenReturn(Optional.of(new User(2, "other", null)));
        LocalDateTime date = LocalDateTime.of(2026, 10, 9, 10, 0);
        when(todos.findById(7)).thenReturn(Optional.of(new Todo(7, "legacy", "preserved", date, date)));
        when(todos.update(any(Todo.class))).thenReturn(1);
    }

    @Test
    void legacyItemRemainsReadableButEditAndDeleteControlsAreHidden() throws Exception {
        mvc.perform(get("/todos/7")).andExpect(status().isOk())
                .andExpect(xpath("//a[@href='/todos/7/edit']").doesNotExist())
                .andExpect(xpath("//form[@action='/todos/7/delete']").doesNotExist());
    }

    @Test
    void legacyItemCannotOpenEditForm() throws Exception {
        mvc.perform(get("/todos/7/edit")).andExpect(status().isForbidden());
    }

    @Test
    void legacyItemCannotBeUpdatedEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/todos/7/edit").with(csrf()).param("title", "changed"))
                .andExpect(status().isForbidden());
        verify(todos, never()).update(any());
    }

    @Test
    void legacyItemCannotBeDeletedEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/todos/7/delete").with(csrf())).andExpect(status().isForbidden());
        verify(todos, never()).deleteById(any(), any());
    }

    @Test
    void ownerCanEditUpdateAndDeleteAndOwnershipIsPreserved() throws Exception {
        ownedItem();
        when(todos.deleteById(8, 1)).thenReturn(1);
        mvc.perform(get("/todos/8")).andExpect(status().isOk())
                .andExpect(xpath("//a[@href='/todos/8/edit']").exists());
        mvc.perform(get("/todos/8/edit")).andExpect(status().isOk());
        mvc.perform(post("/todos/8/edit").with(csrf()).param("title", "updated").param("authorId", "2"))
                .andExpect(redirectedUrl("/todos/8"));
        var captor = ArgumentCaptor.forClass(Todo.class);
        verify(todos).update(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAuthorId()).isEqualTo(1);
        mvc.perform(post("/todos/8/delete").with(csrf())).andExpect(redirectedUrl("/todos"));
        verify(todos).deleteById(8, 1);
    }

    @Test
    void otherMemberCanReadButCannotEditUpdateOrDeleteEvenWithForgedAuthor() throws Exception {
        ownedItem();
        mvc.perform(get("/todos/8").with(user("other"))).andExpect(status().isOk())
                .andExpect(xpath("//a[@href='/todos/8/edit']").doesNotExist())
                .andExpect(xpath("//form[@action='/todos/8/delete']").doesNotExist());
        mvc.perform(get("/todos/8/edit").with(user("other"))).andExpect(status().isForbidden());
        mvc.perform(post("/todos/8/edit").with(user("other")).with(csrf())
                        .param("title", " ").param("authorId", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/todos/8/delete").with(user("other")).with(csrf()).param("authorId", "1"))
                .andExpect(status().isForbidden());
        verify(todos, never()).update(any());
        verify(todos, never()).deleteById(any(), any());
    }

    @Test
    void creationUsesLoggedInMemberInsteadOfSubmittedAuthor() throws Exception {
        when(todos.insert(any())).thenReturn(9);
        mvc.perform(post("/todos").with(csrf()).param("title", "created").param("authorId", "2"))
                .andExpect(redirectedUrl("/todos/9"));
        var captor = ArgumentCaptor.forClass(Todo.class);
        verify(todos).insert(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAuthorId()).isEqualTo(1);
    }

    @Test
    void deletedAccountCannotUseAnOldAuthenticatedSessionToWrite() throws Exception {
        ownedItem();
        mvc.perform(post("/todos/8/edit").with(user("deleted")).with(csrf()).param("title", "changed"))
                .andExpect(status().isForbidden());
        verify(todos, never()).update(any());
    }

    private void ownedItem() {
        LocalDateTime date = LocalDateTime.of(2026, 10, 9, 10, 0);
        when(todos.findById(8)).thenReturn(Optional.of(new Todo(8, "owned", "preserved", date, date, 1)));
    }
}
