package com.example.todoapp.user.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SocialLoginConfiguration.class).withPropertyValues(
                    "GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=", "NAVER_CLIENT_ID=", "NAVER_CLIENT_SECRET=");

    @Test
    void missingOrIncompleteCredentialsDoNotRegisterAnUnusableProvider() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var clients = context.getBean(ClientRegistrationRepository.class);
            assertThat(clients.findByRegistrationId("google")).isNull();
            assertThat(clients.findByRegistrationId("naver")).isNull();
        });
        runner.withPropertyValues("GOOGLE_CLIENT_ID=test-google-client").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ClientRegistrationRepository.class).findByRegistrationId("google")).isNull();
        });
    }

    @Test
    void oneCompleteProviderWorksWhileTheOtherRemainsDisabled() {
        runner.withPropertyValues("NAVER_CLIENT_ID=test-naver-client", "NAVER_CLIENT_SECRET=test-fixture-secret",
                "GOOGLE_CLIENT_ID=test-google-client").run(context -> {
            assertThat(context).hasNotFailed();
            var clients = context.getBean(ClientRegistrationRepository.class);
            assertThat(clients.findByRegistrationId("naver")).isNotNull();
            assertThat(clients.findByRegistrationId("google")).isNull();
        });
    }

    @Test
    void explicitSpringSettingsAndCallbackOverrideAreSupported() {
        runner.withPropertyValues("spring.security.oauth2.client.registration.google.client-id=test-google-client",
                "spring.security.oauth2.client.registration.google.client-secret=test-fixture-secret",
                "spring.security.oauth2.client.registration.google.redirect-uri=http://localhost:8081/login/oauth2/code/google")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var google = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("google");
                    assertThat(google.getScopes()).containsExactly("openid");
                    assertThat(google.getRedirectUri()).isEqualTo("http://localhost:8081/login/oauth2/code/google");
                });
    }

    @Test
    void suppliedRegistrationRepositoryIsPreserved() {
        var supplied = new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("naver")
                .clientId("test-client").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback").authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token").build());
        runner.withBean(ClientRegistrationRepository.class, () -> supplied).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ClientRegistrationRepository.class)).isSameAs(supplied);
        });
    }
}
