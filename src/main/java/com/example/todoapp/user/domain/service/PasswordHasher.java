package com.example.todoapp.user.domain.service;

public interface PasswordHasher {
    String hash(String rawPassword);
}
