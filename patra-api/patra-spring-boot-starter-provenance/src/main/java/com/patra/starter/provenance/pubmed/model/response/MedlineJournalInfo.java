package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Additional journal metadata supplied by Medline.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MedlineJournalInfo {

  @JacksonXmlProperty(localName = "MedlineTA")
  private String medlineTa;

  @JacksonXmlProperty(localName = "Country")
  private String country;

  @JacksonXmlProperty(localName = "NlmUniqueID")
  private String nlmUniqueId;

  @JacksonXmlProperty(localName = "ISSNLinking")
  private String issnLinking;

  public MedlineJournalInfo() {}

  /** Medline TA abbreviation. */
  public String medlineTa() {
    return medlineTa;
  }

  /** Country associated with the journal. */
  public String country() {
    return country;
  }

  /** NLM unique identifier for the journal. */
  public String nlmUniqueId() {
    return nlmUniqueId;
  }

  /** Linking ISSN value. */
  public String issnLinking() {
    return issnLinking;
  }
}
