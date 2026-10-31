package com.eduplay.backend.child.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.child.dto.CreateChildRequest;
import com.eduplay.backend.child.dto.UpdateChildRequest;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.config.EduplayProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildService {

    private static final int MAX_CHILD_AGE_YEARS = 18;

    private final ChildRepository childRepository;
    private final ChildAccessService childAccessService;
    private final EduplayProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ChildResponse> list(UUID parentId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending().and(Sort.by("id")));
        return PageResponse.of(childRepository.findByParentIdAndDeletedAtIsNull(parentId, pageable),
                ChildResponse::from);
    }

    @Transactional(readOnly = true)
    public ChildResponse get(UUID parentId, UUID childId) {
        return ChildResponse.from(childAccessService.requireOwnedChild(parentId, childId));
    }

    @Transactional
    public ChildResponse create(UUID parentId, CreateChildRequest request) {
        int max = properties.children().maxPerParent();
        if (childRepository.countByParentIdAndDeletedAtIsNull(parentId) >= max) {
            throw ApiException.conflict("CHILD_LIMIT_REACHED", "A parent account can have at most " + max + " children");
        }
        validateBirthYear(request.birthYear());
        Child child = new Child(parentId, request.displayName().trim(), request.birthYear(), request.avatarId(),
                clock.instant());
        return ChildResponse.from(childRepository.save(child));
    }

    @Transactional
    public ChildResponse update(UUID parentId, UUID childId, UpdateChildRequest request) {
        Child child = childAccessService.requireOwnedChild(parentId, childId);
        if (request.displayName() != null) {
            child.setDisplayName(request.displayName().trim());
        }
        if (request.birthYear() != null) {
            validateBirthYear(request.birthYear());
            child.setBirthYear(request.birthYear());
        }
        if (request.avatarId() != null) {
            child.setAvatarId(request.avatarId());
        }
        child.setUpdatedAt(clock.instant());
        return ChildResponse.from(childRepository.save(child));
    }

    /** Soft delete; the profile and its learning data are purged permanently after the retention period. */
    @Transactional
    public void delete(UUID parentId, UUID childId) {
        Child child = childAccessService.requireOwnedChild(parentId, childId);
        Instant now = clock.instant();
        child.setDeletedAt(now);
        child.setUpdatedAt(now);
        childRepository.save(child);
    }

    private void validateBirthYear(Integer birthYear) {
        if (birthYear == null) {
            return;
        }
        int currentYear = clock.instant().atZone(ZoneOffset.UTC).getYear();
        if (birthYear > currentYear || birthYear < currentYear - MAX_CHILD_AGE_YEARS) {
            throw ApiException.badRequest("INVALID_BIRTH_YEAR",
                    "birthYear must be between " + (currentYear - MAX_CHILD_AGE_YEARS) + " and " + currentYear);
        }
    }
}
