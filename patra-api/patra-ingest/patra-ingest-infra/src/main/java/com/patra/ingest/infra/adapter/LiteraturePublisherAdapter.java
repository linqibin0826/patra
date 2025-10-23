package com.patra.ingest.infra.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardAuthor;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardJournal;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** Anti-corruption layer that converts ingest domain models into catalog API DTOs. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherAdapter implements LiteraturePublisherPort {

  private final ObjectMapper objectMapper;

  @Override
  public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
    List<StandardLiterature> safeLiterature =
        literature == null ? Collections.emptyList() : literature;

    List<LiteratureDTO> payload =
        safeLiterature.stream().map(this::toDto).collect(Collectors.toList());

    try {
      byte[] serialized = objectMapper.writeValueAsBytes(payload);
      String storageKey = generateStorageKey(context);

      log.info(
          "literature payload prepared runId={} batchNo={} provenance={} size={} bytes entries={}",
          context.runId(),
          context.batchNo(),
          context.provenanceCode(),
          serialized.length,
          payload.size());

      // TODO(catalog-storage): replace placeholder with actual MinIO upload logic and persist the
      // returned object handle.

      return PublishResult.builder().storageKey(storageKey).publishedCount(payload.size()).build();

    } catch (JsonProcessingException ex) {
      throw new LiteraturePublishException("Failed to serialize literature payload", ex);
    } catch (Exception ex) {
      throw new LiteraturePublishException("Failed to publish literature payload", ex);
    }
  }

  private LiteratureDTO toDto(StandardLiterature source) {
    return LiteratureDTO.builder()
        .title(source.getTitle())
        .abstractText(source.getAbstractText())
        .authors(mapAuthors(source.getAuthors()))
        .journal(mapJournal(source.getJournal()))
        .identifiers(source.getIdentifiers())
        .publicationDate(source.getPublicationDate())
        .keywords(source.getKeywords())
        .language(null)
        .publicationTypes(List.of())
        .build();
  }

  private List<AuthorDTO> mapAuthors(List<StandardAuthor> authors) {
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
        .collect(Collectors.toUnmodifiableList());
  }

  private JournalDTO mapJournal(StandardJournal journal) {
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

  private List<String> resolveAffiliations(String affiliation) {
    if (!StringUtils.hasText(affiliation)) {
      return List.of();
    }
    return List.of(affiliation);
  }

  private String generateStorageKey(PublishContext context) {
    String provenance =
        StringUtils.hasText(context.provenanceCode())
            ? context.provenanceCode().toLowerCase(Locale.ROOT)
            : "unknown";
    long runId = context.runId() != null ? context.runId() : 0L;
    int batchNo = context.batchNo();
    return String.format("%s/%d/batch-%d.json", provenance, runId, batchNo);
  }

  /** Publishing exception indicating serialization or storage failure. */
  public static class LiteraturePublishException extends RuntimeException {
    public LiteraturePublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
