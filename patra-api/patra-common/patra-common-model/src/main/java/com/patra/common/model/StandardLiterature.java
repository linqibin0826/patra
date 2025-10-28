package com.patra.common.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Canonical literature representation shared across Papertrace microservices.
 *
 * <p>This immutable structure acts as the Shared Kernel model between ingestion, catalog, and
 * provenance adapters. It deliberately contains no business behavior to keep the shared module
 * framework-free and portable.
 *
 * @since 0.2.0
 */
@Value
@Builder
@Jacksonized
public class StandardLiterature {

  /** Human readable title of the literature item. */
  String title;

  /** Abstract or summary text. */
  String abstractText;

  /** Authors in presentation order. */
  List<StandardAuthor> authors;

  /** Journal metadata when available. */
  StandardJournal journal;

  /** Identifier map such as PMID, DOI, PMC. */
  Map<String, String> identifiers;

  /** Publication date at day resolution; null when not provided. */
  LocalDate publicationDate;

  /** Domain level keywords. */
  List<String> keywords;

  /** Author snapshot aligned with catalog contract needs. */
  @Value
  @Builder
  @Jacksonized
  public static class StandardAuthor {

    String lastName;
    String foreName;
    String affiliation;
  }

  /** Journal snapshot aligned with catalog contract needs. */
  @Value
  @Builder
  @Jacksonized
  public static class StandardJournal {

    String title;
    String issn;
    String publisher;
  }
}
