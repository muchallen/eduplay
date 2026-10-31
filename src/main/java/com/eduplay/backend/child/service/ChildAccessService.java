package com.eduplay.backend.child.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.common.exception.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single gate for every operation on child data (profiles, progress, game results, reports).
 *
 * <p>The caller's id always comes from the verified access token, never from the request. A child
 * the caller cannot reach is reported as not found (404) rather than forbidden, so ids of other
 * families' children cannot be probed. Two levels of access exist:
 * <ul>
 *   <li><b>owner</b> (the account that created the profile): everything, including edit and delete;</li>
 *   <li><b>teacher</b> of a class the child is linked to: read, reports and sync.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChildAccessService {

    private final ChildRepository childRepository;

    /** Owner or linked teacher: read, report and sync. */
    @Transactional(readOnly = true)
    public Child requireAccessibleChild(UUID userId, UUID childId) {
        return childRepository.findAccessible(childId, userId).orElseThrow(ChildAccessService::notFound);
    }

    /** Owner only: edit, delete and class membership. A linked teacher gets 403 CHILD_NOT_OWNED. */
    @Transactional(readOnly = true)
    public Child requireOwnedChild(UUID userId, UUID childId) {
        Child child = requireAccessibleChild(userId, childId);
        if (!child.getParentId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CHILD_NOT_OWNED",
                    "Only the account that created this child profile can change it");
        }
        return child;
    }

    private static ApiException notFound() {
        return ApiException.notFound("CHILD_NOT_FOUND", "Child profile not found");
    }
}
