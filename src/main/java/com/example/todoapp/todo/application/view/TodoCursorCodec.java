package com.example.todoapp.todo.application.view;

import com.example.todoapp.todo.domain.model.TodoCursor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** URL에 담는 목록 위치를 변환한다. 인증이나 권한 판단에는 사용하지 않는다. */
public final class TodoCursorCodec {
    private TodoCursorCodec() {
    }

    public static String encode(TodoCursor cursor) {
        String value = "1|" + (cursor.createdAt() == null ? "" : cursor.createdAt()) + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static TodoCursor decode(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("올바른 목록 위치가 필요합니다.");
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !"1".equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            return new TodoCursor(parts[1].isEmpty() ? null : LocalDateTime.parse(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("올바른 목록 위치가 필요합니다.");
        }
    }
}
