package com.patra.ingest.adapter.in.job.mapper;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.in.job.model.JobParameterDto;
import com.patra.ingest.app.usecase.command.*;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Mapper for converting JobParameterDto to application layer commands.
 * Handles default value assignment and type conversion.
 */
@Slf4j
public final class JobParameterMapper {

    // Default values for optional parameters
    private static final Priority DEFAULT_PRIORITY = Priority.NORMAL;
    private static final CursorType DEFAULT_CURSOR_TYPE = CursorType.TIME;
    private static final com.patra.common.enums.SortDirection DEFAULT_SORT_DIRECTION =
            com.patra.common.enums.SortDirection.ASC;
    private static final Duration DEFAULT_TIME_WINDOW = Duration.ofDays(2);
    private static final long DEFAULT_ID_RANGE_SIZE = 10000L;
    // Additional adapter-level defaults can be introduced as needed

    private JobParameterMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Maps JobParameterDto to JobStartPlanCommand for application layer.
     *
     * @param dto            Job parameter DTO from JSON parsing
     * @param requestedBy    Who requested the job execution
     * @param provenanceCode Data source provenance code
     * @param operationType  Type of ingest operation
     * @return JobStartPlanCommand for application layer
     */
    public static JobStartPlanCommand toJobStartPlanCommand(
            JobParameterDto dto,
            String requestedBy,
            ProvenanceCode provenanceCode,
            IngestOperationType operationType) {

        log.debug("Mapping JobParameterDto to JobStartPlanCommand for operation: {}", operationType);

        // Map IngestScope
        Optional<IngestScope> scope = mapIngestScope(dto.ingestScope());

        // Map CursorSpec with defaults
        CursorSpec cursorSpec = mapCursorSpec(dto);

        // Map Priority with default
        Priority priority = mapPriority(dto.priority());

        // Map SafetyLimits with defaults (use conservative defaults for adapter)
        SafetyLimits safetyLimits = mapSafetyLimits();

        // Map IngestOptOverrides with defaults
        IngestOptOverrides overrides = mapOverrides(dto.overrides());
        // Build command
        return new JobStartPlanCommand(
                requestedBy,
                provenanceCode,
                operationType,
                scope,
                cursorSpec,
                dto.dryRun(),
                priority,
                safetyLimits,
                overrides
        );
    }

    private static Optional<IngestScope> mapIngestScope(JobParameterDto.IngestScopeDto scopeDto) {
        if (scopeDto == null) return Optional.empty();
        // Domain IngestScope expects journalIssns, affiliations, subjectAreas; map journals->journalIssns, authors->affiliations (approx)
        List<String> journals = scopeDto.journals();
        List<String> subjectAreas = scopeDto.subjectAreas();
        List<String> authors = scopeDto.authors();
        IngestScope scope = new IngestScope(
                Optional.ofNullable(journals),
                Optional.ofNullable(authors),
                Optional.ofNullable(subjectAreas)
        );
        return Optional.of(scope);
    }

    private static CursorSpec mapCursorSpec(JobParameterDto dto) {
        JobParameterDto.CursorSpecDto c = dto.cursorSpec();
        CursorType type = c != null && c.type() != null ? c.type() : DEFAULT_CURSOR_TYPE;
        com.patra.common.enums.SortDirection direction =
                c != null && c.direction() != null ? c.direction() : DEFAULT_SORT_DIRECTION;
        Optional<String> field = Optional.ofNullable(c == null ? null : c.field());
        Optional<String> lastSeenId = Optional.ofNullable(c == null ? null : c.lastSeenId());
        Optional<Instant> since = Optional.ofNullable(c == null ? null : c.since());
        Optional<Instant> until = Optional.ofNullable(c == null ? null : c.until());
        Optional<Duration> timeWindow = Optional.ofNullable(c == null ? null : c.timeWindow());
        Optional<Long> idWindow = Optional.ofNullable(c == null ? null : c.idWindow());
        Optional<Duration> effectiveTimeWindow = timeWindow.isPresent() ? timeWindow : Optional.of(DEFAULT_TIME_WINDOW);
        Optional<Long> effectiveIdWindow = idWindow.isPresent() ? idWindow : Optional.of(DEFAULT_ID_RANGE_SIZE);

        return new CursorSpec(
                type,
                field,
                direction,
                lastSeenId,
                since,
                until,
                effectiveTimeWindow,
                effectiveIdWindow
        );
    }

    private static Priority mapPriority(Priority p) {
        return p == null ? DEFAULT_PRIORITY : p;
    }

    private static SafetyLimits mapSafetyLimits() {
        // Keep empty to delegate to service defaults, but we can cap maxRecords per slice
        return SafetyLimits.empty();
    }

    private static IngestOptOverrides mapOverrides(JobParameterDto.OverridesDto overridesDto) {
        // Currently only supports time field override via CursorSpec.field; keep Overrides empty here.
        return IngestOptOverrides.empty();
    }

}
