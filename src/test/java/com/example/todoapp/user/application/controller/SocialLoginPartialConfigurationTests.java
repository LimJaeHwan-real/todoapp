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

/** social 프로필을 선택해도 일부 미설정 때문에 기존 로그인 화면이 중단되지 않아야 한다. */
@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.main.banner-mode=off", "logging.level.root=OFF",
        "spring.test.mockmvc.print=NONE", "GOOGLE_CLIENT_ID=test-google-client", "GOOGLE_CLIENT_SECRET=test-fixture-secret",
        "NAVER_CLIENT_ID=test-naver-client", "NAVER_CLIENT_SECRET="})
@ActiveProfiles("social")
@AutoConfigureMockMvc
class SocialLoginPartialConfigurationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ClientRegistrationRepository clients;
    @MockitoBean private UserRepositoryImpl users;
    @MockitoBean private TodoRepository todos;

    @Test
    void completeGoogleConfigurationAndIncompleteNaverConfigurationKeepAppUsable() throws Exception {
        assertThat(clients.findByRegistrationId("google")).isNotNull();
        assertThat(clients.findByRegistrationId("naver")).isNull();
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(xpath("//form[@action='/login'][@method='post']").exists())
                .andExpect(xpath("//form/input[@name='_csrf']").exists())
                .andExpect(xpath("//a[@href='/oauth2/authorization/google']").exists())
                .andExpect(xpath("//a[@href='/oauth2/authorization/naver']").doesNotExist())
                .andExpect(xpath("//button[@disabled][contains(.,'네이버로 로그인')]").exists());
    }
}
