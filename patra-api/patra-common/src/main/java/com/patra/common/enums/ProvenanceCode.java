package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 上游数据来源枚举（Provenance）。
 *
 * <p>为常见文献/元数据来源提供统一标识（如 PubMed、Crossref、DataCite），
 * 支持字符串解析与 Jackson JSON 序列化/反序列化。</p>
 *
 * @author linqibin
 * @since 0.1.0
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

    /** 数据源标识（如 "pubmed"）。 */
    private final String code;

    /** 数据源的展示名称或说明（如 "PubMed"）。 */
    private final String description;

    /** 构造函数。 */
    ProvenanceCode(String code, String display) {
        this.code = code;
        this.description = display;
    }

    /**
     * 解析字符串为枚举：会进行规范化（trim、lowercase、'-'→'_'）并处理常见别名。
     */
    public static ProvenanceCode parse(String s) {
        if (s == null)
            throw new IllegalArgumentException("source is null");
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
            default -> throw new IllegalArgumentException("Unknown source: " + s);
        };
    }

    /** JSON 反序列化工厂方法。 */
    @JsonCreator
    public static ProvenanceCode fromJson(String value) {
        return parse(value);
    }

    /** JSON 序列化输出 code。 */
    @JsonValue
    public String toJson() {
        return this.code;
    }
}
