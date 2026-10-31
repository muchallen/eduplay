package com.eduplay.backend.user.repository;

import com.eduplay.backend.user.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /** Hard-deletes soft-deleted accounts; children, progress, results and tokens cascade. */
    @Modifying
    @Query("delete from User u where u.deletedAt is not null and u.deletedAt < :cutoff")
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);
}
