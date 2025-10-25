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
    /** The primary title of the literature article */
    @NotBlank String title,

    /** The abstract text summarizing the literature content */
    String abstractText,

    /** List of authors who contributed to this literature */
    @Valid List<AuthorDTO> authors,

    /** Journal metadata where this literature was published */
    @Valid JournalDTO journal,

    /** Map of external identifiers (pmid, doi, pmc, etc.) */
    Map<String, String> identifiers,

    /** The date when this literature was published */
    LocalDate publicationDate,

    /** Keywords or MeSH terms associated with this literature */
    List<String> keywords,

    /** Language code (ISO 639-1) of the literature content */
    String language,

    /** Publication type classifications (e.g., Journal Article, Review) */
    List<String> publicationTypes) {}
