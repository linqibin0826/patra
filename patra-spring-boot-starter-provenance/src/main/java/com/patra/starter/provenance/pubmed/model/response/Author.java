package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 PubMed 引文中提取的作者信息。
 *
 * <p>包含作者的姓名、缩写、机构隶属关系和可选的标识符 (如 ORCID)。
 *
 * <p><b>主要字段:</b>
 *
 * <ul>
 *   <li><b>lastName</b>: 姓氏
 *   <li><b>foreName</b>: 名字
 *   <li><b>initials</b>: 姓名缩写
 *   <li><b>affiliationInfo</b>: 机构隶属关系列表
 *   <li><b>identifier</b>: 作者标识符 (如 ORCID)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
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
