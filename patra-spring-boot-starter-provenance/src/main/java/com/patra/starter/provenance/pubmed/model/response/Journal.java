package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

/**
 * Journal metadata derived from PubMed citation.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class Journal {

  private final String issn;
  private final String issnType;
  private final String title;
  private final String isoAbbreviation;

  private Journal(String issn, String issnType, String title, String isoAbbreviation) {
    this.issn = issn;
    this.issnType = issnType;
    this.title = title;
    this.isoAbbreviation = isoAbbreviation;
  }

  /**
   * Parse a journal node into a curated journal representation.
   *
   * @param node journal node from the Medline citation
   * @return structured journal metadata
   */
  public static Journal from(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return new Journal(null, null, null, null);
    }
    JsonNode issnNode = node.path("ISSN");
    String issn = JsonHelpers.textValue(issnNode);
    String issnType = JsonHelpers.textValue(issnNode.path("@IssnType"));
    String title = JsonHelpers.textValue(node.path("Title"));
    String isoAbbreviation = JsonHelpers.textValue(node.path("ISOAbbreviation"));
    return new Journal(issn, issnType, title, isoAbbreviation);
  }

  /**
   * Get the journal ISSN.
   *
   * @return ISSN value or {@code null}
   */
  public String issn() {
    return issn;
  }

  /**
   * Get the ISSN type attribute.
   *
   * @return ISSN type or {@code null}
   */
  public String issnType() {
    return issnType;
  }

  /**
   * Get the full journal title.
   *
   * @return journal title or {@code null}
   */
  public String title() {
    return title;
  }

  /**
   * Get the ISO abbreviation for the journal.
   *
   * @return ISO abbreviation or {@code null}
   */
  public String isoAbbreviation() {
    return isoAbbreviation;
  }
}
