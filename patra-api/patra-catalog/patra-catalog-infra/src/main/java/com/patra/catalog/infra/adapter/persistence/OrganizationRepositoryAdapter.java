package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.model.vo.organization.ExternalId;
import com.patra.catalog.domain.model.vo.organization.GeoLocation;
import com.patra.catalog.domain.model.vo.organization.OrganizationId;
import com.patra.catalog.domain.model.vo.organization.OrganizationName;
import com.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import com.patra.catalog.domain.model.vo.organization.RorId;
import com.patra.catalog.domain.port.repository.OrganizationRepository;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.OrganizationJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.OrganizationDao;
import com.patra.catalog.infra.adapter.persistence.dao.OrganizationExternalIdDao;
import com.patra.catalog.infra.adapter.persistence.dao.OrganizationLocationDao;
import com.patra.catalog.infra.adapter.persistence.dao.OrganizationNameDao;
import com.patra.catalog.infra.adapter.persistence.dao.OrganizationRelationDao;
import com.patra.catalog.infra.adapter.persistence.entity.OrganizationEntity;
import com.patra.catalog.infra.adapter.persistence.entity.OrganizationExternalIdEntity;
import com.patra.catalog.infra.adapter.persistence.entity.OrganizationLocationEntity;
import com.patra.catalog.infra.adapter.persistence.entity.OrganizationNameEntity;
import com.patra.catalog.infra.adapter.persistence.entity.OrganizationRelationEntity;
import com.patra.common.domain.ChildEntityChange;
import com.patra.starter.jpa.entity.BaseJpaEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 机构聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理 OrganizationAggregate（机构聚合根）的持久化
/// - 支持 ROR 数据批量导入和增量同步
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// - 使用 JPA `saveAll()` 配合 Hibernate 批量配置
/// - 定期 flush + clear 防止大批量操作内存溢出
/// - 子表通过 `org_id` 逻辑外键关联
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrganizationRepositoryAdapter implements OrganizationRepository {

  private static final int FLUSH_INTERVAL = 50;

  private final OrganizationDao organizationDao;
  private final OrganizationNameDao nameDao;
  private final OrganizationExternalIdDao externalIdDao;
  private final OrganizationRelationDao relationDao;
  private final OrganizationLocationDao locationDao;
  private final OrganizationJpaMapper mapper;
  private final EntityManager entityManager;

  // ========== 数据存在性检查 ==========

  @Override
  public boolean hasAnyData() {
    return organizationDao.hasAnyData();
  }

  @Override
  public boolean existsByRorId(RorId rorId) {
    return organizationDao.existsByRorId(rorId.getId());
  }

  // ========== 基本查询 ==========

  @Override
  public Optional<OrganizationAggregate> findById(OrganizationId id) {
    return organizationDao.findById(id.value()).map(this::toAggregateWithChildren);
  }

  @Override
  public Optional<OrganizationAggregate> findByRorId(RorId rorId) {
    return organizationDao.findByRorId(rorId.getId()).map(this::toAggregateWithChildren);
  }

  @Override
  public Map<RorId, OrganizationAggregate> findByRorIds(Collection<RorId> rorIds) {
    if (rorIds == null || rorIds.isEmpty()) {
      return Map.of();
    }

    List<String> rorIdStrings = rorIds.stream().map(RorId::getId).toList();
    List<OrganizationEntity> entities = organizationDao.findByRorIdIn(rorIdStrings);

    if (entities.isEmpty()) {
      return Map.of();
    }

    // 批量加载子表数据
    List<Long> orgIds = entities.stream().map(OrganizationEntity::getId).toList();
    Map<Long, List<OrganizationNameEntity>> namesByOrgId = loadNamesGroupByOrgId(orgIds);
    Map<Long, List<OrganizationExternalIdEntity>> extIdsByOrgId =
        loadExternalIdsGroupByOrgId(orgIds);
    Map<Long, List<OrganizationRelationEntity>> relationsByOrgId =
        loadRelationsGroupByOrgId(orgIds);
    Map<Long, List<OrganizationLocationEntity>> locationsByOrgId =
        loadLocationsGroupByOrgId(orgIds);

    // 转换为聚合根映射
    Map<RorId, OrganizationAggregate> result = new HashMap<>();
    for (OrganizationEntity entity : entities) {
      OrganizationAggregate aggregate = mapper.toAggregate(entity);
      Long orgId = entity.getId();

      mapper.addNamesToAggregate(aggregate, namesByOrgId.getOrDefault(orgId, List.of()));
      mapper.addExternalIdsToAggregate(aggregate, extIdsByOrgId.getOrDefault(orgId, List.of()));
      mapper.addRelationsToAggregate(aggregate, relationsByOrgId.getOrDefault(orgId, List.of()));
      mapper.addLocationsToAggregate(aggregate, locationsByOrgId.getOrDefault(orgId, List.of()));
      aggregate.clearDirty();

      result.put(RorId.of(entity.getRorId()), aggregate);
    }

    return result;
  }

  @Override
  public Map<String, Long> findIdsByRorIds(Collection<String> rorIds) {
    if (rorIds == null || rorIds.isEmpty()) {
      return Map.of();
    }

    List<OrganizationEntity> entities = organizationDao.findByRorIdIn(rorIds);
    return entities.stream()
        .collect(Collectors.toMap(OrganizationEntity::getRorId, OrganizationEntity::getId));
  }

  // ========== 批量写入 ==========

  @Override
  public void insertAll(List<OrganizationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换主表实体并分配 ID
    List<OrganizationEntity> orgEntities = new ArrayList<>(aggregates.size());
    for (OrganizationAggregate aggregate : aggregates) {
      OrganizationEntity entity = mapper.toEntity(aggregate);
      assignIdIfMissing(entity);
      orgEntities.add(entity);
    }

    // 2. 收集子表数据
    List<OrganizationNameEntity> nameEntities = new ArrayList<>();
    List<OrganizationExternalIdEntity> extIdEntities = new ArrayList<>();
    List<OrganizationRelationEntity> relationEntities = new ArrayList<>();
    List<OrganizationLocationEntity> locationEntities = new ArrayList<>();

    for (int i = 0; i < aggregates.size(); i++) {
      OrganizationAggregate aggregate = aggregates.get(i);
      Long orgId = orgEntities.get(i).getId();
      collectChildData(
          aggregate, orgId, nameEntities, extIdEntities, relationEntities, locationEntities);
    }

    // 3. 批量插入主表
    saveBatchWithFlush(orgEntities, "机构");

    // 4. 批量插入子表
    if (!nameEntities.isEmpty()) {
      saveBatchWithFlush(nameEntities, "名称");
    }
    if (!extIdEntities.isEmpty()) {
      saveBatchWithFlush(extIdEntities, "外部标识符");
    }
    if (!relationEntities.isEmpty()) {
      saveBatchWithFlush(relationEntities, "关系");
    }
    if (!locationEntities.isEmpty()) {
      saveBatchWithFlush(locationEntities, "地理位置");
    }

    log.info(
        "批量插入机构 {} 条（名称 {}，外部ID {}，关系 {}，位置 {}）",
        orgEntities.size(),
        nameEntities.size(),
        extIdEntities.size(),
        relationEntities.size(),
        locationEntities.size());
  }

  @Override
  public Set<String> findExistingRorIds(Collection<String> rorIds) {
    if (rorIds == null || rorIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(organizationDao.findExistingRorIds(rorIds));
  }

  // ========== 单条保存/更新 ==========

  @Override
  public OrganizationAggregate save(OrganizationAggregate aggregate) {
    if (aggregate == null) {
      return null;
    }

    // 转换并保存主表
    OrganizationEntity entity = mapper.toEntity(aggregate);
    assignIdIfMissing(entity);
    OrganizationEntity savedEntity = organizationDao.save(entity);
    Long orgId = savedEntity.getId();

    // 保存子表（简单策略：删除旧数据，插入新数据）
    saveChildren(aggregate, orgId);

    // 重新加载并返回
    return findById(OrganizationId.of(orgId)).orElse(null);
  }

  // ========== 批量更新 ==========

  @Override
  public void updateBatch(List<OrganizationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    int updatedCount = 0;
    int childChangesCount = 0;

    for (OrganizationAggregate aggregate : aggregates) {
      if (aggregate.getId() == null) {
        continue;
      }

      Long orgId = aggregate.getId().value();

      // 更新主表（仅脏数据）
      if (aggregate.isDirty()) {
        OrganizationEntity entity = mapper.toEntity(aggregate);
        organizationDao.save(entity);
        updatedCount++;
      }

      // 处理子表变更
      List<ChildEntityChange> changes = aggregate.pullChildChanges();
      if (!changes.isEmpty()) {
        applyChildChanges(orgId, changes);
        childChangesCount += changes.size();
      }
    }

    if (updatedCount > 0 || childChangesCount > 0) {
      log.debug("批量更新机构：主表 {} 条，子表变更 {} 条", updatedCount, childChangesCount);
    }
  }

  // ========== 关系延迟填充 ==========

  @Override
  public int linkRelatedOrganizations(Map<String, Long> rorIdToOrgId) {
    if (rorIdToOrgId == null || rorIdToOrgId.isEmpty()) {
      return 0;
    }

    int totalUpdated = 0;
    for (Map.Entry<String, Long> entry : rorIdToOrgId.entrySet()) {
      int updated = relationDao.linkByRorId(entry.getKey(), entry.getValue());
      totalUpdated += updated;
    }

    log.info("关联机构关系：共更新 {} 条记录", totalUpdated);
    return totalUpdated;
  }

  // ========== 统计查询 ==========

  @Override
  public long count() {
    return organizationDao.count();
  }

  @Override
  public Map<String, Long> countByStatus() {
    List<Object[]> results = organizationDao.countByStatus();
    return results.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
  }

  // ========== 私有辅助方法 ==========

  /// 将实体转换为聚合根并加载子表数据。
  private OrganizationAggregate toAggregateWithChildren(OrganizationEntity entity) {
    OrganizationAggregate aggregate = mapper.toAggregate(entity);
    Long orgId = entity.getId();

    mapper.addNamesToAggregate(aggregate, nameDao.findAllByOrgId(orgId));
    mapper.addExternalIdsToAggregate(aggregate, externalIdDao.findAllByOrgId(orgId));
    mapper.addRelationsToAggregate(aggregate, relationDao.findAllByOrgId(orgId));
    mapper.addLocationsToAggregate(aggregate, locationDao.findAllByOrgId(orgId));
    aggregate.clearDirty();

    return aggregate;
  }

  /// 收集聚合根的子表数据。
  private void collectChildData(
      OrganizationAggregate aggregate,
      Long orgId,
      List<OrganizationNameEntity> nameEntities,
      List<OrganizationExternalIdEntity> extIdEntities,
      List<OrganizationRelationEntity> relationEntities,
      List<OrganizationLocationEntity> locationEntities) {

    for (OrganizationName name : aggregate.getNames()) {
      OrganizationNameEntity entity = mapper.toNameEntity(name, orgId);
      assignIdIfMissing(entity);
      nameEntities.add(entity);
    }

    for (ExternalId extId : aggregate.getExternalIds()) {
      OrganizationExternalIdEntity entity = mapper.toExternalIdEntity(extId, orgId);
      assignIdIfMissing(entity);
      extIdEntities.add(entity);
    }

    for (OrganizationRelation relation : aggregate.getRelations()) {
      OrganizationRelationEntity entity = mapper.toRelationEntity(relation, orgId);
      assignIdIfMissing(entity);
      relationEntities.add(entity);
    }

    for (GeoLocation location : aggregate.getLocations()) {
      OrganizationLocationEntity entity = mapper.toLocationEntity(location, orgId);
      assignIdIfMissing(entity);
      locationEntities.add(entity);
    }
  }

  /// 保存子表数据（删除旧数据，插入新数据）。
  private void saveChildren(OrganizationAggregate aggregate, Long orgId) {
    // 删除旧数据
    nameDao.deleteAllByOrgId(orgId);
    externalIdDao.deleteAllByOrgId(orgId);
    relationDao.deleteAllByOrgId(orgId);
    locationDao.deleteAllByOrgId(orgId);

    // 插入新数据
    List<OrganizationNameEntity> nameEntities = new ArrayList<>();
    List<OrganizationExternalIdEntity> extIdEntities = new ArrayList<>();
    List<OrganizationRelationEntity> relationEntities = new ArrayList<>();
    List<OrganizationLocationEntity> locationEntities = new ArrayList<>();

    collectChildData(
        aggregate, orgId, nameEntities, extIdEntities, relationEntities, locationEntities);

    if (!nameEntities.isEmpty()) {
      nameDao.saveAll(nameEntities);
    }
    if (!extIdEntities.isEmpty()) {
      externalIdDao.saveAll(extIdEntities);
    }
    if (!relationEntities.isEmpty()) {
      relationDao.saveAll(relationEntities);
    }
    if (!locationEntities.isEmpty()) {
      locationDao.saveAll(locationEntities);
    }
  }

  /// 应用子实体变更。
  ///
  /// 根据变更事件的实体类型分发到对应的处理逻辑。
  ///
  /// @param orgId 机构 ID
  /// @param changes 子实体变更事件列表
  @SuppressWarnings("unchecked")
  private void applyChildChanges(Long orgId, List<ChildEntityChange> changes) {
    for (ChildEntityChange change : changes) {
      Class<?> entityType = change.entityType();

      if (entityType == OrganizationName.class) {
        applyNameChange(orgId, change);
      } else if (entityType == ExternalId.class) {
        applyExternalIdChange(orgId, change);
      } else if (entityType == OrganizationRelation.class) {
        applyRelationChange(orgId, change);
      } else if (entityType == GeoLocation.class) {
        applyLocationChange(orgId, change);
      } else {
        log.warn("未知的子实体类型: {}", entityType.getName());
      }
    }
  }

  /// 应用名称子表变更。
  @SuppressWarnings("unchecked")
  private void applyNameChange(Long orgId, ChildEntityChange change) {
    switch (change) {
      case ChildEntityChange.Added<?> added -> {
        OrganizationName name = (OrganizationName) added.entity();
        OrganizationNameEntity entity = mapper.toNameEntity(name, orgId);
        assignIdIfMissing(entity);
        nameDao.save(entity);
      }
      case ChildEntityChange.Updated<?> updated -> {
        OrganizationName name = (OrganizationName) updated.entity();
        OrganizationNameEntity entity = mapper.toNameEntity(name, orgId);
        nameDao.save(entity);
      }
      case ChildEntityChange.Removed<?> removed -> {
        Object entityId = removed.entityId();
        if (entityId instanceof OrganizationName name && name.id() != null) {
          nameDao.deleteById(name.id());
        } else if (entityId instanceof Long id) {
          nameDao.deleteById(id);
        }
      }
    }
  }

  /// 应用外部标识符子表变更。
  @SuppressWarnings("unchecked")
  private void applyExternalIdChange(Long orgId, ChildEntityChange change) {
    switch (change) {
      case ChildEntityChange.Added<?> added -> {
        ExternalId extId = (ExternalId) added.entity();
        OrganizationExternalIdEntity entity = mapper.toExternalIdEntity(extId, orgId);
        assignIdIfMissing(entity);
        externalIdDao.save(entity);
      }
      case ChildEntityChange.Updated<?> updated -> {
        ExternalId extId = (ExternalId) updated.entity();
        OrganizationExternalIdEntity entity = mapper.toExternalIdEntity(extId, orgId);
        externalIdDao.save(entity);
      }
      case ChildEntityChange.Removed<?> removed -> {
        Object entityId = removed.entityId();
        if (entityId instanceof ExternalId extId && extId.id() != null) {
          externalIdDao.deleteById(extId.id());
        } else if (entityId instanceof Long id) {
          externalIdDao.deleteById(id);
        }
      }
    }
  }

  /// 应用关系子表变更。
  @SuppressWarnings("unchecked")
  private void applyRelationChange(Long orgId, ChildEntityChange change) {
    switch (change) {
      case ChildEntityChange.Added<?> added -> {
        OrganizationRelation relation = (OrganizationRelation) added.entity();
        OrganizationRelationEntity entity = mapper.toRelationEntity(relation, orgId);
        assignIdIfMissing(entity);
        relationDao.save(entity);
      }
      case ChildEntityChange.Updated<?> updated -> {
        OrganizationRelation relation = (OrganizationRelation) updated.entity();
        OrganizationRelationEntity entity = mapper.toRelationEntity(relation, orgId);
        relationDao.save(entity);
      }
      case ChildEntityChange.Removed<?> removed -> {
        Object entityId = removed.entityId();
        if (entityId instanceof OrganizationRelation relation && relation.id() != null) {
          relationDao.deleteById(relation.id());
        } else if (entityId instanceof Long id) {
          relationDao.deleteById(id);
        }
      }
    }
  }

  /// 应用地理位置子表变更。
  @SuppressWarnings("unchecked")
  private void applyLocationChange(Long orgId, ChildEntityChange change) {
    switch (change) {
      case ChildEntityChange.Added<?> added -> {
        GeoLocation location = (GeoLocation) added.entity();
        OrganizationLocationEntity entity = mapper.toLocationEntity(location, orgId);
        assignIdIfMissing(entity);
        locationDao.save(entity);
      }
      case ChildEntityChange.Updated<?> updated -> {
        GeoLocation location = (GeoLocation) updated.entity();
        OrganizationLocationEntity entity = mapper.toLocationEntity(location, orgId);
        locationDao.save(entity);
      }
      case ChildEntityChange.Removed<?> removed -> {
        Object entityId = removed.entityId();
        if (entityId instanceof GeoLocation location && location.id() != null) {
          locationDao.deleteById(location.id());
        } else if (entityId instanceof Long id) {
          locationDao.deleteById(id);
        }
      }
    }
  }

  /// 批量加载名称并按机构 ID 分组。
  private Map<Long, List<OrganizationNameEntity>> loadNamesGroupByOrgId(List<Long> orgIds) {
    return nameDao.findAllByOrgIdIn(orgIds).stream()
        .collect(Collectors.groupingBy(OrganizationNameEntity::getOrgId));
  }

  /// 批量加载外部标识符并按机构 ID 分组。
  private Map<Long, List<OrganizationExternalIdEntity>> loadExternalIdsGroupByOrgId(
      List<Long> orgIds) {
    return externalIdDao.findAllByOrgIdIn(orgIds).stream()
        .collect(Collectors.groupingBy(OrganizationExternalIdEntity::getOrgId));
  }

  /// 批量加载关系并按机构 ID 分组。
  private Map<Long, List<OrganizationRelationEntity>> loadRelationsGroupByOrgId(List<Long> orgIds) {
    return relationDao.findAllByOrgIdIn(orgIds).stream()
        .collect(Collectors.groupingBy(OrganizationRelationEntity::getOrgId));
  }

  /// 批量加载地理位置并按机构 ID 分组。
  private Map<Long, List<OrganizationLocationEntity>> loadLocationsGroupByOrgId(List<Long> orgIds) {
    return locationDao.findAllByOrgIdIn(orgIds).stream()
        .collect(Collectors.groupingBy(OrganizationLocationEntity::getOrgId));
  }

  /// 批量保存实体，定期 flush + clear 防止内存溢出。
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
  private void assignIdIfMissing(BaseJpaEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
