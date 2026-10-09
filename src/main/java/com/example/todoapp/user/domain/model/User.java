package com.example.todoapp.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
public class User {

    private final Integer id;
    private final String username;
    private final String passwordHash;

    public static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.strip();
        if (!normalized.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, 점, 밑줄, 하이픈으로 3~50자 입력해 주세요.");
        }
        return normalized;
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank()
                || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요.");
        }
    }
}
