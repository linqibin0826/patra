package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.port.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.converter.VenueIdentifierConverter;
import com.patra.catalog.infra.persistence.converter.VenuePublicationStatsConverter;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenuePublicationStatsMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
/// - 支持 OpenAlex Sources 批量数据导入
/// - 以聚合根为单位保证数据一致性
///
/// **性能优化**：
///
/// 批量操作使用 `insertBatchSomeColumn` 提升写入效率
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

    for (VenueAggregate aggregate : aggregates) {
      // 生成 ID
      Long venueId = IdWorker.getId();

      // 转换主表
      VenueDO venueDO = venueConverter.toDO(aggregate);
      venueDO.setId(venueId);
      venueDOs.add(venueDO);

      // 收集子表数据
      collectChildData(aggregate, venueId, identifierDOs, metricsDOs);
    }

    // 批量插入主表
    venueMapper.insertBatchSomeColumn(venueDOs);
    log.debug("批量插入载体 {} 条", venueDOs.size());

    // 批量插入子表
    if (!identifierDOs.isEmpty()) {
      venueIdentifierMapper.insertBatchSomeColumn(identifierDOs);
      log.debug("批量插入载体标识符 {} 条", identifierDOs.size());
    }
    if (!metricsDOs.isEmpty()) {
      venuePublicationStatsMapper.insertBatchSomeColumn(metricsDOs);
      log.debug("批量插入载体年度指标 {} 条", metricsDOs.size());
    }
  }

  /// 收集聚合根的子表数据。
  ///
  /// @param aggregate 聚合根
  /// @param venueId 主表 ID
  /// @param identifierDOs 标识符 DO 列表（输出参数）
  /// @param metricsDOs 年度指标 DO 列表（输出参数）
  private void collectChildData(
      VenueAggregate aggregate,
      Long venueId,
      List<VenueIdentifierDO> identifierDOs,
      List<VenuePublicationStatsDO> metricsDOs) {
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
}
