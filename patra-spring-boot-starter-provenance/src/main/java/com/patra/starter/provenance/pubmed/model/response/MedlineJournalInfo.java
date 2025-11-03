package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Medline 提供的额外期刊元数据。
 *
 * <p>包含期刊的标准化缩写、出版国家、NLM唯一标识符和链接ISSN等信息。
 *
 * @author Patra
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MedlineJournalInfo {

  /** Medline标准化期刊缩写名称 */
  @JacksonXmlProperty(localName = "MedlineTA")
  private String medlineTa;

  /** 期刊出版国家/地区 */
  @JacksonXmlProperty(localName = "Country")
  private String country;

  /** NLM(美国国家医学图书馆)唯一标识符 */
  @JacksonXmlProperty(localName = "NlmUniqueID")
  private String nlmUniqueId;

  /** 链接ISSN(国际标准期刊号) */
  @JacksonXmlProperty(localName = "ISSNLinking")
  private String issnLinking;

  public MedlineJournalInfo() {}

  /** 返回Medline标准化期刊缩写名称 */
  public String medlineTa() {
    return medlineTa;
  }

  /** 返回期刊出版国家/地区 */
  public String country() {
    return country;
  }

  /** 返回NLM唯一标识符 */
  public String nlmUniqueId() {
    return nlmUniqueId;
  }

  /** 返回链接ISSN值 */
  public String issnLinking() {
    return issnLinking;
  }
}
