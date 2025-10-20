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
    /** Author last name */
    String lastName,

    /** Author given name */
    String foreName,

    /** Author initials */
    String initials,

    /** Author affiliations (institutional) */
    List<String> affiliations,

    /** Author identifier (e.g., ORCID) */
    String identifier,

    /** Source of the identifier */
    String identifierSource) {}
