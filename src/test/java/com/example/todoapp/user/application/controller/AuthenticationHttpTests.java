package com.example.todoapp.user.application.controller;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.service.DuplicateUsernameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 실제 내장 서버와 HTTP 쿠키를 사용한다. 테스트 계정은 메모리에만 보관한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.read-only=true",
        "server.address=127.0.0.1",
        "logging.level.root=OFF",
        "spring.main.banner-mode=off"
})
class AuthenticationHttpTests {
    @LocalServerPort
    private int port;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // DB 저장 경계만 대체하고 실제 인증 필터·서비스·화면·HTTP 서버는 유지한다.
    @MockitoBean
    private UserRepository repository;

    @MockitoBean
    private TodoRepository todoRepository;

    private final Map<String, User> accounts = new ConcurrentHashMap<>();
    private final Map<Integer, Todo> todos = new ConcurrentHashMap<>();
    private HttpClient client;
    private CookieManager cookies;
    private String username;
    private String password;
    private boolean unavailable;

    @BeforeEach
    void setUp() {
        accounts.clear();
        todos.clear();
        unavailable = false;
        username = "http_" + UUID.randomUUID().toString().replace("-", "");
        password = UUID.randomUUID().toString();
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        when(repository.findByUsername(anyString())).thenAnswer(call -> {
            if (unavailable) {
                throw new CannotGetJdbcConnectionException("database unavailable");
            }
            return Optional.ofNullable(accounts.get(call.getArgument(0)));
        });
        doAnswer(call -> {
            User account = call.getArgument(0);
            User stored = new User(accounts.size() + 1, account.getUsername(), account.getPasswordHash());
            if (accounts.putIfAbsent(account.getUsername(), stored) != null) {
                throw new DuplicateUsernameException();
            }
            return null;
        }).when(repository).insert(any(User.class));
        when(todoRepository.findAll()).thenAnswer(call -> List.copyOf(todos.values()));
        when(todoRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(todos.get(call.getArgument(0))));
        when(todoRepository.insert(any())).thenAnswer(call -> {
            Todo todo = call.getArgument(0);
            int id = todos.size() + 1;
            todos.put(id, new Todo(id, todo.getTitle(), todo.getDetail(), todo.getCreatedAt(), todo.getUpdatedAt(), todo.getAuthorId()));
            return id;
        });
        when(todoRepository.update(any())).thenAnswer(call -> {
            Todo changed = call.getArgument(0);
            Todo stored = todos.get(changed.getId());
            if (stored == null || !stored.isOwnedBy(changed.getAuthorId())) return 0;
            todos.put(changed.getId(), changed);
            return 1;
        });
        when(todoRepository.deleteById(any(), any())).thenAnswer(call -> {
            Integer id = call.getArgument(0);
            Todo stored = todos.get(id);
            return stored != null && stored.isOwnedBy(call.getArgument(1)) && todos.remove(id) != null ? 1 : 0;
        });
    }

    @AfterEach
    void closeBrowser() {
        if (client != null) client.close();
    }

    @Test
    void publicFormsRenderAndAnonymousTodoReadsRequireLoginOverHttp() throws Exception {
        status(get("/login"), 200);
        status(get("/signup"), 200);
        assertThat(csrf(get("/login")).isBlank()).isFalse();
        assertThat(csrf(get("/signup")).isBlank()).isFalse();
        for (String path : List.of("/todos", "/todos/1", "/todos/new", "/todos/1/edit")) {
            redirect(get(path), "/login");
        }
    }

    @Test
    void threeCharacterPasswordIsRejectedAndFourCharacterPasswordCanLoginOverHttp() throws Exception {
        password = UUID.randomUUID().toString().substring(0, 3);
        HttpResponse<String> rejected = post("/signup", Map.of("username", username, "password", password,
                "_csrf", csrf(get("/signup"))));
        status(rejected, 200);
        assertThat(accounts.isEmpty()).isTrue();
        assertThat(rejected.body().contains("4자 이상")).isTrue();
        password = UUID.randomUUID().toString().substring(0, 4);
        register();
        login();
        status(get("/todos"), 200);
    }

    @Test
    void signupLoginSessionRotationAndLogoutWorkOverHttp() throws Exception {
        register();
        User stored = accounts.get(username);
        assertThat(stored != null).isTrue();
        assertThat(stored.getPasswordHash().equals(password)).isFalse();
        assertThat(passwordEncoder.matches(password, stored.getPasswordHash())).isTrue();
        HttpResponse<String> form = get("/login");
        String beforeLogin = sessionId();
        redirect(post("/login", Map.of("username", username, "password", password,
                "_csrf", csrf(form))), "/todos");
        assertThat(sessionId().isBlank()).isFalse();
        assertThat(sessionId().equals(beforeLogin)).isFalse();
        HttpResponse<String> todos = get("/todos");
        status(todos, 200);
        assertThat(todos.body().contains("action=\"/logout\"")).isTrue();
        redirect(post("/logout", Map.of("_csrf", csrf(todos))), "/login?logout");
        redirect(get("/todos"), "/login");
    }

    @Test
    void missingCsrfBlocksSignupLoginAndAuthenticatedWritesOverHttp() throws Exception {
        get("/signup");
        status(post("/signup", Map.of("username", username, "password", password)), 403);
        status(post("/login", Map.of("username", username, "password", password)), 403);
        assertThat(accounts.isEmpty()).isTrue();
        register();
        login();
        for (String path : List.of("/todos", "/todos/1/edit", "/todos/1/delete", "/logout")) {
            status(post(path, Map.of("title", "HTTP verification")), 403);
        }
        status(get("/todos"), 200);
    }

    @Test
    void anonymousWritesWithValidCsrfStillRequireLoginOverHttp() throws Exception {
        for (String path : List.of("/todos", "/todos/1/edit", "/todos/1/delete")) {
            String token = csrf(get("/login"));
            redirect(post(path, Map.of("_csrf", token, "title", "HTTP verification")), "/login");
        }
        assertThat(accounts.isEmpty()).isTrue();
    }

    @Test
    void wrongPasswordAndUnknownAccountCannotLoginOverHttp() throws Exception {
        register();
        redirect(post("/login", Map.of("username", username, "password", UUID.randomUUID().toString(),
                "_csrf", csrf(get("/login")))), "/login?error");
        redirect(get("/todos"), "/login");
        redirect(post("/login", Map.of("username", "absent_" + UUID.randomUUID().toString(),
                "password", password, "_csrf", csrf(get("/login")))), "/login?error");
        redirect(get("/todos"), "/login");
    }

    @Test
    void duplicateSignupDoesNotReplacePasswordOrEchoItOverHttp() throws Exception {
        register();
        String replacement = UUID.randomUUID().toString();
        HttpResponse<String> result = post("/signup", Map.of("username", username,
                "password", replacement, "_csrf", csrf(get("/signup"))));
        status(result, 200);
        assertThat(result.body().contains(replacement)).isFalse();
        assertThat(passwordEncoder.matches(password, accounts.get(username).getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(replacement, accounts.get(username).getPasswordHash())).isFalse();
    }

    @Test
    void databaseFailureReturnsSafeSignupPageAndRejectsLoginOverHttp() throws Exception {
        unavailable = true;
        HttpResponse<String> result = post("/signup", Map.of("username", username,
                "password", password, "_csrf", csrf(get("/signup"))));
        status(result, 503);
        assertThat(result.body().contains(password)).isFalse();
        assertThat(result.body().contains("database unavailable")).isFalse();
        redirect(post("/login", Map.of("username", username, "password", password,
                "_csrf", csrf(get("/login")))), "/login?error");
        redirect(get("/todos"), "/login");
    }

    @Test
    void onlyAuthorCanModifyItemAcrossTwoRealHttpSessions() throws Exception {
        register();
        login();
        String owner = username;
        String ownerPassword = password;
        redirect(post("/todos", Map.of("title", "original", "authorId", "2",
                "_csrf", csrf(get("/todos/new")))), "/todos/1");
        assertThat(todos.get(1).getAuthorId()).isEqualTo(accounts.get(owner).getId());

        freshBrowser();
        username = "other_" + UUID.randomUUID().toString().replace("-", "");
        password = UUID.randomUUID().toString();
        register();
        login();
        HttpResponse<String> detail = get("/todos/1");
        status(detail, 200);
        assertThat(detail.body().contains("href=\"/todos/1/edit\"")).isFalse();
        assertThat(detail.body().contains("action=\"/todos/1/delete\"")).isFalse();
        status(get("/todos/1/edit"), 403);
        String token = csrf(get("/todos"));
        status(post("/todos/1/edit", Map.of("title", "changed", "authorId", "1", "_csrf", token)), 403);
        status(post("/todos/1/delete", Map.of("authorId", "1", "_csrf", token)), 403);
        assertThat(todos.get(1).getTitle()).isEqualTo("original");

        freshBrowser();
        username = owner;
        password = ownerPassword;
        login();
        status(get("/todos/1/edit"), 200);
        redirect(post("/todos/1/edit", Map.of("title", "updated", "authorId", "2",
                "_csrf", csrf(get("/todos/1/edit")))), "/todos/1");
        assertThat(todos.get(1).getTitle()).isEqualTo("updated");
        assertThat(todos.get(1).getAuthorId()).isEqualTo(accounts.get(owner).getId());
        redirect(post("/todos/1/delete", Map.of("_csrf", csrf(get("/todos/1")))), "/todos");
        assertThat(todos.containsKey(1)).isFalse();
    }

    @Test
    void legacyItemIsReadOnlyOverRealHttp() throws Exception {
        register();
        login();
        LocalDateTime date = LocalDateTime.of(2026, 10, 9, 10, 0);
        todos.put(7, new Todo(7, "legacy", "preserved", date, date));
        HttpResponse<String> detail = get("/todos/7");
        status(detail, 200);
        assertThat(detail.body().contains("href=\"/todos/7/edit\"")).isFalse();
        status(get("/todos/7/edit"), 403);
        String token = csrf(get("/todos"));
        status(post("/todos/7/edit", Map.of("title", "changed", "_csrf", token)), 403);
        status(post("/todos/7/delete", Map.of("_csrf", token)), 403);
        assertThat(todos.get(7).getTitle()).isEqualTo("legacy");
    }

    private void freshBrowser() {
        client.close();
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    }

    private void register() throws Exception {
        redirect(post("/signup", Map.of("username", username, "password", password,
                "_csrf", csrf(get("/signup")))), "/login?registered");
    }

    private void login() throws Exception {
        redirect(post("/login", Map.of("username", username, "password", password,
                "_csrf", csrf(get("/login")))), "/todos");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String csrf(HttpResponse<String> response) {
        var inputs = Pattern.compile("<input\\b[^>]*>").matcher(response.body());
        while (inputs.find()) {
            if (inputs.group().contains("name=\"_csrf\"")) {
                var value = Pattern.compile("value=\"([^\"]*)\"").matcher(inputs.group());
                if (value.find()) {
                    return value.group(1);
                }
            }
        }
        assertThat(false).as("Rendered form must contain a CSRF field").isTrue();
        return "";
    }

    private String sessionId() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(java.net.HttpCookie::getValue).findFirst().orElse("");
    }

    private static void status(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).isEqualTo(expected);
    }

    private static void redirect(HttpResponse<String> response, String expected) {
        status(response, 302);
        String location = response.headers().firstValue("Location").orElse("");
        assertThat(!location.isBlank()).isTrue();
        URI target = URI.create(location);
        assertThat(target.getPath() + (target.getQuery() == null ? "" : "?" + target.getQuery())).isEqualTo(expected);
    }
}
