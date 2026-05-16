package dev.linqibin.patra.catalog.infra.adapter.persistence;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.model.entity.MeshConcept;
import dev.linqibin.patra.catalog.domain.model.entity.MeshEntryTerm;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.ScrSource;
import dev.linqibin.patra.catalog.domain.port.repository.MeshScrRepository;
import dev.linqibin.patra.catalog.infra.persistence.converter.mapper.MeshScrJpaMapper;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryTermDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrHeadingMappedToDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrIndexingInfoDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrPharmacologicalActionDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrSourceDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshConceptEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshEntryTermEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrHeadingMappedToEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrIndexingInfoEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrPharmacologicalActionEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrSourceEntity;
import dev.linqibin.starter.jpa.entity.IdAwareEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH SCR（补充概念记录）聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理 MeshScrAggregate（SCR 聚合根）的持久化
/// - 支持 NLM MeSH Supplemental XML 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// - 使用 JPA `saveAll()` 配合 Hibernate 批量配置
/// - 定期 flush + clear 防止大批量操作内存溢出
/// - 子表通过 `scrUi` 业务键关联，无需等待主表 ID 回填
///
/// **子表说明**：
///
/// - HeadingMappedTo：SCR 到 Descriptor 的映射关系
/// - Concept：概念实体（复用 cat_mesh_concept 表，recordType=SCR）
/// - EntryTerm：入口术语（复用 cat_mesh_entry_term 表，recordType=SCR）
/// - Source：数据来源列表
/// - IndexingInfo：索引信息
/// - PharmacologicalAction：药理作用
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshScrRepositoryAdapter implements MeshScrRepository {

  private static final int FLUSH_INTERVAL = 50;

  private final MeshScrDao scrDao;
  private final MeshScrHeadingMappedToDao headingMappedToDao;
  private final MeshConceptDao conceptDao;
  private final MeshEntryTermDao entryTermDao;
  private final MeshScrSourceDao sourceDao;
  private final MeshScrIndexingInfoDao indexingInfoDao;
  private final MeshScrPharmacologicalActionDao pharmacologicalActionDao;
  private final MeshScrJpaMapper converter;
  private final EntityManager entityManager;

  @Override
  public boolean hasAnyData() {
    return scrDao.hasAnyData();
  }

  @Override
  public void insertAll(List<MeshScrAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换主表实体并分配 ID
    List<MeshScrEntity> scrEntities = new ArrayList<>(aggregates.size());
    for (MeshScrAggregate aggregate : aggregates) {
      MeshScrEntity entity = converter.toEntity(aggregate);
      assignIdIfMissing(entity);
      scrEntities.add(entity);
    }

    // 2. 收集子表数据
    List<MeshScrHeadingMappedToEntity> headingMappedToEntities = new ArrayList<>();
    List<MeshConceptEntity> conceptEntities = new ArrayList<>();
    List<MeshEntryTermEntity> entryTermEntities = new ArrayList<>();
    List<MeshScrSourceEntity> sourceEntities = new ArrayList<>();
    List<MeshScrIndexingInfoEntity> indexingInfoEntities = new ArrayList<>();
    List<MeshScrPharmacologicalActionEntity> pharmacologicalActionEntities = new ArrayList<>();

    for (MeshScrAggregate aggregate : aggregates) {
      String scrUi = aggregate.getUi().ui();
      collectChildData(
          aggregate,
          scrUi,
          headingMappedToEntities,
          conceptEntities,
          entryTermEntities,
          sourceEntities,
          indexingInfoEntities,
          pharmacologicalActionEntities);
    }

    // 3. 批量插入主表
    saveBatchWithFlush(scrEntities, "SCR");

    // 4. 批量插入子表
    if (!headingMappedToEntities.isEmpty()) {
      saveBatchWithFlush(headingMappedToEntities, "映射关系");
    }
    if (!conceptEntities.isEmpty()) {
      saveBatchWithFlush(conceptEntities, "概念");
    }
    if (!entryTermEntities.isEmpty()) {
      saveBatchWithFlush(entryTermEntities, "入口术语");
    }
    if (!sourceEntities.isEmpty()) {
      saveBatchWithFlush(sourceEntities, "来源");
    }
    if (!indexingInfoEntities.isEmpty()) {
      saveBatchWithFlush(indexingInfoEntities, "索引信息");
    }
    if (!pharmacologicalActionEntities.isEmpty()) {
      saveBatchWithFlush(pharmacologicalActionEntities, "药理作用");
    }
  }

  /// 收集聚合根的子表数据。
  ///
  /// @param aggregate 聚合根
  /// @param scrUi SCR UI（用于子表关联）
  /// @param headingMappedToEntities 映射关系实体列表（输出参数）
  /// @param conceptEntities 概念实体列表（输出参数）
  /// @param entryTermEntities 入口术语实体列表（输出参数）
  /// @param sourceEntities 来源实体列表（输出参数）
  /// @param indexingInfoEntities 索引信息实体列表（输出参数）
  /// @param pharmacologicalActionEntities 药理作用实体列表（输出参数）
  private void collectChildData(
      MeshScrAggregate aggregate,
      String scrUi,
      List<MeshScrHeadingMappedToEntity> headingMappedToEntities,
      List<MeshConceptEntity> conceptEntities,
      List<MeshEntryTermEntity> entryTermEntities,
      List<MeshScrSourceEntity> sourceEntities,
      List<MeshScrIndexingInfoEntity> indexingInfoEntities,
      List<MeshScrPharmacologicalActionEntity> pharmacologicalActionEntities) {

    // 收集 HeadingMappedTo
    for (HeadingMappedTo mapping : aggregate.getHeadingMappedTos()) {
      MeshScrHeadingMappedToEntity entity = converter.toHeadingMappedToEntity(mapping, scrUi);
      assignIdIfMissing(entity);
      headingMappedToEntities.add(entity);
    }

    // 收集 Concept
    for (MeshConcept concept : aggregate.getConcepts()) {
      MeshConceptEntity entity = converter.toConceptEntity(concept, scrUi);
      assignIdIfMissing(entity);
      conceptEntities.add(entity);
    }

    // 收集 EntryTerm
    for (MeshEntryTerm entryTerm : aggregate.getEntryTerms()) {
      MeshEntryTermEntity entity = converter.toEntryTermEntity(entryTerm, scrUi);
      assignIdIfMissing(entity);
      entryTermEntities.add(entity);
    }

    // 收集 Source（带排序号）
    List<ScrSource> sources = aggregate.getSources();
    for (int i = 0; i < sources.size(); i++) {
      MeshScrSourceEntity entity = converter.toSourceEntity(sources.get(i), scrUi, i);
      assignIdIfMissing(entity);
      sourceEntities.add(entity);
    }

    // 收集 IndexingInfo
    for (IndexingInfo info : aggregate.getIndexingInfos()) {
      MeshScrIndexingInfoEntity entity = converter.toIndexingInfoEntity(info, scrUi);
      assignIdIfMissing(entity);
      indexingInfoEntities.add(entity);
    }

    // 收集 PharmacologicalAction
    for (PharmacologicalAction action : aggregate.getPharmacologicalActions()) {
      MeshScrPharmacologicalActionEntity entity =
          converter.toPharmacologicalActionEntity(action, scrUi);
      assignIdIfMissing(entity);
      pharmacologicalActionEntities.add(entity);
    }
  }

  /// 批量保存实体，并定期 flush + clear 以防内存溢出。
  ///
  /// @param entities 实体列表
  /// @param entityName 实体名称（用于日志）
  /// @param <T> 实体类型
  private <T> void saveBatchWithFlush(List<T> entities, String entityName) {
    int count = 0;
    for (T entity : entities) {
      entityManager.persist(entity);
      if (++count % FLUSH_INTERVAL == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
    entityManager.flush();
    entityManager.clear();
    log.debug("批量插入{} {} 条", entityName, entities.size());
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// 所有 MeSH 实体均继承 BaseJpaEntity，使用统一方法避免代码重复。
  ///
  /// @param entity JPA 实体（继承自 BaseJpaEntity）
  private void assignIdIfMissing(IdAwareEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  @Override
  public Map<String, String> findAllByNameIn(Collection<String> names) {
    if (names == null || names.isEmpty()) {
      return Map.of();
    }

    List<MeshScrEntity> entities = scrDao.findAllByNameIn(names);

    // 转换为 name → ui 映射
    return entities.stream()
        .collect(
            Collectors.toMap(
                MeshScrEntity::getName,
                MeshScrEntity::getUi,
                (existing, replacement) -> existing // 保留首个匹配
                ));
  }
}
