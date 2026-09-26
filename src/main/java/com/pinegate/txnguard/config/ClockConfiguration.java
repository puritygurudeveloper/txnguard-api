package com.pinegate.txnguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock is a bean so that time is an injected dependency rather than a static call.
 *
 * <p>Tests substitute {@link Clock#fixed}, which is what makes assertions about
 * timestamps and velocity windows deterministic instead of flaky.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
