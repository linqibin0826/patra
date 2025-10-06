package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Author extracted from PubMed citation.
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
        return affiliations;
    }

    public String identifier() {
        return identifier;
    }

    public String identifierSource() {
        return identifierSource;
    }

    private record Identifier(String value, String source) {
        private static final Identifier EMPTY = new Identifier(null, null);
    }
}
