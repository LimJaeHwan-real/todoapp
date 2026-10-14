package com.example.todoapp.todo.domain.model;

import java.util.regex.Pattern;

public final class TodoSearchKeyword {

    private static final Pattern SEARCHABLE_WORD = Pattern.compile("[\\p{L}\\p{N}]{3}");

    private TodoSearchKeyword() {
    }

    public static String normalize(String input) {
        String keyword = input == null ? "" : input.strip();
        if (keyword.isEmpty()) {
            return keyword;
        }
        if (keyword.codePointCount(0, keyword.length()) > 255) {
            throw new IllegalArgumentException("검색어는 255자 이내로 입력해 주세요.");
        }
        // 공백·기호만으로 3자를 채우면 부분 검색에 사용할 trigram이 생기지 않는다.
        if (!SEARCHABLE_WORD.matcher(keyword).find()) {
            throw new IllegalArgumentException("연속된 글자 또는 숫자를 3자 이상 입력해 주세요.");
        }
        return keyword;
    }
}
