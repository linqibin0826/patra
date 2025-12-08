package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.converter.VenueIdentifierConverter;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
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
}
