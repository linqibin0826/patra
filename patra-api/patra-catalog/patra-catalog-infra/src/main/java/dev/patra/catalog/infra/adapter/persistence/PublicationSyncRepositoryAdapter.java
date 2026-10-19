package dev.patra.catalog.infra.adapter.persistence;

import dev.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.persistence.converter.mapper.PublicationJpaMapper;
import dev.patra.catalog.infra.persistence.dao.PublicationAbstractDao;
import dev.patra.catalog.infra.persistence.dao.PublicationAlternativeAbstractDao;
import dev.patra.catalog.infra.persistence.dao.PublicationAuthorAffiliationDao;
import dev.patra.catalog.infra.persistence.dao.PublicationAuthorDao;
import dev.patra.catalog.infra.persistence.dao.PublicationDateDao;
import dev.patra.catalog.infra.persistence.dao.PublicationFundingDao;
import dev.patra.catalog.infra.persistence.dao.PublicationIdentifierDao;
import dev.patra.catalog.infra.persistence.dao.PublicationInvestigatorDao;
import dev.patra.catalog.infra.persistence.dao.PublicationKeywordDao;
import dev.patra.catalog.infra.persistence.dao.PublicationMeshHeadingDao;
import dev.patra.catalog.infra.persistence.dao.PublicationMeshQualifierDao;
import dev.patra.catalog.infra.persistence.dao.PublicationMetadataDao;
import dev.patra.catalog.infra.persistence.dao.PublicationPersonalNameSubjectDao;
import dev.patra.catalog.infra.persistence.dao.PublicationSupplMeshDao;
import dev.patra.catalog.infra.persistence.dao.PublicationTypeDao;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationMetadataEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// `PublicationSyncRepository` 的 JPA 实现（PubMed 增量同步覆盖更新）。
///
/// 覆盖顺序（一个事务，由调用方的 chunk 事务承载）：
///
/// 1. native 复活已软删除的行（JPQL 会被 `@SoftDelete` 过滤掉）；
/// 2. 按"先子后父"批量删 PubMed 管的子表，标识符只删 `source = 'PubMed'`；
/// 3. 主表与 metadata 用**显式列名**的 JPQL 批量 UPDATE，只改 PubMed 管的列——
///    不用托管实体改字段，否则 Hibernate 按载入时的值写回整行，会覆盖并发运行的
///    抓取 / 解析任务在这期间写入的 `is_oa`、`has_full_text`；
/// 4. `clear` 后复用 `PublicationRepositoryAdapter#writeImportOwnedAssociations` 重建子表。
///
/// `IN` 参数按 5000 一批分片（PostgreSQL 绑定参数上限 65535）。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicationSyncRepositoryAdapter implements PublicationSyncRepository {

  private static final int IN_BATCH_SIZE = 5000;

  private static final String PUBMED_SOURCE = "PubMed";

  private final EntityManager entityManager;
  private final PublicationJpaMapper jpaConverter;
  private final PublicationRepositoryAdapter publicationRepositoryAdapter;
  private final PublicationMetadataDao metadataDao;
  private final PublicationMeshQualifierDao meshQualifierDao;
  private final PublicationMeshHeadingDao meshHeadingDao;
  private final PublicationAbstractDao abstractDao;
  private final PublicationAlternativeAbstractDao alternativeAbstractDao;
  private final PublicationDateDao dateDao;
  private final PublicationTypeDao typeDao;
  private final PublicationSupplMeshDao supplMeshDao;
  private final PublicationKeywordDao keywordDao;
  private final PublicationFundingDao fundingDao;
  private final PublicationInvestigatorDao investigatorDao;
  private final PublicationPersonalNameSubjectDao personalNameSubjectDao;
  private final PublicationAuthorAffiliationDao authorAffiliationDao;
  private final PublicationAuthorDao authorDao;
  private final PublicationIdentifierDao identifierDao;

  /// 按 PMID 查库里的文献，包含已软删除的行。
  ///
  /// @param pmids PMID
  /// @return PMID → 同步目标
  @Override
  public Map<String, PublicationSyncTarget> findSyncTargetsByPmids(Collection<String> pmids) {
    Map<String, PublicationSyncTarget> result = new HashMap<>();
    for (List<String> batch : partition(pmids)) {
      List<?> rows =
          entityManager
              .createNativeQuery(
                  "SELECT id, pmid, doi, deleted_at IS NOT NULL FROM cat_publication"
                      + " WHERE pmid IN (:pmids)")
              .setParameter("pmids", batch)
              .getResultList();
      for (Object row : rows) {
        Object[] cols = (Object[]) row;
        String pmid = (String) cols[1];
        result.put(
            pmid,
            PublicationSyncTarget.of(
                ((Number) cols[0]).longValue(), pmid, (String) cols[2], (Boolean) cols[3]));
      }
    }
    return result;
  }

  /// 按 DOI 原值精确查文献 ID，包含已软删除的行。
  ///
  /// @param dois DOI
  /// @return DOI 原值 → 文献 ID
  @Override
  public Map<String, Long> findPublicationIdsByDois(Collection<String> dois) {
    Map<String, Long> result = new HashMap<>();
    for (List<String> batch : partition(dois)) {
      List<?> rows =
          entityManager
              .createNativeQuery("SELECT doi, id FROM cat_publication WHERE doi IN (:dois)")
              .setParameter("dois", batch)
              .getResultList();
      for (Object row : rows) {
        Object[] cols = (Object[]) row;
        result.put((String) cols[0], ((Number) cols[1]).longValue());
      }
    }
    return result;
  }

  /// 覆盖更新已有文献及其 PubMed 子数据。
  ///
  /// @param data 覆盖数据，已分配好已有 ID
  /// @param doiOverrides 文献 ID → 要写入的 DOI
  /// @param syncedAt 本次同步时间
  @Override
  public void overwriteAllWithAssociations(
      List<PublicationCompleteData> data, Map<Long, String> doiOverrides, Instant syncedAt) {
    if (data == null || data.isEmpty()) {
      return;
    }
    List<Long> ids = data.stream().map(PublicationCompleteData::getPublicationId).toList();
    if (ids.contains(null)) {
      throw new IllegalArgumentException("覆盖更新的文献必须已分配 ID");
    }

    entityManager.flush();
    reviveSoftDeleted(ids);
    deleteImportOwnedChildren(ids);
    for (PublicationCompleteData item : data) {
      updatePublicationColumns(item, doiOverrides, syncedAt);
      updateMetadataColumns(item);
    }
    entityManager.flush();
    entityManager.clear();

    publicationRepositoryAdapter.writeImportOwnedAssociations(data);
    log.debug("覆盖更新 {} 条文献（含子表）", data.size());
  }

  /// 按 PMID 软删除文献，已删除的不重复计数。
  ///
  /// @param pmids PMID
  /// @param deletedAt 删除时间
  /// @return 本次实际软删除的行数
  @Override
  public int softDeleteByPmids(Collection<String> pmids, Instant deletedAt) {
    int total = 0;
    for (List<String> batch : partition(pmids)) {
      total +=
          entityManager
              .createNativeQuery(
                  "UPDATE cat_publication SET deleted_at = :deletedAt"
                      + " WHERE pmid IN (:pmids) AND deleted_at IS NULL")
              .setParameter("deletedAt", Timestamp.from(deletedAt))
              .setParameter("pmids", batch)
              .executeUpdate();
    }
    return total;
  }

  /// 复活已软删除的行。
  private void reviveSoftDeleted(List<Long> ids) {
    for (List<Long> batch : partition(ids)) {
      entityManager
          .createNativeQuery(
              "UPDATE cat_publication SET deleted_at = NULL"
                  + " WHERE id IN (:ids) AND deleted_at IS NOT NULL")
          .setParameter("ids", batch)
          .executeUpdate();
    }
  }

  /// 按"先子后父"批量删 PubMed 管的子表；共享实体（关键词、研究者、作者、期刊、MeSH 字典）不删。
  private void deleteImportOwnedChildren(List<Long> ids) {
    for (List<Long> batch : partition(ids)) {
      meshQualifierDao.deleteByPublicationIdIn(batch);
      meshHeadingDao.deleteByPublicationIdIn(batch);
      abstractDao.deleteByPublicationIdIn(batch);
      alternativeAbstractDao.deleteByPublicationIdIn(batch);
      dateDao.deleteByPublicationIdIn(batch);
      typeDao.deleteByPublicationIdIn(batch);
      supplMeshDao.deleteByPublicationIdIn(batch);
      keywordDao.deleteByPublicationIdIn(batch);
      fundingDao.deleteByPublicationIdIn(batch);
      investigatorDao.deleteByPublicationIdIn(batch);
      personalNameSubjectDao.deleteByPublicationIdIn(batch);
      authorAffiliationDao.deleteAllByPublicationIdIn(batch);
      authorDao.deleteAllByPublicationIdIn(batch);
      identifierDao.deleteByPublicationIdInAndSource(batch, PUBMED_SOURCE);
    }
  }

  /// 主表只改 PubMed 管的列，`is_oa`、`oa_status`、`citation_count`、`ext_data`、`record_remarks` 不动。
  ///
  /// 列值取自与插入路径相同的 `toEntity` 转换，保证两条路径写出的值一致。
  private void updatePublicationColumns(
      PublicationCompleteData item, Map<Long, String> doiOverrides, Instant syncedAt) {
    long id = item.getPublicationId();
    PublicationEntity e = jpaConverter.toEntity(item.publication());
    String doi = doiOverrides.containsKey(id) ? doiOverrides.get(id) : e.getDoi();
    entityManager
        .createQuery(
            "UPDATE PublicationEntity p SET"
                + " p.provenanceCode = :provenanceCode, p.doi = :doi, p.title = :title,"
                + " p.originalTitle = :originalTitle, p.languageRaw = :languageRaw,"
                + " p.languageCode = :languageCode, p.publicationStatus = :publicationStatus,"
                + " p.mediaType = :mediaType, p.publicationYear = :publicationYear,"
                + " p.authorsComplete = :authorsComplete,"
                + " p.numberOfReferences = :numberOfReferences,"
                + " p.conflictOfInterest = :conflictOfInterest, p.venueId = :venueId,"
                + " p.venueInstanceId = :venueInstanceId, p.lastSyncedAt = :syncedAt,"
                + " p.version = p.version + 1"
                + " WHERE p.id = :id")
        .setParameter("provenanceCode", e.getProvenanceCode())
        .setParameter("doi", doi)
        .setParameter("title", e.getTitle())
        .setParameter("originalTitle", e.getOriginalTitle())
        .setParameter("languageRaw", e.getLanguageRaw())
        .setParameter("languageCode", e.getLanguageCode())
        .setParameter("publicationStatus", e.getPublicationStatus())
        .setParameter("mediaType", e.getMediaType())
        .setParameter("publicationYear", e.getPublicationYear())
        .setParameter("authorsComplete", e.getAuthorsComplete())
        .setParameter("numberOfReferences", e.getNumberOfReferences())
        .setParameter("conflictOfInterest", e.getConflictOfInterest())
        .setParameter("venueId", e.getVenueId())
        .setParameter("venueInstanceId", e.getVenueInstanceId())
        .setParameter("syncedAt", syncedAt)
        .setParameter("id", id)
        .executeUpdate();
  }

  /// metadata 只改七个 PubMed 列，`has_full_text`、`full_text_url` 等保留；没有 metadata 行时新建。
  private void updateMetadataColumns(PublicationCompleteData item) {
    if (!item.hasMetadata()) {
      return;
    }
    long id = item.getPublicationId();
    PublicationMetadataEntity m = jpaConverter.toMetadataEntity(item.metadata(), id);
    int updated =
        entityManager
            .createQuery(
                "UPDATE PublicationMetadataEntity m SET"
                    + " m.indexingStatus = :indexingStatus, m.indexingMethod = :indexingMethod,"
                    + " m.owner = :owner, m.citationSubset = :citationSubset,"
                    + " m.dataSource = :dataSource, m.importBatch = :importBatch,"
                    + " m.importDate = :importDate"
                    + " WHERE m.publicationId = :id")
            .setParameter("indexingStatus", m.getIndexingStatus())
            .setParameter("indexingMethod", m.getIndexingMethod())
            .setParameter("owner", m.getOwner())
            .setParameter("citationSubset", m.getCitationSubset())
            .setParameter("dataSource", m.getDataSource())
            .setParameter("importBatch", m.getImportBatch())
            .setParameter("importDate", m.getImportDate())
            .setParameter("id", id)
            .executeUpdate();
    if (updated == 0) {
      m.setId(SnowflakeIdGenerator.getId());
      metadataDao.save(m);
    }
  }

  /// 把集合切成不超过 5000 个元素的批次。
  private static <T> List<List<T>> partition(Collection<T> values) {
    List<T> all = List.copyOf(values);
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < all.size(); i += IN_BATCH_SIZE) {
      batches.add(all.subList(i, Math.min(i + IN_BATCH_SIZE, all.size())));
    }
    return batches;
  }
}
