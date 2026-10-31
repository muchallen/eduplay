package com.eduplay.backend.grade;

import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.config.EduplayProperties;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The school grades the app teaches (configured by GRADES, default {@code ecd_a,ecd_b}). Topic ids
 * repeat across grades, so every child, lesson position and game result carries a grade id.
 */
@Component
public class GradeCatalog {

    /** Shape of a grade id; whether the grade exists is checked against the configured list. */
    public static final String PATTERN = "^[a-z0-9_]{1,32}$";
    public static final String MESSAGE = "must be a grade id such as ecd_a";

    private final Set<String> allowed;

    public GradeCatalog(EduplayProperties properties) {
        this.allowed = Set.copyOf(properties.grades().allowed().stream().map(String::trim).toList());
    }

    /** @throws ApiException 400 INVALID_GRADE when the grade is not taught */
    public String require(String gradeId) {
        if (gradeId == null || !allowed.contains(gradeId)) {
            throw ApiException.badRequest("INVALID_GRADE",
                    "Unknown gradeId '" + gradeId + "'; expected one of " + allowed.stream().sorted().toList());
        }
        return gradeId;
    }

    /** Validates an optional grade filter; null means all grades. */
    public String requireIfPresent(String gradeId) {
        return gradeId == null ? null : require(gradeId);
    }
}
