package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.SocialUserRepository;
import com.example.todoapp.user.domain.service.SocialUserService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialLoginUserServiceTests {
    private final MemorySocialRepository repository = new MemorySocialRepository();
    private final SocialUserService accounts = new SocialUserService(repository);

    @Test
    void naverUsesNestedIdAndReturnsLocalUsernameForTodoOwnershipWithoutProfileAttributes() {
        var service = new SocialOAuth2UserService(accounts, request -> naverProfile("naver-id"));
        OAuth2User first = service.loadUser(oauthRequest("naver"));
        OAuth2User again = service.loadUser(oauthRequest("naver"));
        assertThat(first.getName()).isEqualTo(again.getName()).startsWith("naver_");
        assertThat(first.getAttributes()).containsOnlyKeys("username");
        assertThat(first.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(repository.accounts).hasSize(1).containsKey("naver:naver-id");
        assertThat(repository.accounts.get("naver:naver-id").getPasswordHash() == null).isTrue();
    }

    @Test
    void googleUsesVerifiedSubAndKeepsEqualEmailAccountsSeparateAcrossProviders() {
        var idToken = idToken("google-id");
        OAuth2UserService<OidcUserRequest, OidcUser> upstream = request ->
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var google = new SocialOidcUserService(accounts, upstream);
        OidcUser first = google.loadUser(new OidcUserRequest(registration("google"), accessToken(), idToken));
        OidcUser again = google.loadUser(new OidcUserRequest(registration("google"), accessToken(), idToken));
        OAuth2User naver = new SocialOAuth2UserService(accounts, request -> naverProfile("google-id"))
                .loadUser(oauthRequest("naver"));
        assertThat(first.getName()).isEqualTo(again.getName()).startsWith("google_");
        assertThat(first.getName()).isNotEqualTo(naver.getName());
        assertThat(first.getSubject()).isEqualTo("google-id");
        assertThat(first.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(repository.accounts).hasSize(2).containsKeys("google:google-id", "naver:google-id");
    }

    @Test
    void googleTokenClaimsCannotOverrideLocalMemberName() {
        var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "google-id", "username", "existing-local-member"));
        var service = new SocialOidcUserService(accounts, request ->
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token));
        OidcUser user = service.loadUser(new OidcUserRequest(registration("google"), accessToken(), token));
        assertThat(user.getName()).isEqualTo("google_member");
    }

    @Test
    void invalidProfilesAndUnsupportedProvidersNeverCreateAccounts() {
        for (var attributes : List.of(Map.<String, Object>of("response", Map.of()),
                Map.<String, Object>of("resultcode", "error", "response", Map.of("id", "id")),
                Map.<String, Object>of("resultcode", "00", "response", Map.of("id", 123)))) {
            var service = new SocialOAuth2UserService(accounts, request ->
                    new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "response"));
            assertThatThrownBy(() -> service.loadUser(oauthRequest("naver")))
                    .isInstanceOf(OAuth2AuthenticationException.class).hasNoCause();
        }
        assertThatThrownBy(() -> accounts.login("other", "id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.login("google", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.login("naver", "x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.accounts).isEmpty();
    }

    @Test
    void databaseAndUpstreamFailuresDoNotExposeTheirCauseOrProviderResponse() {
        SocialUserRepository unavailable = (provider, subject) -> {
            throw new DataAccessResourceFailureException("private database marker");
        };
        var service = new SocialOAuth2UserService(new SocialUserService(unavailable), request -> naverProfile("id"));
        assertThatThrownBy(() -> service.loadUser(oauthRequest("naver")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessage("간편 로그인을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.").hasNoCause();
        var upstreamFailure = new SocialOAuth2UserService(accounts, request -> {
            throw new OAuth2AuthenticationException("private provider marker");
        });
        assertThatThrownBy(() -> upstreamFailure.loadUser(oauthRequest("naver")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessage("간편 로그인을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.").hasNoCause();
    }

    private static OAuth2User naverProfile(String id) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("resultcode", "00", "message", "success", "response",
                        Map.of("id", id, "email", "same@example.test", "nickname", "tester")), "response");
    }

    private static OidcIdToken idToken(String subject) {
        return new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", subject, "iss", "https://accounts.google.com", "aud", List.of("test-client"),
                        "email", "same@example.test"));
    }

    private static OAuth2AccessToken accessToken() {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-access-token",
                Instant.now(), Instant.now().plusSeconds(300));
    }

    private static OAuth2UserRequest oauthRequest(String provider) {
        return new OAuth2UserRequest(registration(provider), accessToken());
    }

    private static ClientRegistration registration(String provider) {
        return ClientRegistration.withRegistrationId(provider).clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + provider)
                .authorizationUri("https://example.test/authorize").tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/userinfo").userNameAttributeName("response").build();
    }

    private static class MemorySocialRepository implements SocialUserRepository {
        private final Map<String, User> accounts = new ConcurrentHashMap<>();
        private final AtomicInteger ids = new AtomicInteger();

        @Override
        public User findOrCreate(String provider, String subject) {
            return accounts.computeIfAbsent(provider + ":" + subject,
                    key -> new User(ids.incrementAndGet(), provider + "_member", null));
        }
    }
}
