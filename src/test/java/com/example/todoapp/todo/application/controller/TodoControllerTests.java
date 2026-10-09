package com.example.todoapp.todo.application.controller;

import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.service.TodoNotFoundException;
import com.example.todoapp.todo.domain.service.TodoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.model.User;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
@Import(SecurityConfiguration.class)
@WithMockUser
class TodoControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TodoService service;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository users;

    @BeforeEach
    void currentUser() {
        when(users.findByUsername("user")).thenReturn(Optional.of(new User(1, "user", null)));
        when(service.findByIdForEdit(7, 1)).thenAnswer(call -> service.findById(7));
    }

    private final LocalDateTime created = LocalDateTime.of(2026, 10, 5, 9, 0);
    private final LocalDateTime updated = LocalDateTime.of(2026, 10, 6, 10, 30);

    private Todo todo(String title, String detail) {
        return new Todo(7, title, detail, created, updated, 1);
    }

    @Test
    void listRendersEmptyStateAndCreateLink() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(get("/todos"))
                .andExpect(status().isOk())
                .andExpect(view().name("todos/list"))
                .andExpect(content().string(containsString("등록된 할일이 없습니다")))
                .andExpect(xpath("//a[@href='/todos/new']").exists());
    }

    @Test
    void listEscapesTitlesAndLinksToDetailsInServiceOrder() throws Exception {
        when(service.findAll()).thenReturn(List.of(todo("<script>alert(1)</script>", "내용"),
                new Todo(2, "이전 할일", null, created, created)));
        mvc.perform(get("/todos"))
                .andExpect(status().isOk())
                .andExpect(xpath("//main//li[1]//a[@href='/todos/7']").string("<script>alert(1)</script>"))
                .andExpect(xpath("//main//li[2]//a[@href='/todos/2']").string("이전 할일"))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void detailRendersEscapedFieldsDatesAndPostDeletionWithConfirmation() throws Exception {
        when(service.findById(7)).thenReturn(todo("<b>제목</b>", "<script>내용</script>\n둘째 줄"));
        mvc.perform(get("/todos/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("todos/detail"))
                .andExpect(xpath("//h1").string("<b>제목</b>"))
                .andExpect(content().string(containsString("2026-10-05 09:00")))
                .andExpect(content().string(containsString("2026-10-06 10:30")))
                .andExpect(content().string(not(containsString("<script>내용</script>"))))
                .andExpect(xpath("//a[@href='/todos/7/edit']").exists())
                .andExpect(xpath("//dialog[@id='delete-dialog'][not(@open)][@aria-labelledby='delete-dialog-title']").exists())
                .andExpect(xpath("//dialog/h2[@id='delete-dialog-title']").string("할일 삭제 확인"))
                .andExpect(xpath("//dialog//form[@action='/todos/7/delete'][@method='post']").exists())
                .andExpect(xpath("//dialog//button[@type='submit']").string("삭제 확인"))
                .andExpect(xpath("//dialog//button[@type='button'][contains(@onclick, '.close()')]").string("취소"))
                .andExpect(xpath("//button[not(ancestor::dialog)][@type='button'][contains(@onclick, '.showModal()')]").string("삭제"))
                .andExpect(xpath("//form[@action='/todos/7/delete'][not(ancestor::dialog)]").doesNotExist())
                .andExpect(xpath("//button[@type='submit'][not(ancestor::dialog)]").doesNotExist())
                .andExpect(content().string(not(containsString("return confirm("))));
    }

    @Test
    void newFormRendersLabelsAndCreateAction() throws Exception {
        mvc.perform(get("/todos/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("todos/form"))
                .andExpect(xpath("//form[@action='/todos'][@method='post']").exists())
                .andExpect(xpath("//label[@for='title']").exists())
                .andExpect(xpath("//label[@for='detail']").exists());
    }

    @Test
    void editFormContainsExistingValuesAndUpdateAction() throws Exception {
        when(service.findById(7)).thenReturn(todo("기존 제목", "기존 내용"));
        mvc.perform(get("/todos/7/edit"))
                .andExpect(status().isOk())
                .andExpect(xpath("//form[@action='/todos/7/edit'][@method='post']").exists())
                .andExpect(xpath("//input[@name='title']/@value").string("기존 제목"))
                .andExpect(xpath("//textarea[@name='detail']").string("기존 내용"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void createAcceptsDomainBoundariesAndRedirectsToGeneratedId(int length) throws Exception {
        String title = "가".repeat(length);
        when(service.create(title, "내용", 1)).thenReturn(42);
        mvc.perform(post("/todos").with(csrf()).param("title", " " + title + " ").param("detail", "내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/todos/42"));
        verify(service).create(title, "내용", 1);
    }

    @Test
    void createAccepts255UnicodeCodePoints() throws Exception {
        String title = "😀".repeat(255);
        when(service.create(title, "", 1)).thenReturn(42);
        mvc.perform(post("/todos").with(csrf()).param("title", title).param("detail", ""))
                .andExpect(redirectedUrl("/todos/42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void createRejectsBlankTitleAndPreservesInput(String title) throws Exception {
        mvc.perform(post("/todos").with(csrf()).param("title", title).param("detail", "<b>입력 유지</b>"))
                .andExpect(status().isOk())
                .andExpect(view().name("todos/form"))
                .andExpect(model().attributeHasFieldErrors("todoForm", "title"))
                .andExpect(model().attribute("todoForm", hasProperty("title", is(title))))
                .andExpect(xpath("//input[@name='title']").exists())
                .andExpect(xpath("//textarea[@name='detail']").string("<b>입력 유지</b>"))
                .andExpect(content().string(not(containsString("<b>입력 유지</b>"))));
        verifyNoInteractions(service);
    }

    @Test
    void createRejectsMissingAndOverlongTitles() throws Exception {
        mvc.perform(post("/todos").with(csrf()).param("detail", "내용"))
                .andExpect(model().attributeHasFieldErrors("todoForm", "title"));
        mvc.perform(post("/todos").with(csrf()).param("title", "가".repeat(256)))
                .andExpect(model().attributeHasFieldErrors("todoForm", "title"))
                .andExpect(xpath("//input[@name='title']/@value").string("가".repeat(256)));
        verifyNoInteractions(service);
    }

    @Test
    void updateRedirectsToTheSameDetail() throws Exception {
        when(service.findById(7)).thenReturn(todo("기존 제목", "내용"));
        mvc.perform(post("/todos/7/edit").with(csrf()).param("title", " 수정 제목 ").param("detail", "수정 내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/todos/7"));
        verify(service).update(7, "수정 제목", "수정 내용", 1);
    }

    @Test
    void updateInvalidTitleKeepsSubmittedValuesAndEditAction() throws Exception {
        when(service.findById(7)).thenReturn(todo("기존 제목", "내용"));
        mvc.perform(post("/todos/7/edit").with(csrf()).param("title", " ").param("detail", "수정 내용"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("todoForm", "title"))
                .andExpect(xpath("//form[@action='/todos/7/edit']").exists())
                .andExpect(xpath("//textarea[@name='detail']").string("수정 내용"));
    }

    @Test
    void deleteRedirectsToListAndGetCannotDelete() throws Exception {
        mvc.perform(post("/todos/7/delete").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/todos"));
        verify(service).deleteById(7, 1);
        clearInvocations(service);
        mvc.perform(get("/todos/7/delete")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void malformedIdsRender400ForEveryIdRoute(String id) throws Exception {
        mvc.perform(get("/todos/" + id)).andExpect(status().isBadRequest()).andExpect(view().name("error/400"));
        mvc.perform(get("/todos/" + id + "/edit")).andExpect(status().isBadRequest());
        mvc.perform(post("/todos/" + id + "/edit").with(csrf()).param("title", "제목")).andExpect(status().isBadRequest());
        mvc.perform(post("/todos/" + id + "/delete").with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void absentTodoRenders404ForReadEditAndDelete() throws Exception {
        when(service.findById(7)).thenThrow(new TodoNotFoundException(7));
        doThrow(new TodoNotFoundException(7)).when(service).deleteById(7, 1);
        mvc.perform(get("/todos/7")).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
        mvc.perform(get("/todos/7/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/todos/7/edit").with(csrf()).param("title", " ")).andExpect(status().isNotFound());
        mvc.perform(post("/todos/7/delete").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void updateThatDisappearsDuringWriteRenders404() throws Exception {
        when(service.findById(7)).thenReturn(todo("제목", "내용"));
        doThrow(new TodoNotFoundException(7)).when(service).update(7, "수정 제목", "수정 내용", 1);
        mvc.perform(post("/todos/7/edit").with(csrf()).param("title", "수정 제목").param("detail", "수정 내용"))
                .andExpect(status().isNotFound()).andExpect(view().name("error/404"));
    }

    @Test
    void connectionFailureRenders503WithoutInternalExceptionDetails() throws Exception {
        when(service.findAll()).thenThrow(new CannotGetJdbcConnectionException("internal connection marker", new SQLException("internal SQL marker")));
        mvc.perform(get("/todos"))
                .andExpect(status().isServiceUnavailable()).andExpect(view().name("error/503"))
                .andExpect(content().string(not(containsString("internal"))))
                .andExpect(xpath("//a[@href='/todos']").exists());
    }

    @Test
    void nestedConnectionFailureDuringWriteRenders503() throws Exception {
        when(service.create(anyString(), anyString(), eq(1))).thenThrow(new CannotCreateTransactionException("internal transaction marker",
                new CannotGetJdbcConnectionException("internal connection marker")));
        mvc.perform(post("/todos").with(csrf()).param("title", "정상 제목").param("detail", "내용"))
                .andExpect(status().isServiceUnavailable()).andExpect(view().name("error/503"))
                .andExpect(content().string(not(containsString("internal"))));
    }

    @Test
    void transactionConnectionFailureWithSqlState08Renders503() throws Exception {
        when(service.create(anyString(), anyString(), eq(1))).thenThrow(new CannotCreateTransactionException("internal transaction marker",
                new SQLException("internal connection marker", "08001")));
        mvc.perform(post("/todos").with(csrf()).param("title", "정상 제목").param("detail", "내용"))
                .andExpect(status().isServiceUnavailable()).andExpect(view().name("error/503"))
                .andExpect(content().string(not(containsString("internal"))));
    }

    @Test
    void otherInfrastructureFailuresAreNeither503NorTitleErrors() throws Exception {
        when(service.create(anyString(), anyString(), eq(1))).thenThrow(new IllegalArgumentException("internal mapper marker"));
        mvc.perform(post("/todos").with(csrf()).param("title", "정상 제목").param("detail", "내용"))
                .andExpect(status().isInternalServerError()).andExpect(content().string(not(containsString("internal"))));
        when(service.findAll()).thenThrow(new DataAccessResourceFailureException("internal non-connection marker"));
        mvc.perform(get("/todos"))
                .andExpect(status().isInternalServerError()).andExpect(content().string(not(containsString("internal"))));
    }
}
