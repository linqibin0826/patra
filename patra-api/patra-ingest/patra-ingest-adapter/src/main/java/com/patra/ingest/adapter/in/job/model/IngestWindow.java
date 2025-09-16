package com.patra.ingest.adapter.in.job.model;

import java.time.Instant;
import java.time.ZoneId;

public record IngestWindow(Instant fromInclusive, Instant toExclusive, ZoneId zone) {
}
