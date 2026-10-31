package com.eduplay.backend.child.controller;

import com.eduplay.backend.child.dto.ChildResponse;
import com.eduplay.backend.child.dto.CreateChildRequest;
import com.eduplay.backend.child.dto.UpdateChildRequest;
import com.eduplay.backend.child.service.ChildService;
import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Children")
@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    @Operation(operationId = "listChildren", summary = "List child profiles",
            description = "Children the caller owns and, for teachers, children linked to their classes.")
    @GetMapping
    public PageResponse<ChildResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return childService.list(user.id(), page, size);
    }

    @Operation(operationId = "getChild", summary = "Get one child profile")
    @GetMapping("/{childId}")
    public ChildResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId) {
        return childService.get(user.id(), childId);
    }

    @Operation(operationId = "createChild", summary = "Create a child profile")
    @PostMapping
    public ResponseEntity<ChildResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody CreateChildRequest request) {
        ChildResponse child = childService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/children/" + child.id())).body(child);
    }

    @Operation(operationId = "updateChild", summary = "Update a child profile (partial)",
            description = "Owner only; a linked teacher receives 403 CHILD_NOT_OWNED.")
    @PatchMapping("/{childId}")
    public ChildResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId,
                                @Valid @RequestBody UpdateChildRequest request) {
        return childService.update(user.id(), childId, request);
    }

    @Operation(operationId = "deleteChild", summary = "Delete a child profile",
            description = "Owner only. The profile disappears immediately; its data is permanently erased after 30 days.")
    @DeleteMapping("/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId) {
        childService.delete(user.id(), childId);
    }
}
