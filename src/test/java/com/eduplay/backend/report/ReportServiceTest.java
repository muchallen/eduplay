package com.eduplay.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduplay.backend.config.EduplayProperties;
import com.eduplay.backend.report.dto.TopicPerformance.Rating;
import com.eduplay.backend.report.service.ReportServiceTestAccess;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private final EduplayProperties.Report config = new EduplayProperties.Report(80, 50, 3, 10);

    @Test
    void ratesTopicsUsingThresholds() {
        assertThat(ReportServiceTestAccess.rate(3, 80.0, config)).isEqualTo(Rating.STRONG);
        assertThat(ReportServiceTestAccess.rate(3, 60.0, config)).isEqualTo(Rating.DEVELOPING);
        assertThat(ReportServiceTestAccess.rate(3, 49.9, config)).isEqualTo(Rating.NEEDS_IMPROVEMENT);
        assertThat(ReportServiceTestAccess.rate(2, 100.0, config)).isEqualTo(Rating.NOT_ENOUGH_DATA);
        assertThat(ReportServiceTestAccess.rate(5, null, config)).isEqualTo(Rating.NOT_ENOUGH_DATA);
    }
}
