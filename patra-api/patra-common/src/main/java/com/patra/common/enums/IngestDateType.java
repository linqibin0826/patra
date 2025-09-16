package com.patra.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of date types (DateType) used to distinguish between different
 * kinds of date fields in literature databases such as PubMed.
 *
 * <p>This enum reflects the metadata fields defined in PubMed:
 * <ul>
 *   <li><b>PDAT (Publication Date)</b>: The official publication date of the article,
 *   often used as a filter in search queries (e.g., "publication date [PDAT]").</li>
 *   <li><b>EDAT (Entrez Date)</b>: The date when the article was entered into
 *   the PubMed database, useful for tracking ingestion time.</li>
 *   <li><b>MHDA (MeSH Date)</b>: The date when MeSH (Medical Subject Headings)
 *   indexing was assigned to the article, indicating completion of subject annotation.</li>
 * </ul>
 *
 * <p>References:
 * <ul>
 *   <li>NCBI PubMed Help, "Search Field Descriptions and Tags",
 *   <a href="https://www.ncbi.nlm.nih.gov/books/NBK3827/#pubmedhelp.Search_Field_Descriptions_and">link</a></li>
 *   <li>US National Library of Medicine, PubMed Data Element (Field) Descriptions.</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@RequiredArgsConstructor
public enum IngestDateType implements CodeEnum<String> {

    PDAT("PDAT", "Publication Date", "The official publication date of the article"),
    EDAT("EDAT", "Entrez Date", "The date when the article was entered into PubMed"),
    MHDA("MHDA", "MeSH Date", "The date when MeSH indexing was assigned to the article");

    /**
     * The code used in PubMed to identify this date type (e.g., "PDAT").
     */
    private final String code;

    /**
     * The short name of the date type (e.g., "Publication Date").
     */
    private final String name;

    /**
     * A detailed description of the meaning of this date type.
     */
    private final String description;

    /**
     * Factory method for creating a DateType from a code string.
     * This method is annotated with @JsonCreator to support JSON deserialization.
     *
     * @param code the string code (case-insensitive) representing the date type
     * @return the corresponding DateType enum
     * @throws IllegalArgumentException if the code does not match any DateType
     */
    @JsonCreator
    public static IngestDateType fromCode(String code) {
        for (IngestDateType type : IngestDateType.values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    /**
     * Returns the code of this DateType for JSON serialization.
     * This method is annotated with @JsonValue.
     *
     * @return the string code of this DateType
     */
    @JsonValue
    public String toCode() {
        return this.code;
    }

}
