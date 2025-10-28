package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Common ingest date types used by literature sources (for example, PubMed's PDAT/EDAT/MHDA
 * fields).
 *
 * <p>Examples:
 *
 * <ul>
 *   <li><b>PDAT</b> – Publication Date, the official publication date often used for filtering.
 *   <li><b>EDAT</b> – Entrez Date, when the record was ingested into the database.
 *   <li><b>MHDA</b> – MeSH Date, when MeSH subject terms were assigned.
 * </ul>
 *
 * <p>Reference: PubMed help documentation.
 */
@Getter
@RequiredArgsConstructor
public enum IngestDateType {
  PDAT("PDAT", "Publication Date", "The official publication date of the article"),
  EDAT("EDAT", "Entrez Date", "The date when the article was entered into PubMed"),
  MHDA("MHDA", "MeSH Date", "The date when MeSH indexing was assigned to the article");

  /** Source-specific code identifying the date type (for example, {@code PDAT}). */
  private final String code;

  /** Short display name, such as {@code Publication Date}. */
  private final String name;

  /** Human-readable description of the date type. */
  private final String description;

  /** Factory method used by Jackson to create an enum from a code. */
  @JsonCreator
  public static IngestDateType fromCode(String code) {
    for (IngestDateType type : IngestDateType.values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown code: " + code);
  }

  /** Serializes the enum back to its code for JSON output. */
  @JsonValue
  public String toCode() {
    return this.code;
  }
}
