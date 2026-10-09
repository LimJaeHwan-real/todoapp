package com.example.todoapp.user.infrastructure.mapper;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRow {
    private Integer id;
    private String username;
    private String passwordHash;
}
