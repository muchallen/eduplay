package com.eduplay.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Single UTC time source so that services are deterministic under test. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
