package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * Author extracted from the PubMed citation.
 *
 * @author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Author {

  @JacksonXmlProperty(localName = "LastName")
  private String lastName;

  @JacksonXmlProperty(localName = "ForeName")
  private String foreName;

  @JacksonXmlProperty(localName = "Initials")
  private String initials;

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(localName = "AffiliationInfo")
  private List<AffiliationInfo> affiliationInfo;

  @JacksonXmlProperty(localName = "Identifier")
  private Identifier identifier;

  public Author() {}

  public String lastName() {
    return lastName;
  }

  public String foreName() {
    return foreName;
  }

  public String initials() {
    return initials;
  }

  public List<String> affiliations() {
    if (affiliationInfo == null || affiliationInfo.isEmpty()) {
      return List.of();
    }
    List<String> affiliations = new ArrayList<>(affiliationInfo.size());
    for (AffiliationInfo info : affiliationInfo) {
      String value = info.value();
      if (value != null && !value.isBlank()) {
        affiliations.add(value);
      }
    }
    return List.copyOf(affiliations);
  }

  public String identifier() {
    return identifier != null ? identifier.value : null;
  }

  public String identifierSource() {
    return identifier != null ? identifier.source : null;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class AffiliationInfo {

    @JacksonXmlProperty(localName = "Affiliation")
    private String affiliation;

    @JacksonXmlText private String value;

    private AffiliationInfo() {}

    private String value() {
      if (affiliation != null && !affiliation.isBlank()) {
        return affiliation;
      }
      return value;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Identifier {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "Source")
    private String source;

    private Identifier() {}
  }
}
