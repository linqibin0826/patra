package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simplified PubMed article metadata extracted from the Medline citation.
 */
public final class Article {

    private final Journal journal;
    private final String title;
    private final List<AbstractSection> abstractSections;
    private final String language;
    private final List<Author> authors;
    private final List<String> publicationTypes;

    private Article(
        Journal journal,
        String title,
        List<AbstractSection> abstractSections,
        String language,
        List<Author> authors,
        List<String> publicationTypes
    ) {
        this.journal = journal;
        this.title = title;
        this.abstractSections = abstractSections;
        this.language = language;
        this.authors = authors;
        this.publicationTypes = publicationTypes;
    }

    public static Article from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new Article(null, null, Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList());
        }
        Journal journal = Journal.from(node.path("Journal"));
        String title = JsonHelpers.textValue(node.path("ArticleTitle"));
        List<AbstractSection> abstracts = parseAbstract(node.path("Abstract"));
        String language = JsonHelpers.textValue(node.path("Language"));
        if (language == null && node.has("Language") && node.path("Language").isArray()) {
            language = JsonHelpers.textValue(node.path("Language").get(0));
        }
        List<Author> authors = parseAuthors(node.path("AuthorList"));
        List<String> publicationTypes = JsonHelpers.toStringList(node.path("PublicationTypeList").path("PublicationType"));
        return new Article(journal, title, abstracts, language, authors, publicationTypes);
    }

    private static List<AbstractSection> parseAbstract(JsonNode abstractNode) {
        if (abstractNode == null || abstractNode.isMissingNode() || abstractNode.isNull()) {
            return Collections.emptyList();
        }
        List<AbstractSection> sections = new ArrayList<>();
        for (JsonNode entry : JsonHelpers.toNodeList(abstractNode.path("AbstractText"))) {
            String label = JsonHelpers.textValue(entry.path("@Label"));
            String text = JsonHelpers.textValue(entry);
            if (text != null && !text.isBlank()) {
                sections.add(new AbstractSection(label, text));
            }
        }
        return Collections.unmodifiableList(sections);
    }

    private static List<Author> parseAuthors(JsonNode authorListNode) {
        if (authorListNode == null || authorListNode.isMissingNode() || authorListNode.isNull()) {
            return Collections.emptyList();
        }
        List<Author> authors = new ArrayList<>();
        for (JsonNode authorNode : JsonHelpers.toNodeList(authorListNode.path("Author"))) {
            authors.add(Author.from(authorNode));
        }
        return Collections.unmodifiableList(authors);
    }

    public Journal journal() {
        return journal;
    }

    public String title() {
        return title;
    }

    public List<AbstractSection> abstractSections() {
        return abstractSections;
    }

    public String language() {
        return language;
    }

    public List<Author> authors() {
        return authors;
    }

    public List<String> publicationTypes() {
        return publicationTypes;
    }

    public record AbstractSection(String label, String text) {
    }
}
