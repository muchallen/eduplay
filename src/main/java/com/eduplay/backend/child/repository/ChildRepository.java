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

    /** The only lookup used for child access: id AND owning parent AND not deleted. */
    Optional<Child> findByIdAndParentIdAndDeletedAtIsNull(UUID id, UUID parentId);

    Page<Child> findByParentIdAndDeletedAtIsNull(UUID parentId, Pageable pageable);

    List<Child> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID parentId);

    long countByParentIdAndDeletedAtIsNull(UUID parentId);

    @Modifying
    @Query("update Child c set c.deletedAt = :now, c.updatedAt = :now where c.parentId = :parentId and c.deletedAt is null")
    int softDeleteAllForParent(@Param("parentId") UUID parentId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Child c where c.deletedAt is not null and c.deletedAt < :cutoff")
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);
}
