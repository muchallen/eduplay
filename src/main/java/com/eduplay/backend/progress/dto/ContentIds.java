package com.eduplay.backend.progress.dto;

/** Curriculum identifiers (section, topic, lesson) are short slugs defined by the Unity content. */
final class ContentIds {

    static final String PATTERN = "^[A-Za-z0-9_.-]{1,64}$";
    static final String MESSAGE = "must be 1-64 characters of letters, digits, '.', '-' or '_'";

    private ContentIds() {
    }
}
