package com.example.todoapp.user.application.controller;

import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.service.UserRegistrationService;
import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@WebMvcTest(controllers = AuthenticationController.class, properties = "spring.test.mockmvc.print=NONE")
@Import({SecurityConfiguration.class, UserRegistrationService.class,
        AuthenticationTransactionFailureTests.TransactionConfiguration.class})
class AuthenticationTransactionFailureTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRegistrationService registrationService;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UserRepository repository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void transactionStartFailureReturnsSafe503SignupForm() throws Exception {
        assertThat(AopUtils.isAopProxy(registrationService)).isTrue();
        when(transactionManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException(
                "internal transaction marker", new SQLException("internal connection marker", "08001")));
        String password = UUID.randomUUID().toString();
        var result = mvc.perform(post("/signup").with(csrf())
                        .param("username", "member").param("password", password))
                .andExpect(status().isServiceUnavailable())
                .andExpect(xpath("//form[@action='/signup'][@method='post']").exists())
                .andExpect(xpath("//input[@name='password'][not(@value)]").exists()).andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(html.contains("internal")).isFalse();
        assertThat(html.contains(password)).isFalse();
        verifyNoInteractions(repository);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
