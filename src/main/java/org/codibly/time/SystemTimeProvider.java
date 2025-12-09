package org.codibly.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class SystemTimeProvider implements TimeProvider {

    private final Clock clock;

    public SystemTimeProvider() {
        this.clock = Clock.systemUTC();
    }

    public SystemTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public LocalDate getLocalDate() {
        return LocalDate.now(clock);
    }

    @Override
    public ZonedDateTime getStartOfDay() {
        return getLocalDate().atStartOfDay().atZone(ZoneOffset.UTC);
    }

    @Override
    public ZonedDateTime getEndOfDay() {
        return getStartOfDay();
    }
}
