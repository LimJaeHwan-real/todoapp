package com.example.todoapp.user.infrastructure.repository;

import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.repository.UserRepository;
import com.example.todoapp.user.domain.repository.SocialUserRepository;
import com.example.todoapp.user.domain.service.DuplicateUsernameException;
import com.example.todoapp.user.infrastructure.mapper.UserMapper;
import com.example.todoapp.user.infrastructure.mapper.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository, SocialUserRepository {

    private final UserMapper userMapper;

    @Override
    @Transactional
    public User findOrCreate(String provider, String subject) {
        UserRow row = userMapper.findOrCreateSocialUser(provider, subject, provider + "_" + UUID.randomUUID());
        return new User(row.getId(), row.getUsername(), row.getPasswordHash());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username))
                .map(row -> new User(row.getId(), row.getUsername(), row.getPasswordHash()));
    }

    @Override
    public void insert(User user) {
        UserRow row = new UserRow();
        row.setUsername(user.getUsername());
        row.setPasswordHash(user.getPasswordHash());
        try {
            userMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            // 동시 가입도 UNIQUE 제약으로 막고 SQL·해시를 예외 메시지에 남기지 않는다.
            throw new DuplicateUsernameException();
        }
    }
}
