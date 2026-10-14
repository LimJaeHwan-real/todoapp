package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.service.PasswordHasher;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return passwordEncoder::encode;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations,
            ObjectProvider<SocialOAuth2UserService> oauthUsers,
            ObjectProvider<SocialOidcUserService> oidcUsers) throws Exception {
        var clients = registrations.getIfAvailable();
        if (clients != null && (clients.findByRegistrationId("google") != null || clients.findByRegistrationId("naver") != null)) {
            http.oauth2Login(login -> login
                    .loginPage("/login")
                    .defaultSuccessUrl("/todos", true)
                    .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                    .userInfoEndpoint(users -> users.userService(oauthUsers.getObject()).oidcUserService(oidcUsers.getObject()))
                    .failureHandler((request, response, exception) -> {
                        var session = request.getSession(false);
                        if (session != null) {
                            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
                        }
                        response.sendRedirect(request.getContextPath() + "/login?socialError");
                    }).permitAll());
        }
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/signup", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/todos", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
