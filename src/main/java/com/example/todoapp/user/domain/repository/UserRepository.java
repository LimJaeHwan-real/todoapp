package com.example.todoapp.user.domain.repository;

import com.example.todoapp.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    void insert(User user);
}
