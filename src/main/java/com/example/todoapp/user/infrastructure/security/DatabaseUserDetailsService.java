package com.example.todoapp.user.infrastructure.security;

import com.example.todoapp.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        try {
            var account = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("로그인 정보를 확인해 주세요."));
            return User.withUsername(account.getUsername())
                    .password(account.getPasswordHash()).roles("USER").build();
        } catch (DataAccessException exception) {
            // 인증 필터가 실패를 기록할 때 SQL 또는 연결정보를 출력하지 않도록 원인을 전달하지 않는다.
            throw new InternalAuthenticationServiceException("로그인을 처리할 수 없습니다.");
        }
    }
}
