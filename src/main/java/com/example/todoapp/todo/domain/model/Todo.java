package com.example.todoapp.todo.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 할일의 제목과 생성·수정 규칙을 관리하는 불변 도메인 객체.
 * 전체 필드 생성자는 저장된 값을 복원할 때 사용한다.
 */
@Getter
@AllArgsConstructor
public final class Todo {

    private final Integer id;
    private final String title;
    private final String detail;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Integer authorId;

    /** 작성자 없는 기존 데이터를 복원한다. 해당 항목은 수정·삭제할 수 없다. */
    public Todo(Integer id, String title, String detail, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, title, detail, createdAt, updatedAt, null);
    }

    public static Todo create(String title, String detail, Integer authorId) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("작성자가 필요합니다.");
        }
        Todo validated = create(title, detail);
        return new Todo(null, validated.title, validated.detail, validated.createdAt, validated.updatedAt, authorId);
    }

    public boolean isOwnedBy(Integer memberId) {
        return authorId != null && authorId.equals(memberId);
    }

    /**
     * 등록 전 기본키는 비워 두고 등록일과 수정일을 같은 서버 시간으로 설정한다.
     */
    public static Todo create(String title, String detail) {
        String normalizedTitle = normalizeTitle(title);
        LocalDateTime now = LocalDateTime.now();
        return new Todo(null, normalizedTitle, detail, now, now);
    }

    /**
     * 기본키와 등록일을 유지한 채 변경된 정보를 담은 새 객체를 반환한다.
     */
    public Todo update(String title, String detail) {
        String normalizedTitle = normalizeTitle(title);
        return new Todo(id, normalizedTitle, detail, createdAt, LocalDateTime.now(), authorId);
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        String normalizedTitle = title.strip();
        int titleLength = normalizedTitle.codePointCount(0, normalizedTitle.length());
        if (titleLength == 0 || titleLength > 255) {
            throw new IllegalArgumentException("제목은 앞뒤 공백을 제외하고 1~255자여야 합니다.");
        }
        return normalizedTitle;
    }
}
