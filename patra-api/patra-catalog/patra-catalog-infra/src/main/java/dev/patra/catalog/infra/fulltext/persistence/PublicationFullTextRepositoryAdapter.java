package dev.patra.catalog.infra.fulltext.persistence;

import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.infra.fulltext.persistence.dao.PublicationFullTextDao;
import dev.patra.catalog.infra.fulltext.persistence.entity.PublicationFullTextEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
  private final EntityManager entityManager;
  private final JsonMapper jsonMapper;

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

  /// 正文表没有实体：用 native `INSERT … ON CONFLICT (publication_id) DO UPDATE` 写入，
  /// 正文以注入的 `JsonMapper` 序列化。
  ///
  /// @param publicationId 文献 ID
  /// @param body 正文
  /// @param parserVersion 解析器版本
  @Override
  public void saveBody(long publicationId, FullTextBody body, int parserVersion) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO cat_publication_fulltext_body (publication_id, body, parser_version)
            VALUES (:id, CAST(:body AS jsonb), :version)
            ON CONFLICT (publication_id) DO UPDATE
               SET body = EXCLUDED.body, parser_version = EXCLUDED.parser_version, updated_at = now()
            """)
        .setParameter("id", publicationId)
        .setParameter("body", jsonMapper.writeValueAsString(body))
        .setParameter("version", parserVersion)
        .executeUpdate();
  }

  /// 更新 `has_full_text`；没有 metadata 行时什么也不做。
  ///
  /// @param publicationId 文献 ID
  /// @param value 是否有全文
  @Override
  public void setHasFullText(long publicationId, boolean value) {
    entityManager
        .createQuery(
            "UPDATE PublicationMetadataEntity m SET m.hasFullText = :value"
                + " WHERE m.publicationId = :id")
        .setParameter("value", value)
        .setParameter("id", publicationId)
        .executeUpdate();
  }
}
