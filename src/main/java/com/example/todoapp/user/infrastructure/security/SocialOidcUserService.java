package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.service.SocialUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.List;

@Service
public class SocialOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final SocialUserService accounts;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public SocialOidcUserService(SocialUserService accounts) {
        this(accounts, new OidcUserService());
    }

    SocialOidcUserService(SocialUserService accounts, OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.accounts = accounts;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        try {
            if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
                throw new IllegalArgumentException();
            }
            // ID 토큰의 서명·issuer·audience·nonce 검증은 Spring Security OIDC 인증 흐름에서 수행한다.
            OidcUser verified = delegate.loadUser(request);
            var account = accounts.login("google", verified.getSubject());
            return new LocalOidcUser(verified, account.getUsername());
        } catch (OAuth2AuthenticationException | IllegalArgumentException | DataAccessException | TransactionException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("social_login_failed"),
                    "간편 로그인을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static final class LocalOidcUser extends DefaultOidcUser {
        private final String localUsername;

        private LocalOidcUser(OidcUser verified, String localUsername) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")), verified.getIdToken());
            this.localUsername = localUsername;
        }

        @Override
        public String getName() {
            return localUsername;
        }
    }
}
