package com.eduplay.backend.user.dto;

import com.eduplay.backend.user.domain.ConsentRecord;
import java.time.Instant;

public record ConsentResponse(String policyVersion, Instant acceptedAt) {

    public static ConsentResponse from(ConsentRecord record) {
        return new ConsentResponse(record.getPolicyVersion(), record.getAcceptedAt());
    }
}
