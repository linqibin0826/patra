package com.patra.ingest.infra.integration.storage.acl;

import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.model.StandardLiterature;
import com.patra.common.model.StandardLiterature.StandardAuthor;
import com.patra.common.model.StandardLiterature.StandardJournal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Anti-Corruption Layer (ACL) converter for transforming ingest domain models into catalog API
 * DTOs.
 *
 * <p>This MapStruct converter protects the ingest bounded context from external API contracts by
 * providing explicit, versioned mappings. Changes to catalog API structures are isolated to this
 * converter.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>StandardLiterature → LiteratureDTO (catalog external API format)
 *   <li>StandardAuthor → AuthorDTO (with affiliation normalization)
 *   <li>StandardJournal → JournalDTO (journal metadata)
 * </ul>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LiteratureConverter {

  /**
   * Converts domain StandardLiterature to catalog LiteratureDTO.
   *
   * @param source domain literature model
   * @return catalog API DTO
   */
  @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
  @Mapping(target = "journal", source = "journal", qualifiedByName = "mapJournal")
  @Mapping(target = "language", ignore = true)
  @Mapping(target = "publicationTypes", expression = "java(java.util.List.of())")
  LiteratureDTO toDto(StandardLiterature source);

  /**
   * Converts a list of StandardLiterature to LiteratureDTO list.
   *
   * @param sources domain literature list
   * @return catalog API DTO list
   */
  List<LiteratureDTO> toDto(List<StandardLiterature> sources);

  /**
   * Maps domain authors to catalog AuthorDTO.
   *
   * @param authors domain author list
   * @return catalog author DTO list
   */
  @Named("mapAuthors")
  default List<AuthorDTO> mapAuthors(List<StandardAuthor> authors) {
    if (CollectionUtils.isEmpty(authors)) {
      return List.of();
    }
    return authors.stream()
        .map(
            author ->
                AuthorDTO.builder()
                    .lastName(author.getLastName())
                    .foreName(author.getForeName())
                    .initials(null)
                    .affiliations(resolveAffiliations(author.getAffiliation()))
                    .identifier(null)
                    .identifierSource(null)
                    .build())
        .toList();
  }

  /**
   * Maps domain journal to catalog JournalDTO.
   *
   * @param journal domain journal
   * @return catalog journal DTO or null
   */
  @Named("mapJournal")
  default JournalDTO mapJournal(StandardJournal journal) {
    if (journal == null) {
      return null;
    }
    return JournalDTO.builder()
        .title(journal.getTitle())
        .issn(journal.getIssn())
        .issnType(null)
        .publisher(journal.getPublisher())
        .country(null)
        .build();
  }

  /**
   * Resolves single affiliation string into list format.
   *
   * @param affiliation affiliation text
   * @return affiliation list (empty if no text)
   */
  default List<String> resolveAffiliations(String affiliation) {
    if (!StringUtils.hasText(affiliation)) {
      return List.of();
    }
    return List.of(affiliation);
  }
}
