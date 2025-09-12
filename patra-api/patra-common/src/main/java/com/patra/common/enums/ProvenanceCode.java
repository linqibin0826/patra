package com.patra.common.enums;

import lombok.Getter;

/**
 * 采集数据源枚举。
 *
 * <p>
 * 用于标识支持的上游数据源，并提供从字符串解析与列出全部 code 的能力。
 *
 * @author linqibin
 * @since 0.1.0
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

    @Getter
    private final String code;

    @Getter
    private final String description;

    ProvenanceCode(String code, String display) {
        this.code = code;
        this.description = display;
    }

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
}

