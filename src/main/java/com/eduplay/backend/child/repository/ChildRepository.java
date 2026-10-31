package com.eduplay.backend.child.repository;

import com.eduplay.backend.child.domain.Child;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildRepository extends JpaRepository<Child, UUID> {

    /** Children a teacher reaches through one of their classes. */
    String TAUGHT_BY = "EXISTS (SELECT 1 FROM classroom_children cc JOIN classrooms r ON r.id = cc.classroom_id "
            + "WHERE cc.child_id = c.id AND r.teacher_id = :userId)";

    Optional<Child> findByIdAndParentIdAndDeletedAtIsNull(UUID id, UUID parentId);

    /** Id AND (owned by the user OR linked to one of the user's classes) AND not deleted. */
    @Query(value = "SELECT c.* FROM children c WHERE c.id = :childId AND c.deleted_at IS NULL "
            + "AND (c.parent_id = :userId OR " + TAUGHT_BY + ")", nativeQuery = true)
    Optional<Child> findAccessible(@Param("childId") UUID childId, @Param("userId") UUID userId);

    /** Children the user owns or teaches, oldest first. */
    @Query(value = "SELECT c.* FROM children c WHERE c.deleted_at IS NULL "
            + "AND (c.parent_id = :userId OR " + TAUGHT_BY + ") ORDER BY c.created_at, c.id",
            countQuery = "SELECT count(*) FROM children c WHERE c.deleted_at IS NULL "
                    + "AND (c.parent_id = :userId OR " + TAUGHT_BY + ")",
            nativeQuery = true)
    Page<Child> findAccessible(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT c.* FROM children c JOIN classroom_children cc ON cc.child_id = c.id "
            + "WHERE cc.classroom_id = :classroomId AND c.deleted_at IS NULL ORDER BY c.display_name, c.id",
            countQuery = "SELECT count(*) FROM children c JOIN classroom_children cc ON cc.child_id = c.id "
                    + "WHERE cc.classroom_id = :classroomId AND c.deleted_at IS NULL",
            nativeQuery = true)
    Page<Child> findInClassroom(@Param("classroomId") UUID classroomId, Pageable pageable);

    List<Child> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID parentId);

    long countByParentIdAndDeletedAtIsNull(UUID parentId);

    @Modifying
    @Query("update Child c set c.deletedAt = :now, c.updatedAt = :now where c.parentId = :parentId and c.deletedAt is null")
    int softDeleteAllForParent(@Param("parentId") UUID parentId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Child c where c.deletedAt is not null and c.deletedAt < :cutoff")
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);
}
