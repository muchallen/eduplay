package com.eduplay.backend.classroom.controller;

import com.eduplay.backend.classroom.dto.JoinClassRequest;
import com.eduplay.backend.classroom.dto.LinkedClassResponse;
import com.eduplay.backend.classroom.service.ClassroomService;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The child owner's side of class membership. */
@Tag(name = "Classes")
@RestController
@RequestMapping("/api/v1/children/{childId}/classes")
@RequiredArgsConstructor
public class ChildClassController {

    private final ClassroomService classroomService;

    @Operation(operationId = "listChildClasses", summary = "Classes a child is linked to (owner only)")
    @GetMapping
    public List<LinkedClassResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID childId) {
        return classroomService.linkedClasses(user.id(), childId);
    }

    @Operation(operationId = "joinClass", summary = "Link a child to a class with its join code (owner only)",
            description = "Gives the class teacher read, report and sync access to the child. Idempotent. "
                    + "Limited to 10 attempts per hour per account.")
    @PostMapping
    public LinkedClassResponse join(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId,
                                    @Valid @RequestBody JoinClassRequest request) {
        return classroomService.join(user.id(), childId, request.joinCode());
    }

    @Operation(operationId = "leaveClass", summary = "Remove a child from a class (owner only)",
            description = "Idempotent. The teacher loses access to the child.")
    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId,
                      @PathVariable UUID classId) {
        classroomService.leave(user.id(), childId, classId);
    }
}
