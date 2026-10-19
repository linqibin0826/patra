package dev.patra.catalog.infra.adapter.persistence;

import dev.patra.catalog.domain.model.vo.fulltext.PublicationFullText;
import dev.patra.catalog.domain.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.infra.persistence.dao.PublicationFullTextDao;
import dev.patra.catalog.infra.persistence.entity.PublicationFullTextEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// `PublicationFullTextRepository` 的 JPA 实现。
///
/// `save` 先按 `publication_id` 取托管实体再改；并发写同一篇由任务互斥锁与
/// `uk_publication_fulltext_publication` 兜底，撞唯一约束时异常交给调用方按失败处理。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class PublicationFullTextRepositoryAdapter implements PublicationFullTextRepository {

  private final PublicationFullTextDao dao;

  /// 按文献 ID 查全文记录。
  ///
  /// @param publicationId 文献 ID
  /// @return 全文记录
  @Override
  public Optional<PublicationFullText> findByPublicationId(long publicationId) {
    return dao.findByPublicationId(publicationId)
        .map(PublicationFullTextRepositoryAdapter::toDomain);
  }

  /// 按 `publication_id` upsert。
  ///
  /// @param fullText 全文记录
  @Override
  public void save(PublicationFullText fullText) {
    PublicationFullTextEntity entity =
        dao.findByPublicationId(fullText.publicationId())
            .orElseGet(
                () -> {
                  PublicationFullTextEntity created = new PublicationFullTextEntity();
                  created.setId(SnowflakeIdGenerator.getId());
                  created.setPublicationId(fullText.publicationId());
                  return created;
                });
    entity.setPmcid(fullText.pmcid());
    entity.setPmcVersion(fullText.pmcVersion());
    entity.setStatus(fullText.status());
    entity.setUnavailableReason(fullText.unavailableReason());
    entity.setLicenseCode(fullText.licenseCode());
    entity.setManuscript(fullText.manuscript());
    entity.setObjectPrefix(fullText.objectPrefix());
    entity.setXmlMd5(fullText.xmlMd5());
    entity.setPdfObjectKey(fullText.pdfObjectKey());
    entity.setPdfSize(fullText.pdfSize());
    entity.setPdfMd5(fullText.pdfMd5());
    entity.setMedia(new ArrayList<>(fullText.media()));
    entity.setFetchedAt(fullText.fetchedAt());
    entity.setParserVersion(fullText.parserVersion());
    entity.setParsedAt(fullText.parsedAt());
    entity.setWordCount(fullText.wordCount());
    entity.setAttemptCount(fullText.attemptCount());
    entity.setLastError(fullText.lastError());
    entity.setNextAttemptAt(fullText.nextAttemptAt());
    dao.save(entity);
  }

  /// 实体转领域记录。
  private static PublicationFullText toDomain(PublicationFullTextEntity e) {
    return PublicationFullText.builder()
        .publicationId(e.getPublicationId())
        .pmcid(e.getPmcid())
        .pmcVersion(e.getPmcVersion())
        .status(e.getStatus())
        .unavailableReason(e.getUnavailableReason())
        .licenseCode(e.getLicenseCode())
        .manuscript(e.getManuscript())
        .objectPrefix(e.getObjectPrefix())
        .xmlMd5(e.getXmlMd5())
        .pdfObjectKey(e.getPdfObjectKey())
        .pdfSize(e.getPdfSize())
        .pdfMd5(e.getPdfMd5())
        .media(e.getMedia())
        .fetchedAt(e.getFetchedAt())
        .parserVersion(e.getParserVersion())
        .parsedAt(e.getParsedAt())
        .wordCount(e.getWordCount())
        .attemptCount(e.getAttemptCount())
        .lastError(e.getLastError())
        .nextAttemptAt(e.getNextAttemptAt())
        .build();
  }
}
