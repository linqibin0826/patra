package com.patra.ingest.adapter.in.job;

import java.time.Instant;
import java.time.ZoneId;

public record Window(Instant fromExclusive, Instant toInclusive, ZoneId zone) {
    }
