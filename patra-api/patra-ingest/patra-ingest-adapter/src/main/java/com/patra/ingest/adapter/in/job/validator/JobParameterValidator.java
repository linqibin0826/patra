package com.patra.ingest.adapter.in.job.validator;

import com.patra.ingest.adapter.in.job.model.JobParameterDto;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for job parameters with operation-specific validation rules.
 * Performs comprehensive validation at the adapter layer before delegating to application layer.
 */
@Slf4j
public final class JobParameterValidator {
    
    private JobParameterValidator() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates job parameters for general correctness.
     * Operation-specific validation is handled by individual job handlers.
     * 
     * @param dto Job parameter DTO to validate
     * @throws IllegalArgumentException if validation rules fail
     */
    public static void validate(JobParameterDto dto) {
        log.debug("Validating job parameters");
        
        List<String> errors = new ArrayList<>();

        // validate dto's fields
        if (dto == null) {
            errors.add("JobParameterDto cannot be null");
        } else {
            // cursorSpec
            JobParameterDto.CursorSpecDto c = dto.cursorSpec();
            if (c != null) {
                // if since and until provided ensure since <= until
                if (c.since() != null && c.until() != null) {
                    if (c.since().isAfter(c.until())) {
                        errors.add("cursorSpec.since must be before or equal to until");
                    }
                }
                // if timeWindow provided ensure positive
                if (c.timeWindow() != null) {
                    if (c.timeWindow().isZero() || c.timeWindow().isNegative()) {
                        errors.add("cursorSpec.timeWindow must be positive");
                    }
                }
                // if idWindow provided ensure positive
                if (c.idWindow() != null && c.idWindow() <= 0) {
                    errors.add("cursorSpec.idWindow must be positive");
                }
            }

            // overrides basic sanity if present
            JobParameterDto.OverridesDto o = dto.overrides();
            if (o != null) {
                if (o.retryCount() != null && o.retryCount() < 0) errors.add("overrides.retryCount must be >= 0");
                if (o.timeoutSeconds() != null && o.timeoutSeconds() <= 0) errors.add("overrides.timeoutSeconds must be > 0");
                if (o.overlapDays() != null && o.overlapDays() < 0) errors.add("overrides.overlapDays must be >= 0");
                if (o.batchSize() != null && o.batchSize() <= 0) errors.add("overrides.batchSize must be > 0");
            }
        }

    // Priority validation is handled by Jackson deserialization
        
        // Throw exception if validation fails
        if (!errors.isEmpty()) {
            String errorMessage = "Parameter validation failed: " + String.join("; ", errors);
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        log.debug("Job parameter validation completed successfully");
    }

}
