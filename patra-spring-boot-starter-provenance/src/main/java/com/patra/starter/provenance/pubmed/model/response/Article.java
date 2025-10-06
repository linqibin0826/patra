package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.support.JsonHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simplified PubMed article metadata extracted from the Medline citation.
 *
 * @author linqibin
 * @since 0.1.0
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

    /**
     * Parse a PubMed article citation node into a curated metadata view.
     *
     * @param node article node from the Medline citation
     * @return structured article metadata
     */
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

    /**
     * Get the journal information associated with the article.
     *
     * @return journal metadata
     */
    /**
     * Get the journal information associated with the article.
     *
     * @return journal metadata
     */
    public Journal journal() {
        return journal;
    }

    /**
     * Get the article title.
     *
     * @return article title
     */
    /**
     * Get the article title.
     *
     * @return article title
     */
    public String title() {
        return title;
    }

    /**
     * Get the abstract content split into labelled sections.
     *
     * @return immutable list of abstract sections
     */
    /**
     * Get the abstract content split into labelled sections.
     *
     * @return immutable list of abstract sections
     */
    public List<AbstractSection> abstractSections() {
        return abstractSections;
    }

    /**
     * Get the primary article language.
     *
     * @return language code or {@code null}
     */
    /**
     * Get the primary article language.
     *
     * @return language code or {@code null}
     */
    public String language() {
        return language;
    }

    /**
     * Get the list of parsed authors.
     *
     * @return immutable list of authors
     */
    /**
     * Get the list of parsed authors.
     *
     * @return immutable list of authors
     */
    public List<Author> authors() {
        return authors;
    }

    /**
     * Get the publication type identifiers.
     *
     * @return immutable list of publication types
     */
    /**
     * Get the publication type identifiers.
     *
     * @return immutable list of publication types
     */
    public List<String> publicationTypes() {
        return publicationTypes;
    }

    /**
     * Abstract section extracted from the citation.
     *
     * <p>Field descriptions:
     * @param label optional section label
     * @param text section content
     *
     * @author linqibin
     * @since 0.1.0
     */
    public record AbstractSection(String label, String text) {
    }
}
