package com.example.todoapp.todo.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoTests {

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 9, 0);
    private final LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void createNormalizesTitleAndSetsBothDatesToTheSameServerTime() {
        LocalDateTime before = LocalDateTime.now();

        Todo todo = Todo.create(" \t새 할일\n ", " 내용은 그대로 ");

        assertThat(todo.getId()).isNull();
        assertThat(todo.getTitle()).isEqualTo("새 할일");
        assertThat(todo.getDetail()).isEqualTo(" 내용은 그대로 ");
        assertThat(todo.getCreatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(todo.getUpdatedAt()).isEqualTo(todo.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void createAcceptsTitleLengthBoundariesAfterStrippingWhitespace(int length) {
        String title = "가".repeat(length);

        Todo todo = Todo.create("\u2003" + title + "\u2003", null);

        assertThat(todo.getTitle()).isEqualTo(title);
        assertThat(todo.getDetail()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void createRejectsMissingOrBlankTitle(String title) {
        assertThatThrownBy(() -> Todo.create(title, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsTitleLongerThan255Characters() {
        assertThatThrownBy(() -> Todo.create("가".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 255})
    void createCountsSupplementaryUnicodeCharactersAsSingleCharacters(int length) {
        String title = "\uD83D\uDE00".repeat(length);

        assertThat(Todo.create(" " + title + " ", null).getTitle()).isEqualTo(title);
    }

    @Test
    void createRejectsMoreThan255SupplementaryUnicodeCharacters() {
        assertThatThrownBy(() -> Todo.create("\uD83D\uDE00".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePreservesIdentityAndCreationDateAndReturnsANewObject() {
        Todo original = new Todo(7, "기존 제목", "기존 내용", createdAt, updatedAt);
        LocalDateTime before = LocalDateTime.now();

        Todo updated = original.update(" 변경 제목 ", null);

        assertThat(updated.getId()).isEqualTo(7);
        assertThat(updated.getTitle()).isEqualTo("변경 제목");
        assertThat(updated.getDetail()).isNull();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(original).extracting(Todo::getTitle, Todo::getDetail, Todo::getUpdatedAt)
                .containsExactly("기존 제목", "기존 내용", updatedAt);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void updateAcceptsTitleLengthBoundaries(int length) {
        Todo original = new Todo(7, "기존 제목", null, createdAt, updatedAt);

        assertThat(original.update(" " + "가".repeat(length) + " ", "변경 내용").getTitle())
                .isEqualTo("가".repeat(length));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void updateRejectsMissingOrBlankTitleWithoutChangingOriginal(String title) {
        Todo original = new Todo(7, "기존 제목", null, createdAt, updatedAt);

        assertThatThrownBy(() -> original.update(title, "변경 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(original.getTitle()).isEqualTo("기존 제목");
        assertThat(original.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateRejectsTitleLongerThan255Characters() {
        Todo original = new Todo(7, "기존 제목", null, createdAt, updatedAt);

        assertThatThrownBy(() -> original.update("가".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 255})
    void updateCountsSupplementaryUnicodeCharactersAsSingleCharacters(int length) {
        Todo original = new Todo(7, "기존 제목", null, createdAt, updatedAt);
        String title = "\uD83D\uDE00".repeat(length);

        assertThat(original.update(" " + title + " ", null).getTitle()).isEqualTo(title);
    }

    @Test
    void updateRejectsMoreThan255SupplementaryUnicodeCharacters() {
        Todo original = new Todo(7, "기존 제목", null, createdAt, updatedAt);

        assertThatThrownBy(() -> original.update("\uD83D\uDE00".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
