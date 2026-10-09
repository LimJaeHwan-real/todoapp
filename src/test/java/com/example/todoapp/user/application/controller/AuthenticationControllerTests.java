package com.example.todoapp.user.application.controller;

import com.example.todoapp.todo.domain.service.TodoService;
import com.example.todoapp.todo.application.controller.TodoController;
import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.service.DuplicateUsernameException;
import com.example.todoapp.user.domain.service.UserRegistrationService;
import com.example.todoapp.user.infrastructure.security.DatabaseUserDetailsService;
import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthenticationController.class, TodoController.class}, properties = "spring.test.mockmvc.print=NONE")
@Import({SecurityConfiguration.class, DatabaseUserDetailsService.class,
        UserRegistrationService.class, AuthenticationControllerTests.RepositoryConfiguration.class})
class AuthenticationControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TodoService todoService;

    @Autowired
    private MemoryUserRepository repository;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String password = UUID.randomUUID().toString();

    @BeforeEach
    void clearUsers() {
        repository.accounts.clear();
        repository.unavailable = false;
    }

    @Test
    void anonymousVisitorCanOpenSignupFormWithoutPrefilledPassword() throws Exception {
        mvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(xpath("//form[@action='/signup'][@method='post']").exists())
                .andExpect(xpath("//form/input[@name='_csrf']").exists())
                .andExpect(xpath("//input[@name='password'][@type='password'][not(@value)]").exists());
    }

    @Test
    void anonymousVisitorCanOpenLoginForm() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(xpath("//form[@action='/login'][@method='post']").exists())
                .andExpect(xpath("//form/input[@name='_csrf']").exists());
    }

    @Test
    void anonymousVisitorMustLoginBeforeReadingTodos() throws Exception {
        mvc.perform(get("/todos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(todoService);
    }

    @Test
    void signupStoresOnlyHashAndRegisteredAccountCanLoginAndLogout() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("username", "member").param("password", password))
                .andExpect(redirectedUrl("/login?registered"));
        User stored = repository.accounts.get("member");
        assertThat(stored != null).isTrue();
        assertThat(stored.getPasswordHash().equals(password)).isFalse();
        assertThat(passwordEncoder.matches(password, stored.getPasswordHash())).isTrue();

        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", "member").param("password", password))
                .andExpect(redirectedUrl("/todos"))
                .andExpect(authenticated().withUsername("member")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/todos").session(session)).andExpect(status().isOk())
                .andExpect(xpath("//form[@action='/logout']/input[@name='_csrf']").exists());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unknownUserAndWrongPasswordCannotLogin() throws Exception {
        registrationService.register("member", password);
        mvc.perform(post("/login").with(csrf()).param("username", "member")
                        .param("password", UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(post("/login").with(csrf()).param("username", "unknown")
                        .param("password", password))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void duplicateSignupDoesNotReplaceExistingPasswordOrReturnSubmittedPassword() throws Exception {
        registrationService.register("member", password);
        String second = UUID.randomUUID().toString();
        mvc.perform(post("/signup").with(csrf()).param("username", "member").param("password", second))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("signupForm", "username"))
                .andExpect(model().attribute("signupForm", org.hamcrest.Matchers.hasProperty("password", nullValue())))
                .andExpect(xpath("//input[@name='password'][not(@value)]").exists());
        String stored = repository.accounts.get("member").getPasswordHash();
        assertThat(passwordEncoder.matches(password, stored)).isTrue();
        assertThat(passwordEncoder.matches(second, stored)).isFalse();
    }

    @Test
    void invalidSignupDoesNotStoreAccountOrEchoPassword() throws Exception {
        mvc.perform(post("/signup").with(csrf()).param("username", "<script>").param("password", password))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("signupForm"))
                .andExpect(xpath("//input[@name='password'][not(@value)]").exists());
        assertThat(repository.accounts).isEmpty();
        mvc.perform(post("/signup").with(csrf()).param("username", "member").param("password", "short"))
                .andExpect(model().attributeHasErrors("signupForm"));
        assertThat(repository.accounts).isEmpty();
    }

    @Test
    void signupLoginAndTodoWritesRequireCsrf() throws Exception {
        mvc.perform(post("/signup").param("username", "member").param("password", password))
                .andExpect(status().isForbidden());
        assertThat(repository.accounts).isEmpty();
        mvc.perform(post("/login").param("username", "member").param("password", password))
                .andExpect(status().isForbidden());
        mvc.perform(post("/todos").with(user("member")).param("title", "할일"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/todos/1/edit").with(user("member")).param("title", "할일"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/todos/1/delete").with(user("member")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("member"))).andExpect(status().isForbidden());
        verifyNoInteractions(todoService);
    }

    @Test
    void anonymousTodoWriteWithCsrfStillRequiresLogin() throws Exception {
        mvc.perform(post("/todos").with(csrf()).param("title", "할일"))
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(todoService);
    }

    @Test
    void signupDatabaseFailureReturns503WithoutInternalDetailsOrPassword() throws Exception {
        repository.unavailable = true;
        var result = mvc.perform(post("/signup").with(csrf())
                        .param("username", "member").param("password", password))
                .andExpect(status().isServiceUnavailable())
                .andExpect(model().attributeHasErrors("signupForm"))
                .andExpect(xpath("//input[@name='password'][not(@value)]").exists()).andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(html.contains("internal database marker")).isFalse();
        assertThat(html.contains(password)).isFalse();
        assertThat(repository.accounts).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RepositoryConfiguration {
        @Bean
        MemoryUserRepository userRepository() {
            return new MemoryUserRepository();
        }
    }

    static class MemoryUserRepository implements UserRepository {
        final Map<String, User> accounts = new ConcurrentHashMap<>();
        boolean unavailable;

        @Override
        public Optional<User> findByUsername(String username) {
            if (unavailable) {
                throw new CannotGetJdbcConnectionException("internal database marker");
            }
            return Optional.ofNullable(accounts.get(username));
        }

        @Override
        public void insert(User user) {
            if (accounts.putIfAbsent(user.getUsername(), user) != null) {
                throw new DuplicateUsernameException();
            }
        }
    }
}
