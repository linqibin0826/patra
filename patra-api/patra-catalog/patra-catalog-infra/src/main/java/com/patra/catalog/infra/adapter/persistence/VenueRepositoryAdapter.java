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
/// **聚合边界**：
///
/// - VenueAggregate：聚合根
/// - VenueIdentifier：值对象集合（保护 ISSN-L 唯一性不变量）
///
/// **职责**：
///
/// - 管理 VenueAggregate（载体聚合根）的持久化
/// - 管理与聚合关联的补充数据（年度指标、MeSH、关联关系、索引历史）
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
  private final VenuePublicationStatsMapper publicationStatsMapper;
  private final VenueMeshMapper meshMapper;
  private final VenueRelationMapper relationMapper;
  private final VenueIndexingHistoryMapper indexingHistoryMapper;
  private final VenueConverter venueConverter;
  private final VenueIdentifierConverter identifierConverter;

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

    for (VenueAggregate aggregate : aggregates) {
      // 生成 ID 并回填到聚合根
      Long venueId = IdWorker.getId();
      aggregate.assignId(venueId);

      // 转换主表
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(venueId);
      venueDOs.add(venueDO);

      // 收集标识符数据
      collectIdentifiers(aggregate, venueId, identifierDOs);
    }

    // 批量插入主表
    venueMapper.insertBatchSomeColumn(venueDOs);
    log.debug("批量插入载体 {} 条", venueDOs.size());

    // 批量插入标识符
    if (!identifierDOs.isEmpty()) {
      venueIdentifierMapper.insertBatchSomeColumn(identifierDOs);
      log.debug("批量插入载体标识符 {} 条", identifierDOs.size());
    }
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

    // 删除旧的标识符
    venueIdentifierMapper.delete(
        new LambdaQueryWrapper<VenueIdentifierDO>().in(VenueIdentifierDO::getVenueId, venueIds));

    // 收集新的标识符数据
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();

    for (VenueAggregate aggregate : aggregates) {
      // 更新主表
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(aggregate.getId());
      venueMapper.updateById(venueDO);

      // 收集标识符数据
      collectIdentifiers(aggregate, aggregate.getId(), identifierDOs);
    }

    log.debug("批量更新载体 {} 条", aggregates.size());

    // 批量插入新的标识符
    if (!identifierDOs.isEmpty()) {
      venueIdentifierMapper.insertBatchSomeColumn(identifierDOs);
      log.debug("批量插入载体标识符 {} 条", identifierDOs.size());
    }
  }

  // ========== 私有辅助方法 ==========

  /// 收集聚合根的标识符数据。
  ///
  /// @param aggregate 聚合根
  /// @param venueId 主表 ID
  /// @param identifierDOs 标识符 DO 列表（输出参数）
  private void collectIdentifiers(
      VenueAggregate aggregate, Long venueId, List<VenueIdentifierDO> identifierDOs) {
    for (VenueIdentifier identifier : aggregate.getIdentifiers()) {
      VenueIdentifierDO identifierDO = identifierConverter.toDO(identifier);
      identifierDO.setId(IdWorker.getId());
      identifierDO.setVenueId(venueId);
      identifierDOs.add(identifierDO);
    }
  }

  /// 从 DO 列表重建聚合根列表。
  ///
  /// 批量加载标识符，避免 N+1 问题。
  ///
  /// @param venueDOs 主表 DO 列表
  /// @return 重建的聚合根列表
  private List<VenueAggregate> reconstructAggregates(List<VenueDO> venueDOs) {
    List<Long> venueIds = venueDOs.stream().map(VenueDO::getId).toList();

    // 批量加载标识符
    Map<Long, List<VenueIdentifierDO>> identifiersByVenueId = loadIdentifiers(venueIds);

    // 重建聚合根
    List<VenueAggregate> aggregates = new ArrayList<>(venueDOs.size());
    for (VenueDO venueDO : venueDOs) {
      VenueAggregate aggregate =
          reconstructAggregate(
              venueDO, identifiersByVenueId.getOrDefault(venueDO.getId(), List.of()));
      aggregates.add(aggregate);
    }

    return aggregates;
  }

  /// 重建单个聚合根。
  private VenueAggregate reconstructAggregate(
      VenueDO venueDO, List<VenueIdentifierDO> identifierDOs) {

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

    // 添加标识符
    for (VenueIdentifierDO identifierDO : identifierDOs) {
      VenueIdentifier identifier = toIdentifierEntity(identifierDO);
      aggregate.addIdentifier(identifier);
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

  // ========== DO → Entity 转换方法 ==========

  private VenueIdentifier toIdentifierEntity(VenueIdentifierDO doEntity) {
    return new VenueIdentifier(
        VenueIdentifierType.valueOf(doEntity.getIdentifierType()), doEntity.getIdentifierValue());
  }

  // ========== 补充数据管理（年度指标、MeSH、关联关系、索引历史） ==========

  // --- 年度指标（OpenAlex 数据） ---

  @Override
  public Map<Long, List<VenuePublicationStats>> findYearlyMetricsByVenueIds(
      Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenuePublicationStatsDO> doList =
        publicationStatsMapper.selectList(
            new LambdaQueryWrapper<VenuePublicationStatsDO>()
                .in(VenuePublicationStatsDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenuePublicationStatsDO::getVenueId,
                Collectors.mapping(this::toPublicationStats, Collectors.toList())));
  }

  @Override
  public void replaceYearlyMetricsBatch(Map<Long, List<VenuePublicationStats>> metricsByVenueId) {
    if (metricsByVenueId == null || metricsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(metricsByVenueId.keySet());

    // 删除旧数据
    publicationStatsMapper.delete(
        new LambdaQueryWrapper<VenuePublicationStatsDO>()
            .in(VenuePublicationStatsDO::getVenueId, venueIds));

    // 收集新数据
    List<VenuePublicationStatsDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenuePublicationStats>> entry : metricsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenuePublicationStats stats : entry.getValue()) {
        doList.add(toPublicationStatsDO(stats, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      publicationStatsMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入年度指标 {} 条", doList.size());
    }
  }

  // --- MeSH 主题词（Serfile 数据） ---

  @Override
  public Map<Long, List<VenueMesh>> findMeshTermsByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueMeshDO> doList =
        meshMapper.selectList(
            new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueMeshDO::getVenueId, Collectors.mapping(this::toMesh, Collectors.toList())));
  }

  @Override
  public void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId) {
    if (meshTermsByVenueId == null || meshTermsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(meshTermsByVenueId.keySet());

    // 删除旧数据
    meshMapper.delete(new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueMeshDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueMesh>> entry : meshTermsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueMesh mesh : entry.getValue()) {
        doList.add(toMeshDO(mesh, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      meshMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入 MeSH 主题词 {} 条", doList.size());
    }
  }

  // --- 期刊关联关系（Serfile 数据） ---

  @Override
  public Map<Long, List<VenueRelation>> findRelationsByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueRelationDO> doList =
        relationMapper.selectList(
            new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueRelationDO::getVenueId,
                Collectors.mapping(this::toRelation, Collectors.toList())));
  }

  @Override
  public void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId) {
    if (relationsByVenueId == null || relationsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(relationsByVenueId.keySet());

    // 删除旧数据
    relationMapper.delete(
        new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueRelationDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueRelation>> entry : relationsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueRelation relation : entry.getValue()) {
        doList.add(toRelationDO(relation, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      relationMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入关联关系 {} 条", doList.size());
    }
  }

  // --- 索引历史（Serfile 数据） ---

  @Override
  public Map<Long, List<VenueIndexingHistory>> findIndexingHistoriesByVenueIds(
      Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueIndexingHistoryDO> doList =
        indexingHistoryMapper.selectList(
            new LambdaQueryWrapper<VenueIndexingHistoryDO>()
                .in(VenueIndexingHistoryDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueIndexingHistoryDO::getVenueId,
                Collectors.mapping(this::toIndexingHistory, Collectors.toList())));
  }

  @Override
  public void replaceIndexingHistoriesBatch(
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    if (historiesByVenueId == null || historiesByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(historiesByVenueId.keySet());

    // 删除旧数据
    indexingHistoryMapper.delete(
        new LambdaQueryWrapper<VenueIndexingHistoryDO>()
            .in(VenueIndexingHistoryDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueIndexingHistoryDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueIndexingHistory>> entry : historiesByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueIndexingHistory history : entry.getValue()) {
        doList.add(toIndexingHistoryDO(history, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      indexingHistoryMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入索引历史 {} 条", doList.size());
    }
  }

  // --- 便捷方法：Serfile 数据批量替换 ---

  @Override
  public void replaceSerfileDataBatch(
      Map<Long, List<VenueMesh>> meshTermsByVenueId,
      Map<Long, List<VenueRelation>> relationsByVenueId,
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    replaceMeshTermsBatch(meshTermsByVenueId);
    replaceRelationsBatch(relationsByVenueId);
    replaceIndexingHistoriesBatch(historiesByVenueId);
  }

  // ========== 补充数据 DO → Entity 转换方法 ==========

  private VenuePublicationStats toPublicationStats(VenuePublicationStatsDO doEntity) {
    return VenuePublicationStats.create(
        doEntity.getYear().intValue(),
        doEntity.getWorksCount() != null ? doEntity.getWorksCount() : 0,
        doEntity.getCitedByCount() != null ? doEntity.getCitedByCount() : 0,
        doEntity.getOaWorksCount());
  }

  private VenueMesh toMesh(VenueMeshDO doEntity) {
    return new VenueMesh(
        doEntity.getDescriptorName(),
        doEntity.getDescriptorUi(),
        Boolean.TRUE.equals(doEntity.getIsMajorTopic()),
        doEntity.getQualifierName(),
        doEntity.getQualifierUi());
  }

  private VenueRelation toRelation(VenueRelationDO doEntity) {
    VenueRelationType relationType = VenueRelationType.fromCodeOrNull(doEntity.getRelationType());
    if (relationType == null) {
      relationType = VenueRelationType.PRECEDING;
    }

    return new VenueRelation(
        doEntity.getRelatedVenueId(),
        doEntity.getRelatedNlmId(),
        doEntity.getRelatedTitle(),
        relationType,
        doEntity.getEffectiveDate(),
        doEntity.getNotes());
  }

  private VenueIndexingHistory toIndexingHistory(VenueIndexingHistoryDO doEntity) {
    IndexingTreatment treatment =
        doEntity.getIndexingTreatment() != null
            ? IndexingTreatment.fromCodeOrNull(doEntity.getIndexingTreatment())
            : null;
    CitationSubset subset =
        doEntity.getCitationSubset() != null
            ? CitationSubset.fromCodeOrNull(doEntity.getCitationSubset())
            : null;

    return new VenueIndexingHistory(
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

  // ========== 补充数据 Entity → DO 转换方法 ==========

  private VenuePublicationStatsDO toPublicationStatsDO(VenuePublicationStats entity, Long venueId) {
    VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setYear((short) entity.year());
    doEntity.setWorksCount(entity.worksCount());
    doEntity.setCitedByCount(entity.citedByCount());
    doEntity.setOaWorksCount(entity.oaWorksCount());
    return doEntity;
  }

  private VenueMeshDO toMeshDO(VenueMesh entity, Long venueId) {
    VenueMeshDO doEntity = new VenueMeshDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setDescriptorName(entity.descriptorName());
    doEntity.setDescriptorUi(entity.descriptorUi());
    doEntity.setIsMajorTopic(entity.isMajorTopic());
    doEntity.setQualifierName(entity.qualifierName());
    doEntity.setQualifierUi(entity.qualifierUi());
    return doEntity;
  }

  private VenueRelationDO toRelationDO(VenueRelation entity, Long venueId) {
    VenueRelationDO doEntity = new VenueRelationDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setRelatedVenueId(entity.relatedVenueId());
    doEntity.setRelatedNlmId(entity.relatedNlmId());
    doEntity.setRelatedTitle(entity.relatedTitle());
    doEntity.setRelationType(entity.relationType().getCode());
    doEntity.setEffectiveDate(entity.effectiveDate());
    doEntity.setNotes(entity.notes());
    return doEntity;
  }

  private VenueIndexingHistoryDO toIndexingHistoryDO(VenueIndexingHistory entity, Long venueId) {
    VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setIndexingSource(entity.indexingSource());
    doEntity.setCurrentlyIndexed(entity.currentlyIndexed());
    doEntity.setIndexingTreatment(
        entity.indexingTreatment() != null ? entity.indexingTreatment().getCode() : null);
    doEntity.setCitationSubset(
        entity.citationSubset() != null ? entity.citationSubset().getCode() : null);
    doEntity.setStartYear(entity.startYear());
    doEntity.setStartVolume(entity.startVolume());
    doEntity.setStartIssue(entity.startIssue());
    doEntity.setEndYear(entity.endYear());
    doEntity.setEndVolume(entity.endVolume());
    doEntity.setEndIssue(entity.endIssue());
    return doEntity;
  }
}
