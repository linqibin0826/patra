package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 上游数据源(溯源/Provenance)枚举。
 *
 * <p>为常见的文献和元数据源(如 PubMed、Crossref 和 DataCite)提供规范标识符。 支持字符串解析以及 Jackson 序列化和反序列化。
 */
@Getter
public enum ProvenanceCode {
  PUBMED("PUBMED", "PubMed"),
  PMC("PMC", "PubMed Central"),
  EPMC("EPMC", "Europe PMC"),
  OPENALEX("OPENALEX", "OpenAlex"),
  CROSSREF("CROSSREF", "Crossref"),
  UNPAYWALL("UNPAYWALL", "Unpaywall"),
  DOAJ("DOAJ", "DOAJ"),
  SEMANTIC_SCHOLAR("SEMANTICSCHOLAR", "Semantic Scholar"),
  CORE("CORE", "CORE"),
  DATACITE("DATACITE", "DataCite"),
  BIORXIV("BIORXIV", "bioRxiv"),
  MEDRXIV("MEDRXIV", "medRxiv"),
  PLOS("PLOS", "PLOS"),
  SPRINGER_OA("SPRINGER_OA", "Springer OA"),
  THE_LENS("THE_LENS", "The Lens"),
  LITCOVID("LITCOVID", "LitCovid"),
  CORD19("CORD19", "CORD-19"),
  GIM("GIM", "WHO GIM");

  /** 用于持久化和交换的大写代码(例如 {@code PUBMED})。 */
  private final String code;

  /** 数据源的人类可读描述。 */
  private final String description;

  ProvenanceCode(String code, String display) {
    this.code = code;
    this.description = display;
  }

  /**
   * 将字符串解析为 {@link ProvenanceCode},同时规范化大小写和常见别名。
   *
   * @param s 数据源标识符
   * @return 匹配的数据源代码
   * @throws IllegalArgumentException 如果标识符为 null 或未知
   */
  public static ProvenanceCode parse(String s) {
    if (s == null) {
      throw new IllegalArgumentException("数据源标识符不能为 null");
    }
    String norm = s.trim().toLowerCase().replace('-', '_');
    return switch (norm) {
      case "europe_pmc", "europepmc", "epmc" -> EPMC;
      case "pubmed", "medline", "med" -> PUBMED;
      case "pmc", "pubmed_central" -> PMC;
      case "openalex" -> OPENALEX;
      case "crossref" -> CROSSREF;
      case "unpaywall", "upw" -> UNPAYWALL;
      case "doaj" -> DOAJ;
      case "semantic_scholar", "s2" -> SEMANTIC_SCHOLAR;
      case "core" -> CORE;
      case "datacite" -> DATACITE;
      case "biorxiv" -> BIORXIV;
      case "medrxiv" -> MEDRXIV;
      case "plos" -> PLOS;
      case "springer", "springer_oa" -> SPRINGER_OA;
      case "lens", "the_lens" -> THE_LENS;
      case "litcovid" -> LITCOVID;
      case "cord19", "cord-19" -> CORD19;
      case "gim", "who_gim" -> GIM;
      default -> throw new IllegalArgumentException("未知的数据源: " + s);
    };
  }

  /**
   * 用于 JSON 反序列化的 Jackson 工厂方法。
   *
   * @param value JSON 中的值
   * @return 匹配的数据源代码
   */
  @JsonCreator
  public static ProvenanceCode fromJson(String value) {
    return parse(value);
  }

  /**
   * 将代码序列化为 JSON。
   *
   * @return 数据源代码字符串
   */
  @JsonValue
  public String toJson() {
    return this.code;
  }
}
