package com.example.todoapp.user.application.controller;

import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.user.infrastructure.repository.UserRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 인증정보가 준비되면 별도 프로필 없이 두 로그인 흐름을 사용할 수 있어야 한다. */
@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.main.banner-mode=off", "logging.level.root=OFF",
        "spring.test.mockmvc.print=NONE", "GOOGLE_CLIENT_ID=test-google-client", "GOOGLE_CLIENT_SECRET=test-fixture-secret",
        "NAVER_CLIENT_ID=test-naver-client", "NAVER_CLIENT_SECRET=test-fixture-secret"})
@AutoConfigureMockMvc
class SocialLoginAutoConfigurationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectProvider<ClientRegistrationRepository> clients;
    @MockitoBean private UserRepositoryImpl users;
    @MockitoBean private TodoRepository todos;

    @Test
    void completeCredentialsEnableBothLoginButtonsWithoutManualProfileActivation() throws Exception {
        assertThat(clients.getIfAvailable()).isNotNull();
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(xpath("//section[@aria-label='간편 로그인']/a").nodeCount(2))
                .andExpect(xpath("//a[@href='/oauth2/authorization/naver']").exists())
                .andExpect(xpath("//a[@href='/oauth2/authorization/google']").exists());
    }

    @Test
    void bothAuthorizationRoutesRedirectToTheirActualProviderWithoutCallingExternalApis() throws Exception {
        var google = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var naver = mvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(URI.create(google.getResponse().getRedirectedUrl()).getHost()).isEqualTo("accounts.google.com");
        assertThat(URI.create(naver.getResponse().getRedirectedUrl()).getHost()).isEqualTo("nid.naver.com");
    }
}
