package com.eduplay.backend.report.controller;

import com.eduplay.backend.report.dto.ChildReportResponse;
import com.eduplay.backend.report.service.ReportService;
import com.eduplay.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(operationId = "getChildReport", summary = "Parent dashboard report for a child", description = """
            Totals, per-topic performance, strong topics, topics needing improvement and recent activity.
            Topic ratings use configurable average-score thresholds and need a minimum number of games.""")
    @GetMapping("/api/v1/children/{childId}/report")
    public ChildReportResponse report(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable UUID childId,
                                      @Parameter(description = "Only include the last N days (omit for all time)")
                                      @RequestParam(required = false) @Min(1) @Max(3650) Integer days) {
        return reportService.report(user.id(), childId, days);
    }
}
