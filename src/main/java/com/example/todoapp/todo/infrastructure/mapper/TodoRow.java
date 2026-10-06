package com.example.todoapp.todo.infrastructure.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MyBatis가 읽고 쓰는 DB 행. 등록 후 생성된 기본키도 이 객체에 채운다.
 */
@Getter
@Setter
public class TodoRow {

    private Integer id;
    private String title;
    private String detail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
