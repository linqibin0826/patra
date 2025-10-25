package com.patra.catalog.api.dto;

import java.util.List;
import lombok.Builder;

/**
 * Author data transfer object.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record AuthorDTO(
    /** The author's last name or family name */
    String lastName,

    /** The author's given name or first name */
    String foreName,

    /** The author's name initials */
    String initials,

    /** List of institutional affiliations for this author */
    List<String> affiliations,

    /** The author's unique identifier (e.g., ORCID) */
    String identifier,

    /** The source system of the author identifier */
    String identifierSource) {}
