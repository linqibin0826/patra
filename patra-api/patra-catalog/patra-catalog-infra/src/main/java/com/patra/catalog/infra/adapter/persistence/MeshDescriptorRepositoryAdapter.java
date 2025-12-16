package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.infra.persistence.jpa.MeshConceptJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshConceptRelationJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshDescriptorJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshEntryCombinationJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshEntryTermJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshTreeNumberJpaRepository;
import com.patra.catalog.infra.persistence.jpa.converter.MeshDescriptorJpaConverter;
import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptRelationEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshDescriptorEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshEntryCombinationEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshEntryTermEntity;
import com.patra.catalog.infra.persistence.jpa.entity.MeshTreeNumberEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 主题词聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理 MeshDescriptorAggregate（主题词聚合根）的持久化
/// - 支持 NLM MeSH XML 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// - 使用 JPA `saveAll()` 配合 Hibernate 批量配置
/// - 定期 flush + clear 防止大批量操作内存溢出
/// - 子表通过 `descriptorUi` 业务键关联，无需等待主表 ID 回填
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshDescriptorRepositoryAdapter implements MeshDescriptorRepository {

  private static final int FLUSH_INTERVAL = 50;

  private final MeshDescriptorJpaRepository descriptorJpaRepository;
  private final MeshTreeNumberJpaRepository treeNumberJpaRepository;
  private final MeshConceptJpaRepository conceptJpaRepository;
  private final MeshConceptRelationJpaRepository conceptRelationJpaRepository;
  private final MeshEntryTermJpaRepository entryTermJpaRepository;
  private final MeshEntryCombinationJpaRepository entryCombinationJpaRepository;
  private final MeshDescriptorJpaConverter converter;
  private final EntityManager entityManager;

  @Override
  public boolean hasAnyData() {
    return descriptorJpaRepository.hasAnyData();
  }

  @Override
  public void insertAll(List<MeshDescriptorAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换主表实体并分配 ID
    List<MeshDescriptorEntity> descriptorEntities = new ArrayList<>(aggregates.size());
    for (MeshDescriptorAggregate aggregate : aggregates) {
      MeshDescriptorEntity entity = converter.toEntity(aggregate);
      assignIdIfMissing(entity);
      descriptorEntities.add(entity);
    }

    // 2. 收集子表数据
    List<MeshTreeNumberEntity> treeNumberEntities = new ArrayList<>();
    List<MeshConceptEntity> conceptEntities = new ArrayList<>();
    List<MeshConceptRelationEntity> conceptRelationEntities = new ArrayList<>();
    List<MeshEntryTermEntity> entryTermEntities = new ArrayList<>();
    List<MeshEntryCombinationEntity> entryCombinationEntities = new ArrayList<>();

    for (MeshDescriptorAggregate aggregate : aggregates) {
      String descriptorUi = aggregate.getUi().ui();
      collectChildData(
          aggregate,
          descriptorUi,
          treeNumberEntities,
          conceptEntities,
          conceptRelationEntities,
          entryTermEntities,
          entryCombinationEntities);
    }

    // 3. 批量插入主表
    saveBatchWithFlush(descriptorEntities, "主题词");

    // 4. 批量插入子表
    if (!treeNumberEntities.isEmpty()) {
      saveBatchWithFlush(treeNumberEntities, "树形编号");
    }
    if (!conceptEntities.isEmpty()) {
      saveBatchWithFlush(conceptEntities, "概念");
    }
    if (!conceptRelationEntities.isEmpty()) {
      saveBatchWithFlush(conceptRelationEntities, "概念关系");
    }
    if (!entryTermEntities.isEmpty()) {
      saveBatchWithFlush(entryTermEntities, "入口术语");
    }
    if (!entryCombinationEntities.isEmpty()) {
      saveBatchWithFlush(entryCombinationEntities, "入口组合");
    }
  }

  /// 收集聚合根的子表数据。
  ///
  /// @param aggregate 聚合根
  /// @param descriptorUi 主题词 UI（用于子表关联）
  /// @param treeNumberEntities 树形编号实体列表（输出参数）
  /// @param conceptEntities 概念实体列表（输出参数）
  /// @param conceptRelationEntities 概念关系实体列表（输出参数）
  /// @param entryTermEntities 入口术语实体列表（输出参数）
  /// @param entryCombinationEntities 入口组合实体列表（输出参数）
  private void collectChildData(
      MeshDescriptorAggregate aggregate,
      String descriptorUi,
      List<MeshTreeNumberEntity> treeNumberEntities,
      List<MeshConceptEntity> conceptEntities,
      List<MeshConceptRelationEntity> conceptRelationEntities,
      List<MeshEntryTermEntity> entryTermEntities,
      List<MeshEntryCombinationEntity> entryCombinationEntities) {

    // 收集 TreeNumber
    aggregate
        .getTreeNumbers()
        .forEach(
            tn -> {
              MeshTreeNumberEntity entity = converter.toTreeNumberEntity(tn, descriptorUi);
              assignIdIfMissing(entity);
              treeNumberEntities.add(entity);
            });

    // 收集 Concept 和 ConceptRelation
    aggregate
        .getConcepts()
        .forEach(
            concept -> {
              MeshConceptEntity entity = converter.toConceptEntity(concept, descriptorUi);
              assignIdIfMissing(entity);
              conceptEntities.add(entity);

              // 收集概念的 ConceptRelation
              collectConceptRelations(concept, descriptorUi, conceptRelationEntities);
            });

    // 收集 EntryTerm
    aggregate
        .getEntryTerms()
        .forEach(
            et -> {
              MeshEntryTermEntity entity = converter.toEntryTermEntity(et, descriptorUi);
              assignIdIfMissing(entity);
              entryTermEntities.add(entity);
            });

    // 收集 EntryCombination
    aggregate
        .getEntryCombinations()
        .forEach(
            ec -> {
              MeshEntryCombinationEntity entity =
                  converter.toEntryCombinationEntity(ec, descriptorUi);
              assignIdIfMissing(entity);
              entryCombinationEntities.add(entity);
            });
  }

  /// 收集概念的关系数据。
  ///
  /// @param concept 概念实体
  /// @param descriptorUi 主题词 UI
  /// @param conceptRelationEntities 概念关系实体列表（输出参数）
  private void collectConceptRelations(
      MeshConcept concept,
      String descriptorUi,
      List<MeshConceptRelationEntity> conceptRelationEntities) {
    concept
        .getConceptRelations()
        .forEach(
            cr -> {
              MeshConceptRelationEntity entity =
                  converter.toConceptRelationEntity(
                      cr, descriptorUi, concept.getConceptUi().ui(), concept.isPreferred());
              assignIdIfMissing(entity);
              conceptRelationEntities.add(entity);
            });
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
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshDescriptorEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshTreeNumberEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshConceptEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshConceptRelationEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshEntryTermEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshEntryCombinationEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
