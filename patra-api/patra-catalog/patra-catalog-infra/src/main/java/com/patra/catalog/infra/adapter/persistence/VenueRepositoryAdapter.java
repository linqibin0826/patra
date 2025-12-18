package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.VenueJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.VenueDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenueIdentifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenueIndexingHistoryDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenueMeshDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenuePublicationStatsDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenueRelationDao;
import com.patra.catalog.infra.adapter.persistence.entity.VenueEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenueIdentifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenueIndexingHistoryEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenueMeshEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenuePublicationStatsEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenueRelationEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 出版载体聚合根仓储实现（纯 JPA 版本）。
///
/// **DDD 嵌入式值对象设计**：
///
/// - VenueAggregate：聚合根（含核心属性 + 嵌入式值对象）
/// - VenueIdentifier：值对象集合（保护 ISSN-L 唯一性不变量）
/// - 嵌入式值对象：publicationProfile、citationMetrics、openAccess、affiliatedSocieties（JSON 字段）
///
/// **职责**：
///
/// - 管理 VenueAggregate（载体聚合根）的持久化
/// - 管理 Serfile 相关数据（MeSH、关联关系、索引历史）
/// - 支持 OpenAlex Sources 和 NLM Serfile 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

  // ========== JPA 组件 ==========
  private final VenueDao venueDao;
  private final VenueIdentifierDao identifierDao;
  private final VenuePublicationStatsDao publicationStatsDao;
  private final VenueMeshDao meshDao;
  private final VenueRelationDao relationDao;
  private final VenueIndexingHistoryDao indexingHistoryDao;
  private final VenueJpaMapper jpaConverter;
  private final EntityManager entityManager;

  /// 批量操作时的 flush 阈值。
  private static final int BATCH_FLUSH_SIZE = 500;

  @Override
  public boolean hasAnyData() {
    return venueDao.hasAnyData();
  }

  @Override
  public void insertAll(List<VenueAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换主表实体并分配雪花 ID
    List<VenueEntity> venueEntities = new ArrayList<>(aggregates.size());
    for (VenueAggregate aggregate : aggregates) {
      VenueEntity entity = jpaConverter.toEntityWithQuickAccessFields(aggregate);
      assignIdIfMissing(entity);
      venueEntities.add(entity);
    }

    // 2. 批量插入主表
    List<VenueEntity> savedEntities = venueDao.saveAll(venueEntities);
    log.debug("批量插入载体 {} 条", savedEntities.size());

    // 3. 从实体回填 ID 到聚合根，并收集子表数据
    List<VenueIdentifierEntity> identifierEntities = new ArrayList<>();
    for (int i = 0; i < aggregates.size(); i++) {
      VenueAggregate aggregate = aggregates.get(i);
      VenueEntity saved = savedEntities.get(i);
      aggregate.assignId(VenueId.of(saved.getId()));
      aggregate.assignVersion(saved.getVersion());

      // 收集标识符
      for (VenueIdentifier identifier : aggregate.getIdentifiers()) {
        VenueIdentifierEntity identifierEntity =
            jpaConverter.toIdentifierEntity(identifier, saved.getId());
        assignIdIfMissing(identifierEntity);
        identifierEntities.add(identifierEntity);
      }
      aggregate.pullChildChanges();
    }

    // 4. 批量插入标识符
    if (!identifierEntities.isEmpty()) {
      identifierDao.saveAll(identifierEntities);
      log.debug("批量插入载体标识符 {} 条", identifierEntities.size());
    }
  }

  @Override
  public Set<String> findExistingIssnLs(Collection<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Set.of();
    }

    // 通过标识符表查找 ISSN-L 类型的记录
    List<VenueIdentifierEntity> identifierEntities =
        identifierDao.findByIdentifierTypeAndIdentifierValueIn(
            VenueIdentifierType.ISSN_L.name(), issnLs);

    // 提取存在的 ISSN-L 值
    return identifierEntities.stream()
        .map(VenueIdentifierEntity::getIdentifierValue)
        .collect(Collectors.toSet());
  }

  // ========== Serfile 导入支持方法 ==========

  @Override
  public Map<String, VenueAggregate> findByIssnLs(Collection<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }

    // 通过标识符表查找 ISSN-L 类型的记录
    List<VenueIdentifierEntity> identifierEntities =
        identifierDao.findByIdentifierTypeAndIdentifierValueIn(
            VenueIdentifierType.ISSN_L.name(), issnLs);

    if (identifierEntities.isEmpty()) {
      return Map.of();
    }

    // 获取 venue IDs 并构建 ISSN-L -> venueId 的映射
    Map<Long, String> venueIdToIssnL = new HashMap<>();
    Set<Long> venueIds = new HashSet<>();
    for (VenueIdentifierEntity idEntity : identifierEntities) {
      venueIds.add(idEntity.getVenueId());
      venueIdToIssnL.put(idEntity.getVenueId(), idEntity.getIdentifierValue());
    }

    // 查询主表并重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueIds);

    // 按 ISSN-L 构建 Map
    Map<Long, VenueAggregate> venueIdToAggregate =
        aggregates.stream()
            .collect(Collectors.toMap(a -> a.getId().value(), a -> a, (a1, a2) -> a1));

    Map<String, VenueAggregate> result = new HashMap<>();
    for (Map.Entry<Long, String> entry : venueIdToIssnL.entrySet()) {
      VenueAggregate aggregate = venueIdToAggregate.get(entry.getKey());
      if (aggregate != null) {
        result.put(entry.getValue(), aggregate);
      }
    }

    return result;
  }

  @Override
  public Map<String, VenueAggregate> findByNlmIds(Collection<String> nlmIds) {
    if (nlmIds == null || nlmIds.isEmpty()) {
      return Map.of();
    }

    // 通过标识符表查找 NLM 类型的记录
    List<VenueIdentifierEntity> identifierEntities =
        identifierDao.findByIdentifierTypeAndIdentifierValueIn(
            VenueIdentifierType.NLM.name(), nlmIds);

    if (identifierEntities.isEmpty()) {
      return Map.of();
    }

    // 获取 venue IDs 并构建 NLM ID -> venueId 的映射
    Map<Long, String> venueIdToNlmId = new HashMap<>();
    Set<Long> venueIds = new HashSet<>();
    for (VenueIdentifierEntity idEntity : identifierEntities) {
      venueIds.add(idEntity.getVenueId());
      venueIdToNlmId.put(idEntity.getVenueId(), idEntity.getIdentifierValue());
    }

    // 查询主表并重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueIds);

    // 按 NLM ID 构建 Map
    Map<Long, VenueAggregate> venueIdToAggregate =
        aggregates.stream()
            .collect(Collectors.toMap(a -> a.getId().value(), a -> a, (a1, a2) -> a1));

    Map<String, VenueAggregate> result = new HashMap<>();
    for (Map.Entry<Long, String> entry : venueIdToNlmId.entrySet()) {
      VenueAggregate aggregate = venueIdToAggregate.get(entry.getKey());
      if (aggregate != null) {
        result.put(entry.getValue(), aggregate);
      }
    }

    return result;
  }

  @Override
  public Map<String, VenueAggregate> findByIssns(Collection<String> issns) {
    if (issns == null || issns.isEmpty()) {
      return Map.of();
    }

    // 通过标识符表查找 ISSN 和 ISSN_L 类型的记录
    List<VenueIdentifierEntity> identifierEntities =
        identifierDao.findByTypesAndValues(
            List.of(VenueIdentifierType.ISSN.name(), VenueIdentifierType.ISSN_L.name()), issns);

    if (identifierEntities.isEmpty()) {
      return Map.of();
    }

    // 获取 venue IDs
    Set<Long> venueIds =
        identifierEntities.stream()
            .map(VenueIdentifierEntity::getVenueId)
            .collect(Collectors.toSet());

    // 查询主表并重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueIds);

    // 构建 ISSN -> Aggregate 的映射
    Map<Long, VenueAggregate> venueIdToAggregate =
        aggregates.stream()
            .collect(Collectors.toMap(a -> a.getId().value(), a -> a, (a1, a2) -> a1));

    Map<String, VenueAggregate> result = new HashMap<>();
    for (VenueIdentifierEntity identifierEntity : identifierEntities) {
      String issnValue = identifierEntity.getIdentifierValue();
      VenueAggregate aggregate = venueIdToAggregate.get(identifierEntity.getVenueId());
      if (aggregate != null && !result.containsKey(issnValue)) {
        result.put(issnValue, aggregate);
      }
    }

    return result;
  }

  @Override
  public void updateBatch(List<VenueAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    List<VenueIdentifierEntity> identifiersToInsert = new ArrayList<>();
    List<Long> identifierIdsToDelete = new ArrayList<>();
    List<VenueAggregate> dirtyAggregates = new ArrayList<>();

    for (VenueAggregate aggregate : aggregates) {
      Long venueId = aggregate.getId().value();

      // 计算标识符差异
      computeIdentifierDiff(venueId, aggregate, identifiersToInsert, identifierIdsToDelete);

      // 主表脏标记检查
      if (aggregate.isDirty()) {
        // 使用 EntityManager 获取托管实体并原地更新
        VenueEntity managed = entityManager.find(VenueEntity.class, venueId);
        if (managed != null) {
          jpaConverter.updateEntity(managed, aggregate);
          dirtyAggregates.add(aggregate);
        }
      }
    }

    // 批量操作：标识符删除
    if (!identifierIdsToDelete.isEmpty()) {
      identifierDao.deleteAllByIdInBatch(identifierIdsToDelete);
    }

    // 批量操作：标识符插入
    if (!identifiersToInsert.isEmpty()) {
      for (VenueIdentifierEntity entity : identifiersToInsert) {
        assignIdIfMissing(entity);
      }
      identifierDao.saveAll(identifiersToInsert);
    }

    log.debug(
        "批量更新载体完成: 聚合根 {} 个, 主表更新 {} 条, 标识符新增 {} 条, 标识符删除 {} 条",
        aggregates.size(),
        dirtyAggregates.size(),
        identifiersToInsert.size(),
        identifierIdsToDelete.size());

    // 所有批量操作成功后，统一清除脏标记
    dirtyAggregates.forEach(VenueAggregate::clearDirty);
  }

  // ========== 私有辅助方法 ==========

  /// 计算标识符差异（基于数据库查询）。
  private void computeIdentifierDiff(
      Long venueId,
      VenueAggregate aggregate,
      List<VenueIdentifierEntity> identifiersToInsert,
      List<Long> identifierIdsToDelete) {

    // 从数据库加载当前标识符，构建 值对象 -> 主键ID 的映射
    List<VenueIdentifierEntity> existingEntities = identifierDao.findByVenueId(venueId);
    Map<VenueIdentifier, Long> existingIdentifierMap =
        existingEntities.stream()
            .collect(
                Collectors.toMap(
                    jpaConverter::toIdentifier, VenueIdentifierEntity::getId, (a, b) -> a));

    Set<VenueIdentifier> persistedIdentifiers = existingIdentifierMap.keySet();
    Set<VenueIdentifier> currentIdentifiers = new HashSet<>(aggregate.getIdentifiers());

    // 计算需要删除的标识符（已持久化但不在当前集合）
    for (VenueIdentifier persisted : persistedIdentifiers) {
      if (!currentIdentifiers.contains(persisted)) {
        identifierIdsToDelete.add(existingIdentifierMap.get(persisted));
      }
    }

    // 计算需要新增的标识符（在当前集合但未持久化）
    for (VenueIdentifier current : currentIdentifiers) {
      if (!persistedIdentifiers.contains(current)) {
        VenueIdentifierEntity identifierEntity = jpaConverter.toIdentifierEntity(current, venueId);
        identifiersToInsert.add(identifierEntity);
      }
    }
  }

  /// 从 venue IDs 重建聚合根列表。
  private List<VenueAggregate> reconstructAggregates(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return List.of();
    }

    // 查询主表
    List<VenueEntity> venueEntities = venueDao.findByIdIn(venueIds);
    if (venueEntities.isEmpty()) {
      return List.of();
    }

    // 批量加载标识符
    Map<Long, List<VenueIdentifierEntity>> identifiersByVenueId = loadIdentifiers(venueIds);

    // 重建聚合根
    List<VenueAggregate> aggregates = new ArrayList<>(venueEntities.size());
    for (VenueEntity entity : venueEntities) {
      VenueAggregate aggregate = jpaConverter.toAggregate(entity);

      // 添加标识符
      List<VenueIdentifierEntity> identifierEntities =
          identifiersByVenueId.getOrDefault(entity.getId(), List.of());
      jpaConverter.addIdentifiersToAggregate(aggregate, identifierEntities);

      // 清空变更追踪状态
      aggregate.pullChildChanges();
      aggregate.clearDirty();

      aggregates.add(aggregate);
    }

    return aggregates;
  }

  /// 批量加载标识符。
  private Map<Long, List<VenueIdentifierEntity>> loadIdentifiers(Collection<Long> venueIds) {
    List<VenueIdentifierEntity> all = identifierDao.findByVenueIdIn(venueIds);
    return all.stream().collect(Collectors.groupingBy(VenueIdentifierEntity::getVenueId));
  }

  /// 为没有 ID 的实体分配雪花 ID。
  private void assignIdIfMissing(VenueEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 为没有 ID 的标识符实体分配雪花 ID。
  private void assignIdIfMissing(VenueIdentifierEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  // ========== Serfile 补充数据管理 ==========

  @Override
  public void replaceYearlyMetricsBatch(
      Map<Long, List<VenuePublicationStats>> yearlyMetricsByVenueId) {
    if (yearlyMetricsByVenueId == null || yearlyMetricsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(yearlyMetricsByVenueId.keySet());

    // 删除旧数据
    publicationStatsDao.deleteByVenueIdIn(venueIds);
    entityManager.flush();

    // 收集新数据
    List<VenuePublicationStatsEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<VenuePublicationStats>> entry : yearlyMetricsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenuePublicationStats stats : entry.getValue()) {
        VenuePublicationStatsEntity entity = jpaConverter.toPublicationStatsEntity(stats, venueId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, publicationStatsDao);
      log.debug("批量插入年度指标 {} 条", entities.size());
    }
  }

  @Override
  public void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId) {
    if (meshTermsByVenueId == null || meshTermsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(meshTermsByVenueId.keySet());

    // 删除旧数据
    meshDao.deleteByVenueIdIn(venueIds);
    entityManager.flush();

    // 收集新数据
    List<VenueMeshEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<VenueMesh>> entry : meshTermsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueMesh mesh : entry.getValue()) {
        VenueMeshEntity entity = jpaConverter.toMeshEntity(mesh, venueId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, meshDao);
      log.debug("批量插入 MeSH 主题词 {} 条", entities.size());
    }
  }

  @Override
  public void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId) {
    if (relationsByVenueId == null || relationsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(relationsByVenueId.keySet());

    // 删除旧数据
    relationDao.deleteByVenueIdIn(venueIds);
    entityManager.flush();

    // 收集新数据
    List<VenueRelationEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<VenueRelation>> entry : relationsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueRelation relation : entry.getValue()) {
        VenueRelationEntity entity = jpaConverter.toRelationEntity(relation, venueId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, relationDao);
      log.debug("批量插入关联关系 {} 条", entities.size());
    }
  }

  @Override
  public void replaceIndexingHistoriesBatch(
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    if (historiesByVenueId == null || historiesByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(historiesByVenueId.keySet());

    // 删除旧数据
    indexingHistoryDao.deleteByVenueIdIn(venueIds);
    entityManager.flush();

    // 收集新数据
    List<VenueIndexingHistoryEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<VenueIndexingHistory>> entry : historiesByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueIndexingHistory history : entry.getValue()) {
        VenueIndexingHistoryEntity entity = jpaConverter.toIndexingHistoryEntity(history, venueId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, indexingHistoryDao);
      log.debug("批量插入索引历史 {} 条", entities.size());
    }
  }

  // ========== 批量操作辅助方法 ==========

  /// 批量保存实体，定期 flush 以防内存溢出。
  ///
  /// @param entities 实体列表
  /// @param repository JPA Repository
  /// @param <T> 实体类型
  private <T> void batchSaveWithFlush(
      List<T> entities, org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
    for (int i = 0; i < entities.size(); i++) {
      repository.save(entities.get(i));
      if ((i + 1) % BATCH_FLUSH_SIZE == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
    // 处理剩余的实体
    if (entities.size() % BATCH_FLUSH_SIZE != 0) {
      entityManager.flush();
    }
  }
}
