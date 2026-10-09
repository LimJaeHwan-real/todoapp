package com.example.todoapp.user.domain.service;

import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public void register(String username, String password) {
        String normalized = User.normalizeUsername(username);
        User.validatePassword(password);
        if (userRepository.findByUsername(normalized).isPresent()) {
            throw new DuplicateUsernameException();
        }
        userRepository.insert(new User(null, normalized, passwordHasher.hash(password)));
    }
}
