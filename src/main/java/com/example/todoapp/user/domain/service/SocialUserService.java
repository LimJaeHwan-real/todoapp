package com.example.todoapp.user.domain.service;

import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.SocialUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialUserService {
    private final SocialUserRepository repository;

    public User login(String provider, String subject) {
        if (!("google".equals(provider) || "naver".equals(provider))
                || subject == null || subject.isBlank() || subject.length() > 255) {
            throw new IllegalArgumentException("간편 로그인 회원 정보를 확인할 수 없습니다.");
        }
        return repository.findOrCreate(provider, subject);
    }
}
