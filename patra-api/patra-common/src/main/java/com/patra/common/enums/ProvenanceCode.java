package com.patra.common.enums;

/**
 * 采集数据源枚举。
 *
 * <p>
 * 用于标识支持的上游数据源，并提供从字符串解析与列出全部 code 的能力。
 *
 * @author linqibin
 * @date 2025-08-21 00:00:00
 * @since 0.0.1
 */
public enum ProvenanceCode implements CodeEnum<String> {

    PUBMED("pubmed", "PubMed"),
    PMC("pmc", "PubMed Central"),
    EPMC("epmc", "Europe PMC"),
    OPENALEX("openalex", "OpenAlex"),
    CROSSREF("crossref", "Crossref"),
    UNPAYWALL("unpaywall", "Unpaywall"),
    DOAJ("doaj", "DOAJ"),
    SEMANTIC_SCHOLAR("semanticscholar", "Semantic Scholar"),
    CORE("core", "CORE"),
    DATACITE("datacite", "DataCite"),
    BIORXIV("biorxiv", "bioRxiv"),
    MEDRXIV("medrxiv", "medRxiv"),
    PLOS("plos", "PLOS"),
    SPRINGER_OA("springer_oa", "Springer OA"),
    THE_LENS("the_lens", "The Lens"),
    LITCOVID("litcovid", "LitCovid"),
    CORD19("cord19", "CORD-19"),
    GIM("gim", "WHO GIM");

    private final String code;

    private final String description;

    ProvenanceCode(String code, String display) {
        this.code = code;
        this.description = display;
    }

    /**
     * 显示名称（用于 UI 或日志）。
     */
    public String display() {
        return description;
    }

    /**
     * 从字符串解析数据源。
     *
     * <p>
     * 支持常见别名与大小写/连字符差异。
     *
     * @param s 待解析字符串
     * @return 对应的枚举项
     * @throws IllegalArgumentException 未匹配到任一数据源
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

    @Override
    public String getCode() {
        return code;
    }
}
