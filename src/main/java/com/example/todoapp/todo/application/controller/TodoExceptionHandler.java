package com.example.todoapp.todo.application.controller;

import com.example.todoapp.todo.domain.service.TodoNotFoundException;
import com.example.todoapp.todo.domain.service.TodoAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;

@ControllerAdvice(assignableTypes = TodoController.class)
public class TodoExceptionHandler {

    @ExceptionHandler(TodoAccessDeniedException.class)
    public ModelAndView forbidden() {
        return error("403", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(TodoNotFoundException.class)
    public ModelAndView notFound() {
        return error("404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView invalidId() {
        return error("400", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView invalidRequest(ResponseStatusException exception) {
        return error("400", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView unsupportedMethod() {
        return error("400", HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView internalFailure(Exception exception) {
        // 트랜잭션·MyBatis가 감싼 연결 실패도 원인을 확인한다. 메시지는 화면에 전달하지 않는다.
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof CannotGetJdbcConnectionException
                    || (cause instanceof SQLException sqlException
                    && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("08"))) {
                return error("503", HttpStatus.SERVICE_UNAVAILABLE);
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        ModelAndView result = error("503", HttpStatus.INTERNAL_SERVER_ERROR);
        result.addObject("internalError", true);
        return result;
    }

    private static ModelAndView error(String code, HttpStatus status) {
        ModelAndView result = new ModelAndView("error/" + code);
        result.setStatus(status);
        return result;
    }
}
