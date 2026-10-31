package com.eduplay.backend.common.util;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** Canonical form used for storage and lookups: trimmed and lower-cased. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
