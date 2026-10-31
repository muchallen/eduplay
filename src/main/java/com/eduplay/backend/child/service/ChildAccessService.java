package com.eduplay.backend.child.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.common.exception.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single gate for every operation on child data (profiles, progress, game results, reports).
 *
 * <p>The child is looked up by id <em>and</em> the authenticated parent's id taken from the
 * verified access token, never from the request. A child that belongs to someone else is reported
 * as not found (404) rather than forbidden, so ids of other families' children cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class ChildAccessService {

    private final ChildRepository childRepository;

    @Transactional(readOnly = true)
    public Child requireOwnedChild(UUID parentId, UUID childId) {
        return childRepository.findByIdAndParentIdAndDeletedAtIsNull(childId, parentId)
                .orElseThrow(() -> ApiException.notFound("CHILD_NOT_FOUND", "Child profile not found"));
    }
}
