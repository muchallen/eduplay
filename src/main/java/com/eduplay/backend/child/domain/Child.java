package com.eduplay.backend.child.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A child profile. Children never have credentials of their own. The owner ({@code parentId}) is
 * the account that created the profile: normally a parent, or a teacher for a pupil without a
 * parent account. Teachers can also reach a child through a class it is linked to.
 */
@Entity
@Table(name = "children")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "avatar_id", length = 64)
    private String avatarId;

    @Column(name = "grade_id", nullable = false, length = 32)
    private String gradeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Child(UUID parentId, String displayName, Integer birthYear, String avatarId, String gradeId,
                 Instant now) {
        this.parentId = parentId;
        this.displayName = displayName;
        this.birthYear = birthYear;
        this.avatarId = avatarId;
        this.gradeId = gradeId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
