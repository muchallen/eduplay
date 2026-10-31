package com.eduplay.backend.classroom.domain;

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
 * A teacher's class. Children are linked to it either by the teacher (children the teacher owns)
 * or by a parent entering the class join code. Teachers can read, report on and sync linked children.
 */
@Entity
@Table(name = "classrooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "grade_id", length = 32)
    private String gradeId;

    @Column(name = "join_code", nullable = false, length = 16)
    private String joinCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Classroom(UUID teacherId, String name, String gradeId, String joinCode, Instant now) {
        this.teacherId = teacherId;
        this.name = name;
        this.gradeId = gradeId;
        this.joinCode = joinCode;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
