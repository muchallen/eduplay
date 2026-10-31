package com.eduplay.backend.child.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.child.dto.CreateChildRequest;
import com.eduplay.backend.child.dto.UpdateChildRequest;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.grade.GradeCatalog;
import com.eduplay.backend.security.AuthenticatedUser;
import com.eduplay.backend.user.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildService {

    private static final int MAX_CHILD_AGE_YEARS = 18;

    private final ChildRepository childRepository;
    private final ChildAccessService childAccessService;
    private final GradeCatalog gradeCatalog;
    private final EduplayProperties properties;
    private final Clock clock;

    /** Children the user owns plus, for teachers, children linked to their classes. */
    @Transactional(readOnly = true)
    public PageResponse<ChildResponse> list(UUID userId, int page, int size) {
        return PageResponse.of(childRepository.findAccessible(userId, PageRequest.of(page, size)),
                child -> ChildResponse.from(child, userId));
    }

    @Transactional(readOnly = true)
    public ChildResponse get(UUID userId, UUID childId) {
        return ChildResponse.from(childAccessService.requireAccessibleChild(userId, childId), userId);
    }

    @Transactional
    public ChildResponse create(AuthenticatedUser user, CreateChildRequest request) {
        int max = user.role() == Role.TEACHER
                ? properties.children().maxPerTeacher()
                : properties.children().maxPerParent();
        if (childRepository.countByParentIdAndDeletedAtIsNull(user.id()) >= max) {
            throw ApiException.conflict("CHILD_LIMIT_REACHED", "An account can have at most " + max + " children");
        }
        validateBirthYear(request.birthYear());
        Child child = new Child(user.id(), request.displayName().trim(), request.birthYear(), request.avatarId(),
                gradeCatalog.require(request.gradeId()), clock.instant());
        return ChildResponse.from(childRepository.save(child), user.id());
    }

    @Transactional
    public ChildResponse update(UUID userId, UUID childId, UpdateChildRequest request) {
        Child child = childAccessService.requireOwnedChild(userId, childId);
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
        if (request.gradeId() != null) {
            child.setGradeId(gradeCatalog.require(request.gradeId()));
        }
        child.setUpdatedAt(clock.instant());
        return ChildResponse.from(childRepository.save(child), userId);
    }

    /** Soft delete; the profile and its learning data are purged permanently after the retention period. */
    @Transactional
    public void delete(UUID userId, UUID childId) {
        Child child = childAccessService.requireOwnedChild(userId, childId);
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
