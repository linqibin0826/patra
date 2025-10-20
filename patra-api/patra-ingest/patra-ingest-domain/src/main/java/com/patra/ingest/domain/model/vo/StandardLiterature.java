package com.patra.ingest.domain.model.vo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Domain-level standardized literature representation used within patra-ingest.
 *
 * <p>This value object normalizes literature records originating from heterogeneous sources
 * (PubMed, EPMC, Crossref, etc.) before they leave the ingest bounded context. The structure is
 * intentionally framework-free to keep the domain layer pure Java.
 */
@Value
@Builder
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
  public static class StandardAuthor {
    String lastName;
    String foreName;
    String affiliation;
  }

  /** Journal snapshot aligned with catalog contract needs. */
  @Value
  @Builder
  public static class StandardJournal {
    String title;
    String issn;
    String publisher;
  }
}
