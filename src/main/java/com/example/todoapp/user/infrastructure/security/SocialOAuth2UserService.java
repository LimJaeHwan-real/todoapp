package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.service.SocialUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.List;
import java.util.Map;

@Service
public class SocialOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final SocialUserService accounts;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public SocialOAuth2UserService(SocialUserService accounts) {
        this(accounts, new DefaultOAuth2UserService());
    }

    SocialOAuth2UserService(SocialUserService accounts, OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.accounts = accounts;
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        try {
            if (!"naver".equals(request.getClientRegistration().getRegistrationId())) {
                throw new IllegalArgumentException();
            }
            Map<String, Object> attributes = delegate.loadUser(request).getAttributes();
            if (!"00".equals(attributes.get("resultcode"))
                    || !(attributes.get("response") instanceof Map<?, ?> profile)
                    || !(profile.get("id") instanceof String subject)) {
                throw new IllegalArgumentException();
            }
            var account = accounts.login("naver", subject);
            // Principal 이름은 기존 작성자 조회에 필요한 로컬 아이디다. 프로필과 토큰은 DB에 저장하지 않는다.
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    Map.of("username", account.getUsername()), "username");
        } catch (OAuth2AuthenticationException | IllegalArgumentException | DataAccessException | TransactionException exception) {
            // 인증 실패를 로깅하는 프레임워크에 제공업체 응답이나 DB 원인을 전달하지 않는다.
            throw new OAuth2AuthenticationException(new OAuth2Error("social_login_failed"),
                    "간편 로그인을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
