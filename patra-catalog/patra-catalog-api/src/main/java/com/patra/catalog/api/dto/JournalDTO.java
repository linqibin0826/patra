package com.patra.catalog.api.dto;

import lombok.Builder;

/**
 * Journal data transfer object.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record JournalDTO(
    /** Journal title or abbreviated title */
    String title,

    /** International Standard Serial Number */
    String issn,

    /** Type of ISSN (print/electronic/linking) */
    String issnType,

    /** Publisher name */
    String publisher,

    /** Country of publication */
    String country) {}
