package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTests {
    @Test
    void loginDatabaseFailuresHideSqlAndConnectionDetailsFromAuthenticationFilter() {
        var repository = mock(UserRepository.class);
        when(repository.findByUsername("member"))
                .thenThrow(new CannotGetJdbcConnectionException("internal database marker"));
        var service = new DatabaseUserDetailsService(repository);
        assertThatThrownBy(() -> service.loadUserByUsername("member"))
                .isInstanceOf(InternalAuthenticationServiceException.class)
                .hasMessage("로그인을 처리할 수 없습니다.")
                .hasNoCause();
    }
}
