package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Author extracted from PubMed citation.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class Author {

    private final String lastName;
    private final String foreName;
    private final String initials;
    private final List<String> affiliations;
    private final String identifier;
    private final String identifierSource;

    private Author(
        String lastName,
        String foreName,
        String initials,
        List<String> affiliations,
        String identifier,
        String identifierSource
    ) {
        this.lastName = lastName;
        this.foreName = foreName;
        this.initials = initials;
        this.affiliations = affiliations;
        this.identifier = identifier;
        this.identifierSource = identifierSource;
    }

    /**
     * Parse an author node into a curated author representation.
     *
     * @param node author node
     * @return structured author
     */
    public static Author from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new Author(null, null, null, Collections.emptyList(), null, null);
        }
        String lastName = JsonHelpers.textValue(node.path("LastName"));
        String foreName = JsonHelpers.textValue(node.path("ForeName"));
        String initials = JsonHelpers.textValue(node.path("Initials"));
        List<String> affiliations = parseAffiliations(node.path("AffiliationInfo"));
        Identifier identifier = parseIdentifier(node.path("Identifier"));
        return new Author(lastName, foreName, initials, affiliations, identifier.value, identifier.source);
    }

    private static List<String> parseAffiliations(JsonNode affiliationNode) {
        if (affiliationNode == null || affiliationNode.isMissingNode() || affiliationNode.isNull()) {
            return Collections.emptyList();
        }
        List<String> affiliations = new ArrayList<>();
        for (JsonNode node : JsonHelpers.toNodeList(affiliationNode)) {
            String affiliation = JsonHelpers.textValue(node.path("Affiliation"));
            if (affiliation == null) {
                affiliation = JsonHelpers.textValue(node);
            }
            if (affiliation != null && !affiliation.isBlank()) {
                affiliations.add(affiliation);
            }
        }
        return Collections.unmodifiableList(affiliations);
    }

    private static Identifier parseIdentifier(JsonNode identifierNode) {
        if (identifierNode == null || identifierNode.isMissingNode() || identifierNode.isNull()) {
            return Identifier.EMPTY;
        }
        JsonNode selected = identifierNode.isArray() ? identifierNode.get(0) : identifierNode;
        String value = JsonHelpers.textValue(selected);
        String source = JsonHelpers.textValue(selected.path("@Source"));
        return new Identifier(value, source);
    }

    /**
     * Get the author's last name.
     *
     * @return last name
     */
    /**
     * Get the author's last name.
     *
     * @return last name
     */
    public String lastName() {
        return lastName;
    }

    /**
     * Get the author's given name.
     *
     * @return given name
     */
    /**
     * Get the author's given name.
     *
     * @return given name
     */
    public String foreName() {
        return foreName;
    }

    /**
     * Get the author's initials.
     *
     * @return initials
     */
    /**
     * Get the author's initials.
     *
     * @return initials
     */
    public String initials() {
        return initials;
    }

    /**
     * Get the author's affiliations.
     *
     * @return immutable list of affiliations
     */
    /**
     * Get the author's affiliations.
     *
     * @return immutable list of affiliations
     */
    public List<String> affiliations() {
        return affiliations;
    }

    /**
     * Get the author identifier value.
     *
     * @return identifier value or {@code null}
     */
    /**
     * Get the author identifier value.
     *
     * @return identifier value or {@code null}
     */
    public String identifier() {
        return identifier;
    }

    /**
     * Get the source system of the identifier.
     *
     * @return identifier source or {@code null}
     */
    /**
     * Get the source system of the identifier.
     *
     * @return identifier source or {@code null}
     */
    public String identifierSource() {
        return identifierSource;
    }

    private record Identifier(String value, String source) {
        private static final Identifier EMPTY = new Identifier(null, null);
    }
}
