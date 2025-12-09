package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.converter.VenueIdentifierConverter;
import com.patra.catalog.infra.persistence.converter.VenueIndexingHistoryConverter;
import com.patra.catalog.infra.persistence.converter.VenueMeshConverter;
import com.patra.catalog.infra.persistence.converter.VenuePublicationStatsConverter;
import com.patra.catalog.infra.persistence.converter.VenueRelationConverter;
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
/// - 批量操作使用 `Db.saveBatch()` 配合 `rewriteBatchedStatements=true` 提升写入效率
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
  private final VenuePublicationStatsConverter publicationStatsConverter;
  private final VenueMeshConverter meshConverter;
  private final VenueRelationConverter relationConverter;
  private final VenueIndexingHistoryConverter indexingHistoryConverter;

  @Override
  public boolean hasAnyData() {
    return venueMapper.selectCount(null) > 0;
  }

  @Override
  public void insertAll(List<VenueAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 1. 转换主表 DO（不设置 ID，由 MyBatis-Plus 自动生成）
    List<VenueDO> venueDOs = new ArrayList<>(aggregates.size());
    for (VenueAggregate aggregate : aggregates) {
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDOs.add(venueDO);
    }

    // 2. 批量插入主表（ID 自动回填到 DO）
    Db.saveBatch(venueDOs);
    log.debug("批量插入载体 {} 条", venueDOs.size());

    // 3. 从 DO 回填 ID 到聚合根，并收集子表数据
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    for (int i = 0; i < aggregates.size(); i++) {
      VenueAggregate aggregate = aggregates.get(i);
      Long venueId = venueDOs.get(i).getId();
      aggregate.assignId(venueId);
      collectIdentifiers(aggregate, venueId, identifierDOs);
    }

    // 4. 批量插入标识符
    if (!identifierDOs.isEmpty()) {
      Db.saveBatch(identifierDOs);
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

    // 收集新的标识符数据和主表 DO
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    List<VenueDO> venueDOs = new ArrayList<>(aggregates.size());

    for (VenueAggregate aggregate : aggregates) {
      // 转换主表 DO
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(aggregate.getId());
      venueDOs.add(venueDO);

      // 收集标识符数据
      collectIdentifiers(aggregate, aggregate.getId(), identifierDOs);
    }

    // 批量更新主表
    Db.updateBatchById(venueDOs);
    log.debug("批量更新载体 {} 条", venueDOs.size());

    // 批量插入新的标识符
    if (!identifierDOs.isEmpty()) {
      Db.saveBatch(identifierDOs);
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
      VenueIdentifier identifier = identifierConverter.toEntity(identifierDO);
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
                Collectors.mapping(publicationStatsConverter::toEntity, Collectors.toList())));
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
        VenuePublicationStatsDO statsDO = publicationStatsConverter.toDO(stats);
        statsDO.setVenueId(venueId);
        doList.add(statsDO);
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      Db.saveBatch(doList);
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
                VenueMeshDO::getVenueId,
                Collectors.mapping(meshConverter::toEntity, Collectors.toList())));
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
        VenueMeshDO meshDO = meshConverter.toDO(mesh);
        meshDO.setVenueId(venueId);
        doList.add(meshDO);
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      Db.saveBatch(doList);
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
                Collectors.mapping(relationConverter::toEntity, Collectors.toList())));
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
        VenueRelationDO relationDO = relationConverter.toDO(relation);
        relationDO.setVenueId(venueId);
        doList.add(relationDO);
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      Db.saveBatch(doList);
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
                Collectors.mapping(indexingHistoryConverter::toEntity, Collectors.toList())));
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
        VenueIndexingHistoryDO historyDO = indexingHistoryConverter.toDO(history);
        historyDO.setVenueId(venueId);
        doList.add(historyDO);
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      Db.saveBatch(doList);
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
}
