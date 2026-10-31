package com.eduplay.backend.report.service;

import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.report.dto.TopicPerformance.Rating;

/** Exposes package-private rating logic to tests in other packages. */
public final class ReportServiceTestAccess {

    private ReportServiceTestAccess() {
    }

    public static Rating rate(long games, Double average, EduplayProperties.Report config) {
        return ReportService.rate(games, average, config);
    }
}
