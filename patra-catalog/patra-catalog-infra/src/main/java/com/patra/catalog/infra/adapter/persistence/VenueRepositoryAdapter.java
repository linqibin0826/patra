package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.entity.VenueIndexingHistory;
import com.patra.catalog.domain.model.entity.VenueMesh;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.model.entity.VenueRelation;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.converter.VenueIdentifierConverter;
import com.patra.catalog.infra.persistence.converter.VenuePublicationStatsConverter;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenueIndexingHistoryDO;
import com.patra.catalog.infra.persistence.entity.VenueMeshDO;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import com.patra.catalog.infra.persistence.entity.VenueRelationDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueIndexingHistoryMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMeshMapper;
import com.patra.catalog.infra.persistence.mapper.VenuePublicationStatsMapper;
import com.patra.catalog.infra.persistence.mapper.VenueRelationMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 出版载体聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 VenueAggregate（载体聚合根）的持久化
/// - 支持 OpenAlex Sources 和 NLM Serfile 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// - 批量操作使用 `insertBatchSomeColumn` 提升写入效率
/// - 批量查询时一次性加载所有子实体，避免 N+1 问题
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

  private final VenueMapper venueMapper;
  private final VenueIdentifierMapper venueIdentifierMapper;
  private final VenuePublicationStatsMapper venuePublicationStatsMapper;
  private final VenueMeshMapper venueMeshMapper;
  private final VenueRelationMapper venueRelationMapper;
  private final VenueIndexingHistoryMapper venueIndexingHistoryMapper;
  private final VenueConverter venueConverter;
  private final VenueIdentifierConverter identifierConverter;
  private final VenuePublicationStatsConverter metricsConverter;

  @Override
  public boolean hasAnyData() {
    return venueMapper.selectCount(null) > 0;
  }

  @Override
  public void insertAll(List<VenueAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    List<VenueDO> venueDOs = new ArrayList<>(aggregates.size());
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    List<VenuePublicationStatsDO> metricsDOs = new ArrayList<>();
    List<VenueMeshDO> meshDOs = new ArrayList<>();
    List<VenueRelationDO> relationDOs = new ArrayList<>();
    List<VenueIndexingHistoryDO> historyDOs = new ArrayList<>();

    for (VenueAggregate aggregate : aggregates) {
      // 生成 ID
      Long venueId = IdWorker.getId();

      // 转换主表
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(venueId);
      venueDOs.add(venueDO);

      // 收集子表数据
      collectChildData(
          aggregate, venueId, identifierDOs, metricsDOs, meshDOs, relationDOs, historyDOs);
    }

    // 批量插入主表
    venueMapper.insertBatchSomeColumn(venueDOs);
    log.debug("批量插入载体 {} 条", venueDOs.size());

    // 批量插入子表
    batchInsertChildTables(identifierDOs, metricsDOs, meshDOs, relationDOs, historyDOs);
  }

  @Override
  public Set<String> findExistingIssnLs(Collection<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Set.of();
    }

    return venueMapper
        .selectList(
            new LambdaQueryWrapper<VenueDO>()
                .select(VenueDO::getIssnL)
                .in(VenueDO::getIssnL, issnLs))
        .stream()
        .map(VenueDO::getIssnL)
        .collect(Collectors.toSet());
  }

  // ========== Serfile 导入支持方法 ==========

  @Override
  public Map<String, VenueAggregate> findByIssnLs(Collection<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }

    // 查询主表
    List<VenueDO> venueDOs =
        venueMapper.selectList(new LambdaQueryWrapper<VenueDO>().in(VenueDO::getIssnL, issnLs));

    if (venueDOs.isEmpty()) {
      return Map.of();
    }

    // 重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueDOs);

    // 按 ISSN-L 构建 Map
    return aggregates.stream()
        .filter(a -> a.getIssnL() != null)
        .collect(Collectors.toMap(VenueAggregate::getIssnL, a -> a, (a1, a2) -> a1));
  }

  @Override
  public Map<String, VenueAggregate> findByNlmIds(Collection<String> nlmIds) {
    if (nlmIds == null || nlmIds.isEmpty()) {
      return Map.of();
    }

    // 查询主表
    List<VenueDO> venueDOs =
        venueMapper.selectList(new LambdaQueryWrapper<VenueDO>().in(VenueDO::getNlmId, nlmIds));

    if (venueDOs.isEmpty()) {
      return Map.of();
    }

    // 重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueDOs);

    // 按 NLM ID 构建 Map
    return aggregates.stream()
        .filter(a -> a.getNlmId() != null)
        .collect(Collectors.toMap(VenueAggregate::getNlmId, a -> a, (a1, a2) -> a1));
  }

  @Override
  public Map<String, VenueAggregate> findByIssns(Collection<String> issns) {
    if (issns == null || issns.isEmpty()) {
      return Map.of();
    }

    // 通过标识符表查找 ISSN 类型的记录
    // VenueIdentifierType.ISSN 和 ISSN_L 都是有效的 ISSN 标识符
    List<VenueIdentifierDO> identifierDOs =
        venueIdentifierMapper.selectList(
            new LambdaQueryWrapper<VenueIdentifierDO>()
                .in(
                    VenueIdentifierDO::getIdentifierType,
                    VenueIdentifierType.ISSN.name(),
                    VenueIdentifierType.ISSN_L.name())
                .in(VenueIdentifierDO::getIdentifierValue, issns));

    if (identifierDOs.isEmpty()) {
      return Map.of();
    }

    // 获取 venue IDs
    Set<Long> venueIds =
        identifierDOs.stream().map(VenueIdentifierDO::getVenueId).collect(Collectors.toSet());

    // 查询主表
    List<VenueDO> venueDOs = venueMapper.selectBatchIds(venueIds);

    if (venueDOs.isEmpty()) {
      return Map.of();
    }

    // 重建聚合根
    List<VenueAggregate> aggregates = reconstructAggregates(venueDOs);

    // 构建 ISSN -> Aggregate 的映射
    // 一个 Aggregate 可能有多个 ISSN，需要建立多对一关系
    Map<Long, VenueAggregate> venueIdToAggregate =
        aggregates.stream().collect(Collectors.toMap(a -> a.getId(), a -> a, (a1, a2) -> a1));

    Map<String, VenueAggregate> result = new HashMap<>();
    for (VenueIdentifierDO identifierDO : identifierDOs) {
      String issnValue = identifierDO.getIdentifierValue();
      VenueAggregate aggregate = venueIdToAggregate.get(identifierDO.getVenueId());
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

    List<Long> venueIds = aggregates.stream().map(VenueAggregate::getId).toList();

    // 删除旧的子实体
    deleteChildEntities(venueIds);

    // 收集新的子实体数据
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    List<VenuePublicationStatsDO> metricsDOs = new ArrayList<>();
    List<VenueMeshDO> meshDOs = new ArrayList<>();
    List<VenueRelationDO> relationDOs = new ArrayList<>();
    List<VenueIndexingHistoryDO> historyDOs = new ArrayList<>();

    for (VenueAggregate aggregate : aggregates) {
      // 更新主表
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(aggregate.getId());
      venueMapper.updateById(venueDO);

      // 收集子表数据
      collectChildData(
          aggregate,
          aggregate.getId(),
          identifierDOs,
          metricsDOs,
          meshDOs,
          relationDOs,
          historyDOs);
    }

    log.debug("批量更新载体 {} 条", aggregates.size());

    // 批量插入新的子实体
    batchInsertChildTables(identifierDOs, metricsDOs, meshDOs, relationDOs, historyDOs);
  }

  // ========== 私有辅助方法 ==========

  /// 收集聚合根的所有子表数据。
  ///
  /// @param aggregate 聚合根
  /// @param venueId 主表 ID
  /// @param identifierDOs 标识符 DO 列表（输出参数）
  /// @param metricsDOs 年度指标 DO 列表（输出参数）
  /// @param meshDOs MeSH 主题词 DO 列表（输出参数）
  /// @param relationDOs 关联关系 DO 列表（输出参数）
  /// @param historyDOs 索引历史 DO 列表（输出参数）
  private void collectChildData(
      VenueAggregate aggregate,
      Long venueId,
      List<VenueIdentifierDO> identifierDOs,
      List<VenuePublicationStatsDO> metricsDOs,
      List<VenueMeshDO> meshDOs,
      List<VenueRelationDO> relationDOs,
      List<VenueIndexingHistoryDO> historyDOs) {

    // 收集标识符
    for (VenueIdentifier identifier : aggregate.getIdentifiers()) {
      VenueIdentifierDO identifierDO = identifierConverter.toDO(identifier);
      identifierDO.setId(IdWorker.getId());
      identifierDO.setVenueId(venueId);
      identifierDOs.add(identifierDO);
    }

    // 收集年度指标
    for (VenuePublicationStats metrics : aggregate.getYearlyMetrics()) {
      VenuePublicationStatsDO metricsDO = metricsConverter.toDO(metrics);
      metricsDO.setId(IdWorker.getId());
      metricsDO.setVenueId(venueId);
      metricsDOs.add(metricsDO);
    }

    // 收集 MeSH 主题词
    for (VenueMesh mesh : aggregate.getMeshTerms()) {
      VenueMeshDO meshDO = toMeshDO(mesh, venueId);
      meshDOs.add(meshDO);
    }

    // 收集关联关系
    for (VenueRelation relation : aggregate.getRelations()) {
      VenueRelationDO relationDO = toRelationDO(relation, venueId);
      relationDOs.add(relationDO);
    }

    // 收集索引历史
    for (VenueIndexingHistory history : aggregate.getIndexingHistories()) {
      VenueIndexingHistoryDO historyDO = toIndexingHistoryDO(history, venueId);
      historyDOs.add(historyDO);
    }
  }

  /// 批量插入子表数据。
  private void batchInsertChildTables(
      List<VenueIdentifierDO> identifierDOs,
      List<VenuePublicationStatsDO> metricsDOs,
      List<VenueMeshDO> meshDOs,
      List<VenueRelationDO> relationDOs,
      List<VenueIndexingHistoryDO> historyDOs) {

    if (!identifierDOs.isEmpty()) {
      venueIdentifierMapper.insertBatchSomeColumn(identifierDOs);
      log.debug("批量插入载体标识符 {} 条", identifierDOs.size());
    }
    if (!metricsDOs.isEmpty()) {
      venuePublicationStatsMapper.insertBatchSomeColumn(metricsDOs);
      log.debug("批量插入载体年度指标 {} 条", metricsDOs.size());
    }
    if (!meshDOs.isEmpty()) {
      venueMeshMapper.insertBatchSomeColumn(meshDOs);
      log.debug("批量插入载体 MeSH 主题词 {} 条", meshDOs.size());
    }
    if (!relationDOs.isEmpty()) {
      venueRelationMapper.insertBatchSomeColumn(relationDOs);
      log.debug("批量插入载体关联关系 {} 条", relationDOs.size());
    }
    if (!historyDOs.isEmpty()) {
      venueIndexingHistoryMapper.insertBatchSomeColumn(historyDOs);
      log.debug("批量插入载体索引历史 {} 条", historyDOs.size());
    }
  }

  /// 删除指定载体的所有子实体。
  private void deleteChildEntities(List<Long> venueIds) {
    venueIdentifierMapper.delete(
        new LambdaQueryWrapper<VenueIdentifierDO>().in(VenueIdentifierDO::getVenueId, venueIds));
    venuePublicationStatsMapper.delete(
        new LambdaQueryWrapper<VenuePublicationStatsDO>()
            .in(VenuePublicationStatsDO::getVenueId, venueIds));
    venueMeshMapper.delete(
        new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));
    venueRelationMapper.delete(
        new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));
    venueIndexingHistoryMapper.delete(
        new LambdaQueryWrapper<VenueIndexingHistoryDO>()
            .in(VenueIndexingHistoryDO::getVenueId, venueIds));

    log.debug("删除载体子实体，venueIds: {}", venueIds.size());
  }

  /// 从 DO 列表重建聚合根列表。
  ///
  /// 批量加载所有子实体，避免 N+1 问题。
  ///
  /// @param venueDOs 主表 DO 列表
  /// @return 重建的聚合根列表
  private List<VenueAggregate> reconstructAggregates(List<VenueDO> venueDOs) {
    List<Long> venueIds = venueDOs.stream().map(VenueDO::getId).toList();

    // 批量加载所有子实体
    Map<Long, List<VenueIdentifierDO>> identifiersByVenueId = loadIdentifiers(venueIds);
    Map<Long, List<VenuePublicationStatsDO>> metricsByVenueId = loadMetrics(venueIds);
    Map<Long, List<VenueMeshDO>> meshByVenueId = loadMeshTerms(venueIds);
    Map<Long, List<VenueRelationDO>> relationsByVenueId = loadRelations(venueIds);
    Map<Long, List<VenueIndexingHistoryDO>> historiesByVenueId = loadIndexingHistories(venueIds);

    // 重建聚合根
    List<VenueAggregate> aggregates = new ArrayList<>(venueDOs.size());
    for (VenueDO venueDO : venueDOs) {
      VenueAggregate aggregate =
          reconstructAggregate(
              venueDO,
              identifiersByVenueId.getOrDefault(venueDO.getId(), List.of()),
              metricsByVenueId.getOrDefault(venueDO.getId(), List.of()),
              meshByVenueId.getOrDefault(venueDO.getId(), List.of()),
              relationsByVenueId.getOrDefault(venueDO.getId(), List.of()),
              historiesByVenueId.getOrDefault(venueDO.getId(), List.of()));
      aggregates.add(aggregate);
    }

    return aggregates;
  }

  /// 重建单个聚合根。
  private VenueAggregate reconstructAggregate(
      VenueDO venueDO,
      List<VenueIdentifierDO> identifierDOs,
      List<VenuePublicationStatsDO> metricsDOs,
      List<VenueMeshDO> meshDOs,
      List<VenueRelationDO> relationDOs,
      List<VenueIndexingHistoryDO> historyDOs) {

    VenueType venueType = VenueType.fromCode(venueDO.getVenueType());
    VenueAggregate aggregate =
        VenueAggregate.restore(
            venueDO.getId(), venueType, venueDO.getDisplayName(), venueDO.getVersion());

    // 设置基本属性
    aggregate
        .withAbbreviatedTitle(venueDO.getAbbreviatedTitle())
        .withHomepageUrl(venueDO.getHomepageUrl())
        .withOpenalexId(venueDO.getOpenalexId())
        .withIssnL(venueDO.getIssnL())
        .withNlmId(venueDO.getNlmId())
        .withDoiPrefix(venueDO.getDoiPrefix())
        .withCoden(venueDO.getCoden())
        .withFrequency(venueDO.getFrequency())
        .withPrimaryLanguage(venueDO.getPrimaryLanguage())
        .withPublisher(venueDO.getPublisher())
        .withCountryCode(venueDO.getCountryCode())
        .withOaType(venueDO.getOaType());

    // 设置 OA 状态
    aggregate.withOaStatus(
        Boolean.TRUE.equals(venueDO.getIsOa()), Boolean.TRUE.equals(venueDO.getIsInDoaj()), false);

    // 添加子实体
    for (VenueIdentifierDO identifierDO : identifierDOs) {
      VenueIdentifier identifier = toIdentifierEntity(identifierDO);
      aggregate.addIdentifier(identifier);
    }

    for (VenuePublicationStatsDO metricsDO : metricsDOs) {
      VenuePublicationStats metrics = toMetricsEntity(metricsDO);
      aggregate.addMetrics(metrics);
    }

    for (VenueMeshDO meshDO : meshDOs) {
      VenueMesh mesh = toMeshEntity(meshDO);
      aggregate.addMeshTerm(mesh);
    }

    for (VenueRelationDO relationDO : relationDOs) {
      VenueRelation relation = toRelationEntity(relationDO);
      aggregate.addRelation(relation);
    }

    for (VenueIndexingHistoryDO historyDO : historyDOs) {
      VenueIndexingHistory history = toIndexingHistoryEntity(historyDO);
      aggregate.addIndexingHistory(history);
    }

    return aggregate;
  }

  // ========== 子实体加载方法 ==========

  private Map<Long, List<VenueIdentifierDO>> loadIdentifiers(List<Long> venueIds) {
    List<VenueIdentifierDO> all =
        venueIdentifierMapper.selectList(
            new LambdaQueryWrapper<VenueIdentifierDO>()
                .in(VenueIdentifierDO::getVenueId, venueIds));
    return all.stream().collect(Collectors.groupingBy(VenueIdentifierDO::getVenueId));
  }

  private Map<Long, List<VenuePublicationStatsDO>> loadMetrics(List<Long> venueIds) {
    List<VenuePublicationStatsDO> all =
        venuePublicationStatsMapper.selectList(
            new LambdaQueryWrapper<VenuePublicationStatsDO>()
                .in(VenuePublicationStatsDO::getVenueId, venueIds));
    return all.stream().collect(Collectors.groupingBy(VenuePublicationStatsDO::getVenueId));
  }

  private Map<Long, List<VenueMeshDO>> loadMeshTerms(List<Long> venueIds) {
    List<VenueMeshDO> all =
        venueMeshMapper.selectList(
            new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));
    return all.stream().collect(Collectors.groupingBy(VenueMeshDO::getVenueId));
  }

  private Map<Long, List<VenueRelationDO>> loadRelations(List<Long> venueIds) {
    List<VenueRelationDO> all =
        venueRelationMapper.selectList(
            new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));
    return all.stream().collect(Collectors.groupingBy(VenueRelationDO::getVenueId));
  }

  private Map<Long, List<VenueIndexingHistoryDO>> loadIndexingHistories(List<Long> venueIds) {
    List<VenueIndexingHistoryDO> all =
        venueIndexingHistoryMapper.selectList(
            new LambdaQueryWrapper<VenueIndexingHistoryDO>()
                .in(VenueIndexingHistoryDO::getVenueId, venueIds));
    return all.stream().collect(Collectors.groupingBy(VenueIndexingHistoryDO::getVenueId));
  }

  // ========== DO → Entity 转换方法 ==========

  private VenueIdentifier toIdentifierEntity(VenueIdentifierDO doEntity) {
    return VenueIdentifier.restore(
        doEntity.getId(),
        VenueIdentifierType.valueOf(doEntity.getIdentifierType()),
        doEntity.getIdentifierValue(),
        Boolean.TRUE.equals(doEntity.getIsPrimary()));
  }

  private VenuePublicationStats toMetricsEntity(VenuePublicationStatsDO doEntity) {
    return VenuePublicationStats.restore(
        doEntity.getId(),
        doEntity.getYear().intValue(),
        doEntity.getWorksCount() != null ? doEntity.getWorksCount() : 0,
        doEntity.getCitedByCount() != null ? doEntity.getCitedByCount() : 0,
        doEntity.getOaWorksCount());
  }

  private VenueMesh toMeshEntity(VenueMeshDO doEntity) {
    return VenueMesh.restore(
        doEntity.getId(),
        doEntity.getDescriptorName(),
        doEntity.getDescriptorUi(),
        Boolean.TRUE.equals(doEntity.getIsMajorTopic()),
        doEntity.getQualifierName(),
        doEntity.getQualifierUi());
  }

  private VenueRelation toRelationEntity(VenueRelationDO doEntity) {
    VenueRelationType relationType = VenueRelationType.fromCodeOrNull(doEntity.getRelationType());
    if (relationType == null) {
      relationType = VenueRelationType.PRECEDING; // 默认值
    }
    return VenueRelation.restore(
        doEntity.getId(),
        doEntity.getRelatedVenueId(),
        doEntity.getRelatedNlmId(),
        doEntity.getRelatedTitle(),
        relationType,
        doEntity.getEffectiveDate(),
        doEntity.getNotes());
  }

  private VenueIndexingHistory toIndexingHistoryEntity(VenueIndexingHistoryDO doEntity) {
    IndexingTreatment treatment =
        doEntity.getIndexingTreatment() != null
            ? IndexingTreatment.fromCodeOrNull(doEntity.getIndexingTreatment())
            : null;
    CitationSubset subset =
        doEntity.getCitationSubset() != null
            ? CitationSubset.fromCodeOrNull(doEntity.getCitationSubset())
            : null;

    return VenueIndexingHistory.restore(
        doEntity.getId(),
        doEntity.getIndexingSource(),
        Boolean.TRUE.equals(doEntity.getCurrentlyIndexed()),
        treatment,
        subset,
        doEntity.getStartYear(),
        doEntity.getStartVolume(),
        doEntity.getStartIssue(),
        doEntity.getEndYear(),
        doEntity.getEndVolume(),
        doEntity.getEndIssue());
  }

  // ========== Entity → DO 转换方法 ==========

  private VenueMeshDO toMeshDO(VenueMesh entity, Long venueId) {
    VenueMeshDO doEntity = new VenueMeshDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setDescriptorName(entity.getDescriptorName());
    doEntity.setDescriptorUi(entity.getDescriptorUi());
    doEntity.setIsMajorTopic(entity.isMajorTopic());
    doEntity.setQualifierName(entity.getQualifierName());
    doEntity.setQualifierUi(entity.getQualifierUi());
    return doEntity;
  }

  private VenueRelationDO toRelationDO(VenueRelation entity, Long venueId) {
    VenueRelationDO doEntity = new VenueRelationDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setRelatedVenueId(entity.getRelatedVenueId());
    doEntity.setRelatedNlmId(entity.getRelatedNlmId());
    doEntity.setRelatedTitle(entity.getRelatedTitle());
    doEntity.setRelationType(entity.getRelationType().getCode());
    doEntity.setEffectiveDate(entity.getEffectiveDate());
    doEntity.setNotes(entity.getNotes());
    return doEntity;
  }

  private VenueIndexingHistoryDO toIndexingHistoryDO(VenueIndexingHistory entity, Long venueId) {
    VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setIndexingSource(entity.getIndexingSource());
    doEntity.setCurrentlyIndexed(entity.isCurrentlyIndexed());
    doEntity.setIndexingTreatment(
        entity.getIndexingTreatment() != null ? entity.getIndexingTreatment().getCode() : null);
    doEntity.setCitationSubset(
        entity.getCitationSubset() != null ? entity.getCitationSubset().getCode() : null);
    doEntity.setStartYear(entity.getStartYear());
    doEntity.setStartVolume(entity.getStartVolume());
    doEntity.setStartIssue(entity.getStartIssue());
    doEntity.setEndYear(entity.getEndYear());
    doEntity.setEndVolume(entity.getEndVolume());
    doEntity.setEndIssue(entity.getEndIssue());
    return doEntity;
  }
}
