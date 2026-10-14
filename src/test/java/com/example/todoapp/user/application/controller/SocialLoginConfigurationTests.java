package com.example.todoapp.user.application.controller;

import com.example.todoapp.todo.domain.repository.TodoRepository;
import com.example.todoapp.user.infrastructure.repository.UserRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 배포용 social 프로필 자체를 로드한다. 외부 인증 요청과 DB 쓰기는 실행하지 않는다. */
@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.main.banner-mode=off", "logging.level.root=OFF",
        "GOOGLE_CLIENT_ID=test-google-client", "GOOGLE_CLIENT_SECRET=test-fixture-secret",
        "NAVER_CLIENT_ID=test-naver-client", "NAVER_CLIENT_SECRET=test-fixture-secret"})
@ActiveProfiles("social")
@AutoConfigureMockMvc
class SocialLoginConfigurationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ClientRegistrationRepository clients;
    @MockitoBean private UserRepositoryImpl users;
    @MockitoBean private TodoRepository todos;

    @Test
    void realProfileLoadsBothProvidersWithCorrectIdentityAndCallbackSettings() throws Exception {
        var google = clients.findByRegistrationId("google");
        var naver = clients.findByRegistrationId("naver");
        assertThat(google.getScopes()).containsExactly("openid");
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
        assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://openapi.naver.com/v1/nid/me");
        assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("response");
        assertThat(google.getRedirectUri()).endsWith("/login/oauth2/code/google");
        assertThat(naver.getRedirectUri()).endsWith("/login/oauth2/code/naver");
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(xpath("//a[@href='/oauth2/authorization/naver']").exists())
                .andExpect(xpath("//a[@href='/oauth2/authorization/google']").exists());
    }
}
