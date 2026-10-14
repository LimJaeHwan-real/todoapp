package com.example.todoapp.user.domain.repository;

import com.example.todoapp.user.domain.model.User;

public interface SocialUserRepository {
    /** 제공업체와 고유 식별값으로 회원을 찾거나 생성한다. 동시 요청도 같은 회원을 반환한다. */
    User findOrCreate(String provider, String subject);
}
