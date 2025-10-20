package com.patra.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Literature data transfer object for cross-service communication.
 *
 * <p>This DTO represents the contract between patra-ingest and patra-catalog services. It is the
 * "Published Language" in DDD terms, owned by patra-catalog.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Immutable using Lombok @Builder
 *   <li>Versioned contract (future: add @Schema version annotations)
 *   <li>Validation constraints for data integrity
 *   <li>Backward compatibility: new fields added with defaults
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record LiteratureDTO(
    /** Literature title */
    @NotBlank String title,

    /** Abstract text */
    String abstractText,

    /** Author list */
    @Valid List<AuthorDTO> authors,

    /** Journal information */
    @Valid JournalDTO journal,

    /** Identifier map (pmid, doi, pmc, etc.) */
    Map<String, String> identifiers,

    /** Publication date */
    LocalDate publicationDate,

    /** Keywords or MeSH terms */
    List<String> keywords,

    /** Language code (ISO 639-1) */
    String language,

    /** Publication types */
    List<String> publicationTypes) {}
