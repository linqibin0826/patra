package com.patra.ingest.adapter.in.job.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.in.job.model.JobParameterDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for XXL-Job JSON parameters with comprehensive error handling.
 * Converts raw JSON strings into structured JobParameterDto objects.
 */
@Slf4j
public final class JobParameterParser {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    
    private JobParameterParser() {
        // Utility class - prevent instantiation
    }

    public static JobParameterDto parse(String rawJobParameter) {
        try {
            if (rawJobParameter == null || rawJobParameter.isBlank()) {
                log.debug("Empty job parameter received, using defaults");
                return JobParameterDto.builder().build();
            }
            JobParameterDto dto = OBJECT_MAPPER.readValue(rawJobParameter, JobParameterDto.class);
            return dto == null ? JobParameterDto.builder().build() : dto;
        } catch (Exception e) {
            String msg = "Failed to parse XXL-Job parameter: " + e.getMessage();
            log.error(msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }
    

}
