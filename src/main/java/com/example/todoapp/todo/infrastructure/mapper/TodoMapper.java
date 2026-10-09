package com.example.todoapp.todo.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SQL과 DB 행 매핑은 mybatis/TodoMapper.xml에서 정의한다.
 */
@Mapper
public interface TodoMapper {

    List<TodoRow> findAll();

    TodoRow findById(@Param("id") Integer id);

    int insert(TodoRow row);

    int update(TodoRow row);

    int deleteById(@Param("id") Integer id, @Param("authorId") Integer authorId);
}
