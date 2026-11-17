package dev.patra.catalog.infra.publication.persistence;

import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.PublicationSourceRow;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAbstractDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAlternativeAbstractDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAuthorAffiliationDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAuthorDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationDateDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationFundingDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationIdentifierDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationInvestigatorDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationKeywordDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationMeshHeadingDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationMeshQualifierDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationMetadataDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationPersonalNameSubjectDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationSupplMeshDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationSyncDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationTypeDao;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationMetadataEntity;
import dev.patra.catalog.infra.publication.persistence.mapper.PublicationJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Repository;

/// `PublicationSyncRepository` 的 JPA 实现（PubMed 增量同步覆盖更新）。
///
/// 覆盖顺序（一个事务，由调用方的 chunk 事务承载）：
///
/// 1. 复活这批里已删除的行；
/// 2. 按"先子后父"批量删 PubMed 管的子表，标识符只删 `source = 'PubMed'`；
/// 3. 主表与 metadata 用**显式列名**的 JPQL 批量 UPDATE，只改 PubMed 管的列——
///    不用托管实体改字段，否则 Hibernate 按载入时的值写回整行，会覆盖并发运行的
///    抓取 / 解析任务在这期间写入的 `is_oa`、`has_full_text`；
/// 4. `clear` 后复用 `PublicationRepositoryAdapter#writeImportOwnedAssociations` 重建子表。
///
/// 批量语句都在 [PublicationSyncDao] 上；写主表时带操作人，同步由定时任务触发，操作人为空。
/// `IN` 参数按 5000 一批分片（PostgreSQL 绑定参数上限 65535）。标为 `@Repository` 以启用持久化异常转换：
/// `flush` 撞唯一约束时转成 `DataIntegrityViolationException`，作业的跳过策略才认得。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PublicationSyncRepositoryAdapter implements PublicationSyncRepository {

  private static final int IN_BATCH_SIZE = 5000;

  private static final String PUBMED_SOURCE = "PubMed";

  private final EntityManager entityManager;
  private final PublicationDao publicationDao;
  private final PublicationSyncDao syncDao;
  private final AuditorAware<Long> auditor;
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

  /// 按 PMID 查同步目标：文献行（含已删除）与来源位置，两边任一有就返回（墓碑只有来源行）。
  ///
  /// 墓碑：只被删除列表记过、没写过原文的来源行（只有来源位置），用来挡住比删除更早的旧文件把这篇再导回来。
  ///
  /// @param pmids PMID
  /// @return PMID → 同步目标
  @Override
  public Map<String, PublicationSyncTarget> findSyncTargetsByPmids(Collection<String> pmids) {
    Map<String, PublicationSyncTarget> result = new HashMap<>();
    for (List<String> batch : partition(pmids)) {
      for (Object[] cols : syncDao.findSyncTargets(batch.toArray(String[]::new))) {
        String pmid = (String) cols[1];
        result.put(
            pmid,
            PublicationSyncTarget.builder()
                .publicationId(cols[0] == null ? null : ((Number) cols[0]).longValue())
                .pmid(pmid)
                .doi((String) cols[2])
                .deleted((Boolean) cols[3])
                .sourceRank(cols[4] == null ? null : ((Number) cols[4]).intValue())
                .build());
      }
    }
    return result;
  }

  /// 按 DOI 原值精确查文献 ID，包含已删除的行。
  ///
  /// @param dois DOI
  /// @return DOI 原值 → 文献 ID
  @Override
  public Map<String, Long> findPublicationIdsByDois(Collection<String> dois) {
    Map<String, Long> result = new HashMap<>();
    for (List<String> batch : partition(dois)) {
      for (Object[] cols : syncDao.findIdsByDois(batch)) {
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
  /// @param refreshSyncedAt 要刷新 `last_synced_at` 的文献 ID；其余保留原值
  @Override
  public void overwriteAllWithAssociations(
      List<PublicationCompleteData> data,
      Map<Long, String> doiOverrides,
      Instant syncedAt,
      Set<Long> refreshSyncedAt) {
    if (data == null || data.isEmpty()) {
      return;
    }
    List<Long> ids = data.stream().map(PublicationCompleteData::getPublicationId).toList();
    if (ids.contains(null)) {
      throw new IllegalArgumentException("覆盖更新的文献必须已分配 ID");
    }

    Long actorId = auditor.getCurrentAuditor().orElse(null);
    entityManager.flush();
    reviveDeleted(ids, actorId);
    deleteImportOwnedChildren(ids);
    for (PublicationCompleteData item : data) {
      updatePublicationColumns(
          item,
          doiOverrides,
          refreshSyncedAt.contains(item.getPublicationId()) ? syncedAt : null,
          actorId);
      updateMetadataColumns(item);
    }
    entityManager.flush();
    entityManager.clear();

    publicationRepositoryAdapter.writeImportOwnedAssociations(data);
    log.debug("覆盖更新 {} 条文献（含子表）", data.size());
  }

  /// 按 PMID 写来源行，存在则整行覆盖（位置、原文、解析时间）。
  ///
  /// @param rows 来源行
  @Override
  public void upsertSources(List<PublicationSourceRow> rows) {
    for (PublicationSourceRow row : rows) {
      publicationDao.upsertSource(
          SnowflakeIdGenerator.getId(),
          row.pmid(),
          row.sourceFile(),
          row.sourceRank(),
          row.rawXml(),
          row.parsedAt() == null ? null : Timestamp.from(row.parsedAt()));
    }
  }

  /// 按 `DeleteCitation` 软删文献，带来源位置守卫；`writeTombstones` 时为库里没有的 PMID 写墓碑。
  ///
  /// @param pmids 被删除的 PMID
  /// @param position 删除文件的来源位置
  /// @param writeTombstones 是否写墓碑
  /// @param deletedAt 删除时间
  /// @return 本次实际软删的行数
  @Override
  public int softDeleteByPmids(
      Collection<String> pmids,
      SourcePosition position,
      boolean writeTombstones,
      Instant deletedAt) {
    Long actorId = auditor.getCurrentAuditor().orElse(null);
    int total = 0;
    // 删除列表可能重复同一 PMID：同一条 upsert 不能两次命中同一行
    for (List<String> batch : partition(new LinkedHashSet<>(pmids))) {
      total +=
          syncDao.markDeletedByPmids(batch, position.rank(), Timestamp.from(deletedAt), actorId);
      // 墓碑候选：要写墓碑时是整个删除列表，否则只取库里已有的 PMID；新行各带一个雪花 ID
      List<String> candidates =
          writeTombstones ? batch : publicationDao.findExistingPmids(batch.toArray(String[]::new));
      if (!candidates.isEmpty()) {
        Long[] ids =
            candidates.stream().map(pmid -> SnowflakeIdGenerator.getId()).toArray(Long[]::new);
        publicationDao.upsertTombstones(
            ids, candidates.toArray(String[]::new), position.fileName(), position.rank());
      }
    }
    return total;
  }

  /// 本地重算的候选：有原文、文献未删除的来源行，按 PMID 升序键集分页（PMID 按文本比较，只求稳定不求数值序）。
  ///
  /// @param afterPmid 上一页最后一个 PMID；第一页为 null
  /// @param limit 每页条数
  /// @return 原文与来源位置
  @Override
  public List<PubmedSourceRecord> findReparseCandidates(String afterPmid, int limit) {
    return syncDao.findReparseCandidates(afterPmid, limit).stream()
        .map(
            cols ->
                PubmedSourceRecord.of(
                    (String) cols[0],
                    (String) cols[1],
                    SourcePosition.of((String) cols[2], ((Number) cols[3]).intValue())))
        .toList();
  }

  /// 复活这批里已删除的行。
  private void reviveDeleted(List<Long> ids, Long actorId) {
    for (List<Long> batch : partition(ids)) {
      syncDao.revive(batch, actorId);
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
      PublicationCompleteData item,
      Map<Long, String> doiOverrides,
      Instant syncedAt,
      Long actorId) {
    long id = item.getPublicationId();
    PublicationEntity row = jpaConverter.toEntity(item.publication());
    String doi = doiOverrides.containsKey(id) ? doiOverrides.get(id) : row.getDoi();
    syncDao.updateImportColumns(id, row, doi, syncedAt, actorId);
  }

  /// metadata 只改七个 PubMed 列，`has_full_text`、`full_text_url` 等保留；没有 metadata 行时新建。
  private void updateMetadataColumns(PublicationCompleteData item) {
    if (!item.hasMetadata()) {
      return;
    }
    long id = item.getPublicationId();
    PublicationMetadataEntity m = jpaConverter.toMetadataEntity(item.metadata(), id);
    int updated = syncDao.updateMetadataImportColumns(m);
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
