package com.eduplay.backend.classroom.service;

import com.eduplay.backend.child.domain.Child;
import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.child.repository.ChildRepository;
import com.eduplay.backend.child.service.ChildAccessService;
import com.eduplay.backend.classroom.domain.Classroom;
import com.eduplay.backend.classroom.dto.ClassroomResponse;
import com.eduplay.backend.classroom.dto.CreateClassroomRequest;
import com.eduplay.backend.classroom.dto.LinkedClassResponse;
import com.eduplay.backend.classroom.dto.UpdateClassroomRequest;
import com.eduplay.backend.classroom.repository.ClassroomRepository;
import com.eduplay.backend.common.exception.ApiException;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.grade.GradeCatalog;
import com.eduplay.backend.security.RateLimitService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher classes and the links between classes and children.
 *
 * <p>Who may link: a teacher may add children they own (pupils without a parent account) to their
 * own classes; a parent links their child by entering the class join code. Either side may remove
 * the link. A teacher never gains edit rights over a parent's child.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    /** No 0/O, 1/I/L: join codes are read aloud and typed by parents. */
    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClassroomRepository classroomRepository;
    private final ChildRepository childRepository;
    private final ChildAccessService childAccessService;
    private final GradeCatalog gradeCatalog;
    private final RateLimitService rateLimitService;
    private final EduplayProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ClassroomResponse> list(UUID teacherId) {
        return classroomRepository.findByTeacherIdOrderByCreatedAtAscIdAsc(teacherId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassroomResponse get(UUID teacherId, UUID classroomId) {
        return toResponse(requireOwnClassroom(teacherId, classroomId));
    }

    @Transactional
    public ClassroomResponse create(UUID teacherId, CreateClassroomRequest request) {
        Classroom classroom = new Classroom(teacherId, request.name().trim(),
                gradeCatalog.requireIfPresent(request.gradeId()), newJoinCode(), clock.instant());
        classroom = classroomRepository.save(classroom);
        log.info("Teacher {} created class {}", teacherId, classroom.getId());
        return ClassroomResponse.from(classroom, 0);
    }

    @Transactional
    public ClassroomResponse update(UUID teacherId, UUID classroomId, UpdateClassroomRequest request) {
        Classroom classroom = requireOwnClassroom(teacherId, classroomId);
        if (request.name() != null) {
            classroom.setName(request.name().trim());
        }
        if (request.gradeId() != null) {
            classroom.setGradeId(gradeCatalog.require(request.gradeId()));
        }
        classroom.setUpdatedAt(clock.instant());
        return toResponse(classroom);
    }

    /** Deletes the class and its links; the children themselves are not affected. */
    @Transactional
    public void delete(UUID teacherId, UUID classroomId) {
        classroomRepository.delete(requireOwnClassroom(teacherId, classroomId));
    }

    /** Issues a new join code; the old one stops working. Existing links are kept. */
    @Transactional
    public ClassroomResponse rotateJoinCode(UUID teacherId, UUID classroomId) {
        Classroom classroom = requireOwnClassroom(teacherId, classroomId);
        classroom.setJoinCode(newJoinCode());
        classroom.setUpdatedAt(clock.instant());
        return toResponse(classroom);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChildResponse> children(UUID teacherId, UUID classroomId, int page, int size) {
        requireOwnClassroom(teacherId, classroomId);
        return PageResponse.of(childRepository.findInClassroom(classroomId, PageRequest.of(page, size)),
                child -> ChildResponse.from(child, teacherId));
    }

    /** Teacher adds a child they own to their class. Idempotent. */
    @Transactional
    public void addOwnChild(UUID teacherId, UUID classroomId, UUID childId) {
        requireOwnClassroom(teacherId, classroomId);
        Child child = childAccessService.requireOwnedChild(teacherId, childId);
        classroomRepository.link(classroomId, child.getId(), clock.instant());
    }

    /** Teacher removes any child from their class. Idempotent. */
    @Transactional
    public void removeChild(UUID teacherId, UUID classroomId, UUID childId) {
        requireOwnClassroom(teacherId, classroomId);
        classroomRepository.unlink(classroomId, childId);
    }

    /** Owner links their child to a class by its join code. Idempotent. */
    @Transactional
    public LinkedClassResponse join(UUID ownerId, UUID childId, String joinCode) {
        rateLimitService.consume("class-join-account", ownerId.toString(),
                properties.rateLimit().classJoinPerAccount());
        Child child = childAccessService.requireOwnedChild(ownerId, childId);
        Classroom classroom = classroomRepository.findByJoinCode(normalize(joinCode))
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "No class has this join code"));
        classroomRepository.link(classroom.getId(), child.getId(), clock.instant());
        log.info("Child {} linked to class {}", child.getId(), classroom.getId());
        return classroomRepository.findLinkedClasses(child.getId()).stream()
                .filter(view -> view.getId().equals(classroom.getId()))
                .map(LinkedClassResponse::from)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "No class has this join code"));
    }

    @Transactional(readOnly = true)
    public List<LinkedClassResponse> linkedClasses(UUID ownerId, UUID childId) {
        childAccessService.requireOwnedChild(ownerId, childId);
        return classroomRepository.findLinkedClasses(childId).stream().map(LinkedClassResponse::from).toList();
    }

    /** Owner removes their child from a class. Idempotent. */
    @Transactional
    public void leave(UUID ownerId, UUID childId, UUID classroomId) {
        childAccessService.requireOwnedChild(ownerId, childId);
        classroomRepository.unlink(classroomId, childId);
    }

    private Classroom requireOwnClassroom(UUID teacherId, UUID classroomId) {
        return classroomRepository.findByIdAndTeacherId(classroomId, teacherId)
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "Class not found"));
    }

    private ClassroomResponse toResponse(Classroom classroom) {
        return ClassroomResponse.from(classroom, classroomRepository.countActiveChildren(classroom.getId()));
    }

    private String newJoinCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(JOIN_CODE_LENGTH);
            for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
                code.append(JOIN_CODE_ALPHABET.charAt(RANDOM.nextInt(JOIN_CODE_ALPHABET.length())));
            }
            if (!classroomRepository.existsByJoinCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }

    private static String normalize(String joinCode) {
        return joinCode.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
