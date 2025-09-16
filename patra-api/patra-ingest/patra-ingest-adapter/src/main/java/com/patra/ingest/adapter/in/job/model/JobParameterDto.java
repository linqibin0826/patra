package com.patra.ingest.adapter.in.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patra.common.enums.Priority;
import com.patra.common.enums.SortDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import lombok.Builder;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

/**
 * Job parameter DTO for XXL-Job JSON parameter parsing.
 * Supports all operation types with comprehensive parameter structure.
 * This DTO represents the "shape" of the JSON input from XXL-Job scheduler.
 * All fields are optional - defaults will be applied when not provided.
 */
@Builder
public record JobParameterDto(
        @JsonProperty("dryRun")
        boolean dryRun,


        @JsonProperty("ingestScope")
        IngestScopeDto ingestScope,

        @JsonProperty("priority")
        Priority priority,

        @JsonProperty("overrides")
        OverridesDto overrides,

        @JsonProperty("cursorSpec")
        CursorSpecDto cursorSpec
) {

    /**
     * IngestScope DTO for filtering data collection by journals, subject areas, and authors.
     */
    @Builder
    public record IngestScopeDto(
            @JsonProperty("journals")
            List<String> journals,

            @JsonProperty("subjectAreas")
            List<String> subjectAreas,

            @JsonProperty("authors")
            List<String> authors
    ) {}



    /**
     * Override parameters DTO for customizing job execution behavior.
     */
    @Builder
    public record OverridesDto(
            @JsonProperty("retryCount")
            Integer retryCount,

            @JsonProperty("timeoutSeconds")
            Integer timeoutSeconds,

            @JsonProperty("overlapDays")
            Integer overlapDays,

            @JsonProperty("batchSize")
            Integer batchSize
    ) {}

    @Builder
    public record CursorSpecDto(
            @JsonProperty("type")
            CursorType type,

            @JsonProperty("field")
            String field,

            @JsonProperty("direction")
            SortDirection direction,

            @JsonProperty("lastSeenId")
            String lastSeenId,

            @JsonProperty("since")
            Instant since,

            @JsonProperty("until")
            Instant until,

            @JsonProperty("timeWindow")
            Duration timeWindow,

            @JsonProperty("idWindow")
            Long idWindow
    ) {}
}
