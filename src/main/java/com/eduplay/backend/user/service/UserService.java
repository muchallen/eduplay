package com.eduplay.backend.user.service;

import com.eduplay.backend.auth.service.RefreshTokenService;
import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.classroom.dto.ClassroomResponse;
import com.eduplay.backend.classroom.repository.ClassroomRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.progress.dto.GameResultResponse;
import com.eduplay.backend.progress.dto.LessonProgressResponse;
import com.eduplay.backend.progress.repository.GameResultRepository;
import com.eduplay.backend.progress.repository.LessonProgressRepository;
import com.eduplay.backend.subscription.service.SubscriptionService;
import com.eduplay.backend.user.domain.ConsentRecord;
import com.eduplay.backend.user.domain.User;
import com.eduplay.backend.user.dto.AccountDeletionResponse;
import com.eduplay.backend.user.dto.ConsentResponse;
import com.eduplay.backend.user.dto.DataExportResponse;
import com.eduplay.backend.user.dto.UpdateProfileRequest;
import com.eduplay.backend.user.dto.UserResponse;
import com.eduplay.backend.user.repository.ConsentRecordRepository;
import com.eduplay.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final ChildRepository childRepository;
    private final ClassroomRepository classroomRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final GameResultRepository gameResultRepository;
    private final SubscriptionService subscriptionService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EduplayProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(requireActive(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireActive(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    /**
     * Soft-deletes the account and all child profiles it owns, deletes its classes (teachers) and
     * signs out every device. The data stays
     * recoverable by an operator for the retention period and is then hard-deleted by
     * {@link com.eduplay.backend.infrastructure.DataRetentionJob}.
     */
    @Transactional
    public AccountDeletionResponse deleteAccount(UUID userId, String password) {
        User user = requireActive(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Password is incorrect");
        }
        Instant now = clock.instant();
        user.setDeletedAt(now);
        user.setUpdatedAt(now);
        childRepository.softDeleteAllForParent(userId, now);
        classroomRepository.deleteAllForTeacher(userId);
        refreshTokenService.revokeAll(userId);
        log.info("Account {} scheduled for deletion", userId);
        return new AccountDeletionResponse(
                "Your account has been deleted. All data will be permanently erased after the retention period.",
                now, now.plus(properties.retention().deletedAccountRetention()));
    }

    @Transactional
    public ConsentResponse recordConsent(UUID userId, String policyVersion, String clientIp) {
        requireActive(userId);
        String current = properties.privacy().policyVersion();
        if (!current.equals(policyVersion)) {
            throw ApiException.badRequest("POLICY_VERSION_MISMATCH",
                    "Please accept the current privacy policy (version " + current + ")");
        }
        return ConsentResponse.from(consentRecordRepository.save(
                new ConsentRecord(userId, policyVersion, clock.instant(), clientIp)));
    }

    /** Machine-readable copy of everything stored about the account, its children and its classes. */
    @Transactional(readOnly = true)
    public DataExportResponse export(UUID userId) {
        User user = requireActive(userId);
        List<ConsentResponse> consents = consentRecordRepository.findByUserIdOrderByAcceptedAtDesc(userId).stream()
                .map(ConsentResponse::from)
                .toList();
        List<DataExportResponse.ChildExport> children =
                childRepository.findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                        .map(child -> exportChild(child, userId))
                        .toList();
        List<ClassroomResponse> classes = classroomRepository.findByTeacherIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(classroom -> ClassroomResponse.from(classroom,
                        classroomRepository.countActiveChildren(classroom.getId())))
                .toList();
        return new DataExportResponse(clock.instant(), UserResponse.from(user), consents,
                subscriptionService.findCurrent(userId), children, classes);
    }

    private DataExportResponse.ChildExport exportChild(Child child, UUID ownerId) {
        return new DataExportResponse.ChildExport(
                ChildResponse.from(child, ownerId),
                lessonProgressRepository.findByChildIdOrderByGradeIdAscSectionIdAscTopicIdAscLessonIdAsc(child.getId())
                        .stream()
                        .map(LessonProgressResponse::from)
                        .toList(),
                gameResultRepository.findByChildIdOrderByPlayedAtAsc(child.getId()).stream()
                        .map(GameResultResponse::from)
                        .toList());
    }

    private User requireActive(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.unauthorized("ACCOUNT_NOT_FOUND", "Account no longer exists"));
    }
}
