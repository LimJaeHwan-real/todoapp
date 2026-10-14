package com.example.todoapp.user.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserRow findByUsername(@Param("username") String username);
    int insert(UserRow row);
    UserRow findOrCreateSocialUser(@Param("provider") String provider, @Param("subject") String subject,
                                 @Param("username") String username);
}
