package com.eduplay.backend.classroom.controller;

import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.classroom.dto.ClassroomResponse;
import com.eduplay.backend.classroom.dto.CreateClassroomRequest;
import com.eduplay.backend.classroom.dto.UpdateClassroomRequest;
import com.eduplay.backend.classroom.service.ClassroomService;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Teacher-only (role TEACHER; other roles receive 403 FORBIDDEN). */
@Tag(name = "Classes")
@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @Operation(operationId = "listClasses", summary = "List the signed-in teacher's classes")
    @GetMapping
    public List<ClassroomResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return classroomService.list(user.id());
    }

    @Operation(operationId = "createClass", summary = "Create a class",
            description = "The response includes the join code parents use to link their children.")
    @PostMapping
    public ResponseEntity<ClassroomResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody CreateClassroomRequest request) {
        ClassroomResponse classroom = classroomService.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/classes/" + classroom.id())).body(classroom);
    }

    @Operation(operationId = "getClass", summary = "Get one class")
    @GetMapping("/{classId}")
    public ClassroomResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID classId) {
        return classroomService.get(user.id(), classId);
    }

    @Operation(operationId = "updateClass", summary = "Update a class (partial)")
    @PatchMapping("/{classId}")
    public ClassroomResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID classId,
                                    @Valid @RequestBody UpdateClassroomRequest request) {
        return classroomService.update(user.id(), classId, request);
    }

    @Operation(operationId = "deleteClass", summary = "Delete a class",
            description = "Removes the class and its links. The children and their data are not affected.")
    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID classId) {
        classroomService.delete(user.id(), classId);
    }

    @Operation(operationId = "rotateClassJoinCode", summary = "Replace the class join code",
            description = "The old code stops working; children already linked stay linked.")
    @PostMapping("/{classId}/join-code")
    public ClassroomResponse rotateJoinCode(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID classId) {
        return classroomService.rotateJoinCode(user.id(), classId);
    }

    @Operation(operationId = "listClassChildren", summary = "Children linked to a class")
    @GetMapping("/{classId}/children")
    public PageResponse<ChildResponse> children(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable UUID classId,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return classroomService.children(user.id(), classId, page, size);
    }

    @Operation(operationId = "addClassChild", summary = "Add a child the teacher created to the class",
            description = "Idempotent. Parents' children join with the class join code instead "
                    + "(POST /api/v1/children/{childId}/classes).")
    @PutMapping("/{classId}/children/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addChild(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID classId,
                         @PathVariable UUID childId) {
        classroomService.addOwnChild(user.id(), classId, childId);
    }

    @Operation(operationId = "removeClassChild", summary = "Remove a child from the class", description = "Idempotent.")
    @DeleteMapping("/{classId}/children/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeChild(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID classId,
                            @PathVariable UUID childId) {
        classroomService.removeChild(user.id(), classId, childId);
    }
}
