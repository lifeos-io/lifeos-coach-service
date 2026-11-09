package com.lifeos.coach.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests move by hand ("it is now Thursday 18:01"), shared by the whole application context. */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.parse("2026-11-09T07:00:00Z");

    public void set(Instant now) {
        this.now = now;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
