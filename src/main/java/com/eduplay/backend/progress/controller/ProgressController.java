package com.eduplay.backend.progress.controller;

import com.eduplay.backend.common.response.PageResponse;
import com.eduplay.backend.progress.dto.GameResultResponse;
import com.eduplay.backend.progress.dto.ProgressResponse;
import com.eduplay.backend.progress.dto.SyncRequest;
import com.eduplay.backend.progress.dto.SyncResponse;
import com.eduplay.backend.progress.service.ProgressService;
import com.eduplay.backend.progress.service.SyncService;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Learning progress")
@RestController
@RequestMapping("/api/v1/children/{childId}")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;
    private final SyncService syncService;

    @Operation(operationId = "getProgress", summary = "Server-side lesson progress for a child")
    @GetMapping("/progress")
    public ProgressResponse progress(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId) {
        return progressService.getProgress(user.id(), childId);
    }

    @Operation(operationId = "syncProgress", summary = "Upload progress recorded offline", description = """
            Idempotent and safe to retry. Up to 500 game results and 500 lesson entries per request; send larger
            queues in several requests. Game results whose id is already stored are ignored. Lesson progress is
            merged: completed beats incomplete, otherwise the higher currentGameIndex wins. The response holds the
            full merged progress, which the client should store locally. Bodies may be sent with
            Content-Encoding: gzip.""")
    @PostMapping("/sync")
    public SyncResponse sync(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID childId,
                             @Valid @RequestBody SyncRequest request) {
        return syncService.sync(user.id(), childId, request);
    }

    @Operation(operationId = "listGameResults", summary = "Game results for a child, newest first")
    @GetMapping("/game-results")
    public PageResponse<GameResultResponse> gameResults(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable UUID childId,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return progressService.getGameResults(user.id(), childId, page, size);
    }
}
