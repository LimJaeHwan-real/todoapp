package com.example.todoapp.todo.application.view;

import com.example.todoapp.todo.domain.model.TodoCursor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class TodoCursorCodecTests {
    @Test
    void roundTripKeepsDatePrecisionAndSupportsUndatedRows() {
        for (var cursor : new TodoCursor[]{new TodoCursor(null, 7),
                new TodoCursor(LocalDateTime.of(2026, 10, 14, 1, 2, 3, 456789000), 8)}) {
            String encoded = TodoCursorCodec.encode(cursor);
            assertThat(encoded).doesNotContain("+", "/", "=");
            assertThat(TodoCursorCodec.decode(encoded)).isEqualTo(cursor);
        }
    }

    @Test
    void invalidVersionsDatesIdsAndOversizedValuesAreRejected() {
        for (String value : new String[]{"2||7", "1||0", "1||-1", "1|invalid|7", "1||2147483648", "1||7|extra",
                "1|+999999999-01-01T00:00|7", "1|-999999999-01-01T00:00|7",
                "1|+294276-12-31T23:59:59.999999999|7"}) {
            String encoded = Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> TodoCursorCodec.decode(encoded)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TodoCursorCodec.decode("a".repeat(129))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TodoCursorCodec.decode(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
