package com.example.todoapp.todo.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.example.todoapp.todo.domain.model.TodoCursor;

import java.util.List;

/**
 * SQL과 DB 행 매핑은 mybatis/TodoMapper.xml에서 정의한다.
 */
@Mapper
public interface TodoMapper {

    long count();

    List<TodoRow> findPage(@Param("limit") int limit, @Param("offset") long offset);

    List<TodoRow> search(@Param("pattern") String pattern, @Param("cursor") TodoCursor cursor,
                        @Param("backwards") boolean backwards, @Param("limit") int limit);

    List<TodoRow> findAll();

    TodoRow findById(@Param("id") Integer id);

    int insert(TodoRow row);

    int update(TodoRow row);

    int deleteById(@Param("id") Integer id, @Param("authorId") Integer authorId);
}
