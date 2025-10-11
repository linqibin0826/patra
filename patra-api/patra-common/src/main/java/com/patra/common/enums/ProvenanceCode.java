package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Enumeration of upstream data sources (provenances).
 *
 * <p>Provides canonical identifiers for common literature and metadata sources
 * such as PubMed, Crossref, and DataCite. Supports string parsing as well as
 * Jackson serialization and deserialization.</p>
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

    /** Uppercase code used for persistence and interchange (for example, {@code PUBMED}). */
    private final String code;

    /** Human-readable description of the provenance. */
    private final String description;

    ProvenanceCode(String code, String display) {
        this.code = code;
        this.description = display;
    }

    /**
     * Parses a string into a {@link ProvenanceCode} while normalizing case and common aliases.
     *
     * @param s source identifier
     * @return matching provenance code
     * @throws IllegalArgumentException if the identifier is null or unknown
     */
    public static ProvenanceCode parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("source is null");
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
            default -> throw new IllegalArgumentException("Unknown source: " + s);
        };
    }

    /** Jackson factory method used for JSON deserialization. */
    @JsonCreator
    public static ProvenanceCode fromJson(String value) {
        return parse(value);
    }

    /** Serializes the code to JSON. */
    @JsonValue
    public String toJson() {
        return this.code;
    }
}
