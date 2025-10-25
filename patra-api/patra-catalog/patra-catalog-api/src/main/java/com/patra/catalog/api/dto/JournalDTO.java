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
    /** The journal title or abbreviated title */
    String title,

    /** The International Standard Serial Number (ISSN) */
    String issn,

    /** The type of ISSN (print, electronic, or linking) */
    String issnType,

    /** The name of the publisher */
    String publisher,

    /** The country where this journal is published */
    String country) {}
