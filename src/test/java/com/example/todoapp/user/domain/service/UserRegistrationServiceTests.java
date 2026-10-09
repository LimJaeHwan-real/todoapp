package com.example.todoapp.user.domain.service;

import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserRegistrationServiceTests {
    private final UserRepository repository = mock(UserRepository.class);
    private final SecurityConfiguration configuration = new SecurityConfiguration();
    private final UserRegistrationService service = new UserRegistrationService(repository,
            configuration.passwordHasher(configuration.passwordEncoder()));

    @Test
    void registrationNormalizesUsernameAndSavesSaltedBcryptInsteadOfRawPassword() {
        String raw = UUID.randomUUID().toString();
        service.register(" member ", raw);
        service.register("other", raw);
        var captor = ArgumentCaptor.forClass(User.class);
        verify(repository, times(2)).insert(captor.capture());
        var accounts = captor.getAllValues();
        assertThat(accounts.getFirst().getUsername()).isEqualTo("member");
        assertThat(accounts.getFirst().getPasswordHash().equals(raw)).isFalse();
        assertThat(configuration.passwordEncoder().matches(raw, accounts.getFirst().getPasswordHash())).isTrue();
        assertThat(accounts.getFirst().getPasswordHash().equals(accounts.getLast().getPasswordHash())).isFalse();
    }

    @Test
    void existingUsernameCannotBeRegisteredAgain() {
        when(repository.findByUsername("member")).thenReturn(Optional.of(new User(1, "member", "unused")));
        assertThatThrownBy(() -> service.register("member", UUID.randomUUID().toString()))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(repository, never()).insert(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "ab", "contains space", "<script>", "한글아이디"})
    void invalidUsernamesAreRejectedBeforeDatabaseAccess(String username) {
        assertThatThrownBy(() -> service.register(username, UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "        "})
    void invalidPasswordsAreRejectedBeforeDatabaseAccess(String password) {
        assertThatThrownBy(() -> service.register("member", password)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void bcryptByteLimitRejectsLongAsciiAndUnicodePasswordsWithoutTruncation() {
        assertThatThrownBy(() -> service.register("member", "a".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("member", "가".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
        service.register("member", "가".repeat(24));
        var captor = ArgumentCaptor.forClass(User.class);
        verify(repository).insert(captor.capture());
        assertThat(configuration.passwordEncoder().matches("가".repeat(24), captor.getValue().getPasswordHash())).isTrue();
    }
}
