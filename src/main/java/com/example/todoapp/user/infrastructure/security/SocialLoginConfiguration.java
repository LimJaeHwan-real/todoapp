package com.example.todoapp.user.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 인증정보가 완성된 제공업체만 등록한다. 미설정 상태에서도 기존 로그인으로 앱을 실행할 수 있다. */
@Configuration(proxyBeanMethods = false)
public class SocialLoginConfiguration {
    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository socialClientRegistrations(Environment environment) {
        var clients = new ArrayList<ClientRegistration>();
        String googleId = credential(environment, "google", "client-id", "GOOGLE_CLIENT_ID");
        String googleSecret = credential(environment, "google", "client-secret", "GOOGLE_CLIENT_SECRET");
        if (configured(googleId) && configured(googleSecret)) {
            clients.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret).scope("openid")
                    .redirectUri(redirectUri(environment, "google")).build());
        }
        String naverId = credential(environment, "naver", "client-id", "NAVER_CLIENT_ID");
        String naverSecret = credential(environment, "naver", "client-secret", "NAVER_CLIENT_SECRET");
        if (configured(naverId) && configured(naverSecret)) {
            clients.add(ClientRegistration.withRegistrationId("naver")
                    .clientId(naverId).clientSecret(naverSecret).clientName("Naver")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirectUri(environment, "naver"))
                    .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                    .tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me")
                    .userNameAttributeName("response").build());
        }
        return new SocialClients(clients);
    }

    private static String credential(Environment environment, String provider, String field, String variable) {
        return environment.getProperty("spring.security.oauth2.client.registration." + provider + "." + field,
                environment.getProperty(variable, ""));
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    private static String redirectUri(Environment environment, String provider) {
        return environment.getProperty("spring.security.oauth2.client.registration." + provider + ".redirect-uri",
                "{baseUrl}/login/oauth2/code/" + provider);
    }

    private static final class SocialClients implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        private final List<ClientRegistration> clients;

        private SocialClients(List<ClientRegistration> clients) {
            this.clients = List.copyOf(clients);
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return clients.stream().filter(client -> client.getRegistrationId().equals(registrationId)).findFirst().orElse(null);
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return clients.iterator();
        }
    }
}
