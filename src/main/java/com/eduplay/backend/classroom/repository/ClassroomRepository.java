package com.eduplay.backend.classroom.repository;

import com.eduplay.backend.classroom.domain.Classroom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    Optional<Classroom> findByIdAndTeacherId(UUID id, UUID teacherId);

    List<Classroom> findByTeacherIdOrderByCreatedAtAscIdAsc(UUID teacherId);

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    @Modifying
    @Query("delete from Classroom c where c.teacherId = :teacherId")
    int deleteAllForTeacher(@Param("teacherId") UUID teacherId);

    @Query(value = "SELECT count(*) FROM classroom_children cc JOIN children c ON c.id = cc.child_id "
            + "WHERE cc.classroom_id = :classroomId AND c.deleted_at IS NULL", nativeQuery = true)
    long countActiveChildren(@Param("classroomId") UUID classroomId);

    /** Links a child to a class; linking an already linked child does nothing. */
    @Modifying
    @Query(value = "INSERT INTO classroom_children (classroom_id, child_id, added_at) VALUES (:classroomId, :childId, :now) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int link(@Param("classroomId") UUID classroomId, @Param("childId") UUID childId, @Param("now") Instant now);

    @Modifying
    @Query(value = "DELETE FROM classroom_children WHERE classroom_id = :classroomId AND child_id = :childId",
            nativeQuery = true)
    int unlink(@Param("classroomId") UUID classroomId, @Param("childId") UUID childId);

    /** Classes a child is linked to, with the teacher's name, for the child's owner. */
    @Query(value = """
            SELECT r.id AS id, r.name AS name, r.grade_id AS gradeId, u.full_name AS teacherName, cc.added_at AS linkedAt
            FROM classroom_children cc
            JOIN classrooms r ON r.id = cc.classroom_id
            JOIN users u ON u.id = r.teacher_id
            WHERE cc.child_id = :childId AND u.deleted_at IS NULL
            ORDER BY cc.added_at, r.id
            """, nativeQuery = true)
    List<LinkedClassView> findLinkedClasses(@Param("childId") UUID childId);

    interface LinkedClassView {
        UUID getId();

        String getName();

        String getGradeId();

        String getTeacherName();

        Instant getLinkedAt();
    }
}
