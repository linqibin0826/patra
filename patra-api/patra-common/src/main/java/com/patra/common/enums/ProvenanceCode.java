package com.patra.common.enums;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of provenance codes representing supported upstream data sources.
 *
 * <p>This enum provides identifiers for various literature and metadata databases
 * (e.g., PubMed, Crossref, DataCite), and supports parsing from strings as well
 * as JSON serialization/deserialization through Jackson annotations.
 *
 * <p>Examples of sources:
 * <ul>
 *   <li><b>PUBMED</b>: PubMed, a free database of biomedical literature maintained by NCBI.</li>
 *   <li><b>PMC</b>: PubMed Central, a free full-text archive of biomedical and life sciences journal literature.</li>
 *   <li><b>EPMC</b>: Europe PMC, a free repository of life sciences articles, preprints, and other resources.</li>
 *   <li><b>CROSSREF</b>: A database providing Digital Object Identifiers (DOIs) and metadata for scholarly works.</li>
 *   <li><b>DATACITE</b>: A global nonprofit organization that provides persistent identifiers (DOIs) for research data.</li>
 * </ul>
 *
 * <p>References:
 * <ul>
 *   <li>NCBI PubMed: <a href="https://pubmed.ncbi.nlm.nih.gov/">https://pubmed.ncbi.nlm.nih.gov/</a></li>
 *   <li>Europe PMC: <a href="https://europepmc.org/">https://europepmc.org/</a></li>
 *   <li>Crossref: <a href="https://www.crossref.org/">https://www.crossref.org/</a></li>
 *   <li>DataCite: <a href="https://datacite.org/">https://datacite.org/</a></li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
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

    /**
     * The unique string code identifier for the data source (e.g., "pubmed").
     */
    private final String code;

    /**
     * A human-readable description or display name of the data source (e.g., "PubMed").
     */
    private final String description;

    /**
     * Constructs a provenance code enum constant.
     *
     * @param code the unique identifier string
     * @param display the human-readable description
     */
    ProvenanceCode(String code, String display) {
        this.code = code;
        this.description = display;
    }

    /**
     * Parses a string into a ProvenanceCode.
     * This method normalizes the input (trims, lowercases, replaces '-' with '_')
     * and maps common aliases to the appropriate code.
     *
     * @param s the input string
     * @return the corresponding ProvenanceCode
     * @throws IllegalArgumentException if the string does not match any known source
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

    /**
     * Factory method for JSON deserialization.
     *
     * @param value the string value from JSON
     * @return the corresponding ProvenanceCode
     */
    @JsonCreator
    public static ProvenanceCode fromJson(String value) {
        return parse(value);
    }

    /**
     * Returns the string code for JSON serialization.
     *
     * @return the code of this ProvenanceCode
     */
    @JsonValue
    public String toJson() {
        return this.code;
    }
}
