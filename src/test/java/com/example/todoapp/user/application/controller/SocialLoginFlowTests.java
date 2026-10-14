package com.example.todoapp.user.application.controller;

import com.example.todoapp.todo.application.controller.TodoController;
import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.todo.domain.service.TodoServiceImpl;
import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.repository.SocialUserRepository;
import com.example.todoapp.user.domain.service.UserRegistrationService;
import com.example.todoapp.user.domain.service.SocialUserService;
import com.example.todoapp.user.infrastructure.security.*;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 외부 인증 HTTP와 Google 서명 검증은 실제로 실행하고, 저장 경계만 메모리로 대체한다. */
@WebMvcTest(controllers = {AuthenticationController.class, TodoController.class}, properties = {
        "spring.test.mockmvc.print=NONE", "logging.level.root=OFF"
})
@Import({SecurityConfiguration.class, DatabaseUserDetailsService.class, UserRegistrationService.class,
        SocialUserService.class, SocialOAuth2UserService.class, SocialOidcUserService.class,
        TodoServiceImpl.class, SocialLoginFlowTests.FixtureConfiguration.class})
class SocialLoginFlowTests {
    private static final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private static final Map<String, String> subjects = new ConcurrentHashMap<>();
    private static final RSAKey signingKey = signingKey();
    private static final HttpServer provider = startProvider();
    private static final String baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort();

    @Autowired private MockMvc mvc;
    @Autowired private MemoryAccounts accounts;
    @MockitoBean private TodoRepository todos;

    @BeforeEach
    void clearAccounts() {
        accounts.users.clear();
        accounts.identities.clear();
        accounts.unavailable = false;
        grants.clear();
        subjects.clear();
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
    }

    @Test
    void configuredLoginPageOffersExactlyNaverAndGoogle() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(xpath("//section[@aria-label='간편 로그인']/a").nodeCount(2))
                .andExpect(xpath("//a[@href='/oauth2/authorization/naver']").exists())
                .andExpect(xpath("//a[@href='/oauth2/authorization/google']").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver", "google"})
    void firstLoginRepeatLoginOwnershipAndLogoutUseSameLocalMember(String registration) throws Exception {
        MockHttpSession first = login(registration, "member-subject", false);
        String username = authentication(first).getName();
        User member = accounts.findByUsername(username).orElseThrow();
        var date = LocalDateTime.of(2026, 10, 14, 12, 0);
        when(todos.findById(41)).thenReturn(Optional.of(new Todo(41, "owned", "detail", date, date, member.getId())));
        when(todos.findById(42)).thenReturn(Optional.of(new Todo(42, "other", "detail", date, date, member.getId() + 1)));
        when(todos.update(any())).thenReturn(1);
        mvc.perform(get("/todos/41/edit").session(first)).andExpect(status().isOk());
        mvc.perform(post("/todos/41/edit").session(first).with(csrf()).param("title", "updated"))
                .andExpect(redirectedUrl("/todos/41"));
        mvc.perform(get("/todos/42/edit").session(first)).andExpect(status().isForbidden());
        mvc.perform(post("/todos/42/delete").session(first).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(first).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertThat(first.isInvalid()).isTrue();
        MockHttpSession again = login(registration, "member-subject", false);
        assertThat(authentication(again).getName()).isEqualTo(username);
        assertThat(accounts.users).hasSize(1);
    }

    @Test
    void googleSignedOidcLoginAndNaverWithSameSubjectCreateSeparateMembers() throws Exception {
        MockHttpSession google = login("google", "same-subject", false);
        MockHttpSession naver = login("naver", "same-subject", false);
        assertThat(authentication(google).getName()).startsWith("google_")
                .isNotEqualTo(authentication(naver).getName());
        assertThat(accounts.users).hasSize(2);
        mvc.perform(get("/todos").session(google)).andExpect(status().isOk());
    }

    @Test
    void wrongStateCancelledLoginAndReplayedCallbackNeverCreateMember() throws Exception {
        Authorization authorization = authorize("naver");
        mvc.perform(get("/login/oauth2/code/naver").session(authorization.session())
                        .param("code", "unused").param("state", "wrong-state"))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        Authorization cancelled = authorize("google");
        mvc.perform(get("/login/oauth2/code/google").session(cancelled.session())
                        .param("error", "access_denied").param("state", cancelled.state()))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        mvc.perform(get("/login/oauth2/code/naver").param("code", "unused").param("state", authorization.state()))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        assertThat(accounts.users).isEmpty();
    }

    @Test
    void invalidGoogleNonceCannotProvisionAccount() throws Exception {
        Authorization authorization = authorize("google");
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant("google-subject", "wrong-nonce", false));
        mvc.perform(get("/login/oauth2/code/google").session(authorization.session())
                        .param("code", code).param("state", authorization.state()))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        assertThat(accounts.users).isEmpty();
    }

    @Test
    void invalidGoogleSignatureCannotProvisionAccount() throws Exception {
        Authorization authorization = authorize("google");
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant("google-subject", authorization.nonce(), true));
        mvc.perform(get("/login/oauth2/code/google").session(authorization.session())
                        .param("code", code).param("state", authorization.state()))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        assertThat(accounts.users).isEmpty();
    }

    @Test
    void databaseFailureReturnsSafeLoginPageWithoutCreatingSessionAuthentication() throws Exception {
        accounts.unavailable = true;
        Authorization authorization = authorize("naver");
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant("naver-subject", null, false));
        mvc.perform(get("/login/oauth2/code/naver").session(authorization.session())
                        .param("code", code).param("state", authorization.state()))
                .andExpect(redirectedUrl("/login?socialError")).andExpect(unauthenticated());
        mvc.perform(get("/login").param("socialError", ""))
                .andExpect(status().isOk()).andExpect(xpath("//p[@role='alert']").exists());
        assertThat(authorization.session().getAttribute("SPRING_SECURITY_LAST_EXCEPTION")).isNull();
        assertThat(accounts.users).isEmpty();
    }

    private MockHttpSession login(String registration, String subject, boolean badSignature) throws Exception {
        Authorization authorization = authorize(registration);
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant(subject, authorization.nonce(), badSignature));
        var result = mvc.perform(get("/login/oauth2/code/" + registration).session(authorization.session())
                        .param("code", code).param("state", authorization.state()))
                .andExpect(redirectedUrl("/todos")).andExpect(authenticated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authorization authorize(String registration) throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/" + registration))
                .andExpect(status().is3xxRedirection()).andReturn();
        URI location = URI.create(result.getResponse().getRedirectedUrl());
        assertThat(location.getHost()).isEqualTo("127.0.0.1");
        Map<String, String> query = parameters(location.getRawQuery());
        assertThat(query.get("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/" + registration);
        return new Authorization((MockHttpSession) result.getRequest().getSession(false), query.get("state"), query.get("nonce"));
    }

    private static Authentication authentication(MockHttpSession session) {
        var context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context.getAuthentication();
    }

    private record Authorization(MockHttpSession session, String state, String nonce) {}
    private record Grant(String subject, String nonce, boolean badSignature) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean MemoryAccounts memoryAccounts() { return new MemoryAccounts(); }

        @Bean ClientRegistrationRepository registrations() {
            return new InMemoryClientRegistrationRepository(registration("naver"), registration("google"));
        }

        private ClientRegistration registration(String name) {
            var builder = ClientRegistration.withRegistrationId(name).clientId("test-" + name + "-client")
                    .clientSecret("test-fixture-secret").clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri(baseUrl + "/" + name + "/authorize").tokenUri(baseUrl + "/" + name + "/token")
                    .userInfoUri(baseUrl + "/" + name + "/userinfo").userNameAttributeName("response");
            if (name.equals("google")) {
                builder.scope("openid").issuerUri("https://accounts.google.com")
                        .jwkSetUri(baseUrl + "/google/jwks").userNameAttributeName("sub");
            }
            return builder.build();
        }
    }

    static class MemoryAccounts implements UserRepository, SocialUserRepository {
        final Map<String, User> users = new ConcurrentHashMap<>();
        final Map<String, User> identities = new ConcurrentHashMap<>();
        final AtomicInteger ids = new AtomicInteger();
        boolean unavailable;

        public Optional<User> findByUsername(String username) { return Optional.ofNullable(users.get(username)); }
        public void insert(User user) { users.put(user.getUsername(), user); }
        public User findOrCreate(String registration, String subject) {
            if (unavailable) throw new DataAccessResourceFailureException("private database marker");
            return identities.computeIfAbsent(registration + ":" + subject, key -> {
                int id = ids.incrementAndGet();
                var user = new User(id, registration + "_" + id, null);
                users.put(user.getUsername(), user);
                return user;
            });
        }
    }

    private static Map<String, String> parameters(String query) {
        var result = new java.util.HashMap<String, String>();
        if (query == null) return result;
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static RSAKey signingKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("test-key").build();
        } catch (Exception exception) { throw new IllegalStateException("Test signing key creation failed."); }
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String response;
                if (path.equals("/google/jwks")) {
                    response = "{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}";
                } else if (path.endsWith("/token")) {
                    String code = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).get("code");
                    Grant grant = grants.get(code);
                    String token = UUID.randomUUID().toString();
                    subjects.put(token, grant.subject());
                    response = "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":300";
                    if (path.equals("/google/token")) {
                        try {
                            var claims = new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                                    .audience("test-google-client").subject(grant.subject()).claim("nonce", grant.nonce())
                                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 300000)).build();
                            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
                            jwt.sign(new RSASSASigner(grant.badSignature() ? signingKey() : signingKey));
                            response += ",\"scope\":\"openid\",\"id_token\":\"" + jwt.serialize() + "\"";
                        } catch (Exception exception) { throw new IllegalStateException("Test token creation failed."); }
                    }
                    response += "}";
                } else {
                    String header = exchange.getRequestHeaders().getFirst("Authorization");
                    String subject = subjects.get(header.substring("Bearer ".length()));
                    response = path.equals("/google/userinfo") ? "{\"sub\":\"" + subject + "\"}"
                            : "{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"" + subject + "\"}}";
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception exception) { throw new IllegalStateException("Local test provider start failed."); }
    }
}
