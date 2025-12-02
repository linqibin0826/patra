package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.catalog.domain.port.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMetricsMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 出版载体聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 Venue（载体）、VenueIdentifier（标识符）、VenueMetrics（指标）的持久化
/// - 支持 OpenAlex Sources 批量数据导入
/// - 提供按标识符查询载体的能力
///
/// **性能优化**：
///
/// - 批量操作使用 `insertBatchSomeColumn` 提升写入效率
/// - 查询使用 LambdaQueryWrapper 确保类型安全
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

  private final VenueMapper venueMapper;
  private final VenueIdentifierMapper venueIdentifierMapper;
  private final VenueMetricsMapper venueMetricsMapper;
  private final VenueConverter venueConverter;

  // ========================================
  // 基本 CRUD 操作
  // ========================================

  @Override
  public Optional<VenueData> findById(Long id) {
    VenueDO entity = venueMapper.selectById(id);
    return Optional.ofNullable(entity).map(venueConverter::toVenueData);
  }

  @Override
  public Optional<VenueData> findByOpenalexId(String openalexId) {
    LambdaQueryWrapper<VenueDO> wrapper =
        new LambdaQueryWrapper<VenueDO>().eq(VenueDO::getOpenalexId, openalexId);
    VenueDO entity = venueMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(venueConverter::toVenueData);
  }

  @Override
  public Optional<VenueData> findByIssnL(String issnL) {
    LambdaQueryWrapper<VenueDO> wrapper =
        new LambdaQueryWrapper<VenueDO>().eq(VenueDO::getIssnL, issnL);
    VenueDO entity = venueMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(venueConverter::toVenueData);
  }

  @Override
  public VenueData save(VenueData venue) {
    VenueDO entity = venueConverter.toVenueDO(venue);
    if (entity.getId() == null) {
      venueMapper.insert(entity);
    } else {
      venueMapper.updateById(entity);
    }
    return venueConverter.toVenueData(entity);
  }

  // ========================================
  // 批量操作
  // ========================================

  @Override
  public List<VenueData> saveAll(List<VenueData> venues) {
    List<VenueDO> entities = venueConverter.toVenueDOList(venues);

    // 分离新增和更新
    List<VenueDO> toInsert = entities.stream().filter(e -> e.getId() == null).toList();
    List<VenueDO> toUpdate = entities.stream().filter(e -> e.getId() != null).toList();

    // 批量新增
    if (!toInsert.isEmpty()) {
      venueMapper.insertBatchSomeColumn(toInsert);
      log.debug("批量插入载体 {} 条", toInsert.size());
    }

    // 逐条更新（更新场景较少，暂不优化）
    for (VenueDO entity : toUpdate) {
      venueMapper.updateById(entity);
    }
    if (!toUpdate.isEmpty()) {
      log.debug("批量更新载体 {} 条", toUpdate.size());
    }

    return venueConverter.toVenueDataList(entities);
  }

  @Override
  public void saveIdentifiers(List<VenueIdentifierData> identifiers) {
    if (identifiers.isEmpty()) {
      return;
    }

    List<VenueIdentifierDO> entities = venueConverter.toIdentifierDOList(identifiers);
    venueIdentifierMapper.insertBatchSomeColumn(entities);
    log.debug("批量插入载体标识符 {} 条", entities.size());
  }

  @Override
  public void saveMetrics(List<VenueMetricsData> metrics) {
    if (metrics.isEmpty()) {
      return;
    }

    List<VenueMetricsDO> entities = venueConverter.toMetricsDOList(metrics);
    venueMetricsMapper.insertBatchSomeColumn(entities);
    log.debug("批量插入载体年度指标 {} 条", entities.size());
  }

  // ========================================
  // 标识符查询
  // ========================================

  @Override
  public Optional<Long> findVenueIdByIdentifier(String identifierType, String identifierValue) {
    LambdaQueryWrapper<VenueIdentifierDO> wrapper =
        new LambdaQueryWrapper<VenueIdentifierDO>()
            .eq(VenueIdentifierDO::getIdentifierType, identifierType)
            .eq(VenueIdentifierDO::getIdentifierValue, identifierValue)
            .select(VenueIdentifierDO::getVenueId);
    VenueIdentifierDO entity = venueIdentifierMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(VenueIdentifierDO::getVenueId);
  }

  @Override
  public List<VenueIdentifierData> findIdentifiersByVenueId(Long venueId) {
    LambdaQueryWrapper<VenueIdentifierDO> wrapper =
        new LambdaQueryWrapper<VenueIdentifierDO>().eq(VenueIdentifierDO::getVenueId, venueId);
    return venueConverter.toIdentifierDataList(venueIdentifierMapper.selectList(wrapper));
  }

  // ========================================
  // 指标查询
  // ========================================

  @Override
  public List<VenueMetricsData> findMetricsByVenueId(Long venueId) {
    LambdaQueryWrapper<VenueMetricsDO> wrapper =
        new LambdaQueryWrapper<VenueMetricsDO>()
            .eq(VenueMetricsDO::getVenueId, venueId)
            .orderByDesc(VenueMetricsDO::getYear);
    return venueConverter.toMetricsDataList(venueMetricsMapper.selectList(wrapper));
  }

  @Override
  public Optional<VenueMetricsData> findMetricsByVenueIdAndYear(Long venueId, int year) {
    LambdaQueryWrapper<VenueMetricsDO> wrapper =
        new LambdaQueryWrapper<VenueMetricsDO>()
            .eq(VenueMetricsDO::getVenueId, venueId)
            .eq(VenueMetricsDO::getYear, year);
    VenueMetricsDO entity = venueMetricsMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(venueConverter::toMetricsData);
  }
}
