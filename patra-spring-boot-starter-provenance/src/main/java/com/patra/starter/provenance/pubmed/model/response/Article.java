package com.patra.starter.provenance.pubmed.model.response;

import java.util.List;

/**
 * Article object
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Article(
    Journal journal,
    String articleTitle,
    String pagination,
    String abstractText,
    List<Author> authorList,
    String language,
    List<String> publicationTypeList
) {
}
