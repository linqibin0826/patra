package com.patra.catalog.infra.batch.venue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.entity.VenueMetrics;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMetricsMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 批量写入器（Upsert 策略）。
///
/// **职责**：
///
/// - 将 VenueAggregate 转换为 DO 并持久化
/// - 实现 Upsert 策略：已存在则更新，不存在则新增
/// - 处理子表数据：VenueIdentifier、VenueMetrics
///
/// **Upsert 流程**：
///
/// 1. 提取所有 openalexId
/// 2. 批量查询已存在记录
/// 3. 分离新增和更新列表
/// 4. 批量 INSERT 新记录
/// 5. 逐条 UPDATE 已存在记录
/// 6. 子表：先删后插
///
/// **性能优化**：
///
/// - 使用单条 SQL 批量插入（`INSERT INTO ... VALUES (...), (...), ...`）
/// - 批量查询减少数据库往返
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueImportItemWriter implements ItemWriter<VenueAggregate> {

  private final VenueMapper venueMapper;
  private final VenueIdentifierMapper identifierMapper;
  private final VenueMetricsMapper metricsMapper;
  private final ObjectMapper objectMapper;

  @Override
  public void write(Chunk<? extends VenueAggregate> chunk) throws Exception {
    List<? extends VenueAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    log.debug("开始写入 {} 条 Venue 记录", items.size());

    // 1. 提取所有 openalexId
    Set<String> openalexIds =
        items.stream().map(VenueAggregate::getOpenalexId).collect(Collectors.toSet());

    // 2. 批量查询已存在记录
    LambdaQueryWrapper<VenueDO> queryWrapper =
        Wrappers.<VenueDO>lambdaQuery()
            .in(VenueDO::getOpenalexId, openalexIds)
            .select(VenueDO::getId, VenueDO::getOpenalexId);

    List<VenueDO> existingRecords = venueMapper.selectList(queryWrapper);
    Map<String, Long> existingIdMap = new HashMap<>();
    for (VenueDO record : existingRecords) {
      existingIdMap.put(record.getOpenalexId(), record.getId());
    }

    // 3. 分离新增和更新列表
    List<VenueAggregate> toInsert = new ArrayList<>();
    List<VenueAggregate> toUpdate = new ArrayList<>();
    Map<VenueAggregate, Long> updateIdMap = new HashMap<>();

    for (VenueAggregate venue : items) {
      Long existingId = existingIdMap.get(venue.getOpenalexId());
      if (existingId != null) {
        toUpdate.add(venue);
        updateIdMap.put(venue, existingId);
      } else {
        toInsert.add(venue);
      }
    }

    // 4. 批量 INSERT 新记录
    if (!toInsert.isEmpty()) {
      insertVenues(toInsert);
    }

    // 5. 逐条 UPDATE 已存在记录
    if (!toUpdate.isEmpty()) {
      updateVenues(toUpdate, updateIdMap);
    }

    log.debug("写入完成：新增={}，更新={}", toInsert.size(), toUpdate.size());
  }

  /// 批量插入新记录。
  private void insertVenues(List<VenueAggregate> venues) {
    List<VenueDO> venueDOs = new ArrayList<>(venues.size());
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    List<VenueMetricsDO> metricsDOs = new ArrayList<>();

    for (VenueAggregate venue : venues) {
      // 生成 ID
      Long venueId = IdWorker.getId();

      // 转换主表
      VenueDO venueDO = toVenueDO(venue);
      venueDO.setId(venueId);
      venueDOs.add(venueDO);

      // 收集子表数据
      collectChildData(venue, venueId, identifierDOs, metricsDOs);
    }

    // 批量插入主表
    venueMapper.insertBatchSomeColumn(venueDOs);

    // 批量插入子表
    if (!identifierDOs.isEmpty()) {
      identifierMapper.insertBatchSomeColumn(identifierDOs);
    }
    if (!metricsDOs.isEmpty()) {
      metricsMapper.insertBatchSomeColumn(metricsDOs);
    }
  }

  /// 逐条更新已存在记录。
  private void updateVenues(List<VenueAggregate> venues, Map<VenueAggregate, Long> idMap) {
    List<Long> venueIds = new ArrayList<>(venues.size());
    List<VenueIdentifierDO> identifierDOs = new ArrayList<>();
    List<VenueMetricsDO> metricsDOs = new ArrayList<>();

    for (VenueAggregate venue : venues) {
      Long venueId = idMap.get(venue);
      venueIds.add(venueId);

      // 更新主表
      VenueDO venueDO = toVenueDO(venue);
      venueDO.setId(venueId);
      venueMapper.updateById(venueDO);

      // 收集子表数据
      collectChildData(venue, venueId, identifierDOs, metricsDOs);
    }

    // 物理删除旧的子表数据（绕过 @TableLogic，避免唯一索引冲突）
    if (!venueIds.isEmpty()) {
      identifierMapper.physicalDeleteByVenueIds(venueIds);
      metricsMapper.physicalDeleteByVenueIds(venueIds);
    }

    // 批量插入新的子表数据
    if (!identifierDOs.isEmpty()) {
      identifierMapper.insertBatchSomeColumn(identifierDOs);
    }
    if (!metricsDOs.isEmpty()) {
      metricsMapper.insertBatchSomeColumn(metricsDOs);
    }
  }

  /// 收集子表数据。
  private void collectChildData(
      VenueAggregate venue,
      Long venueId,
      List<VenueIdentifierDO> identifierDOs,
      List<VenueMetricsDO> metricsDOs) {
    // 收集标识符
    for (VenueIdentifier identifier : venue.getIdentifiers()) {
      VenueIdentifierDO identifierDO = new VenueIdentifierDO();
      identifierDO.setId(IdWorker.getId());
      identifierDO.setVenueId(venueId);
      identifierDO.setIdentifierType(identifier.getType().name());
      identifierDO.setIdentifierValue(identifier.getValue());
      identifierDO.setIsPrimary(identifier.isPrimary());
      identifierDOs.add(identifierDO);
    }

    // 收集年度指标
    for (VenueMetrics metrics : venue.getYearlyMetrics()) {
      VenueMetricsDO metricsDO = new VenueMetricsDO();
      metricsDO.setId(IdWorker.getId());
      metricsDO.setVenueId(venueId);
      metricsDO.setYear((short) metrics.getYear());
      metricsDO.setWorksCount(metrics.getWorksCount());
      metricsDO.setCitedByCount(metrics.getCitedByCount());
      metricsDO.setOaWorksCount(metrics.getOaWorksCount());
      metricsDOs.add(metricsDO);
    }
  }

  /// 将 VenueAggregate 转换为 VenueDO。
  private VenueDO toVenueDO(VenueAggregate aggregate) {
    VenueDO venueDO = new VenueDO();

    // 基础字段
    venueDO.setVenueType(aggregate.getVenueType().getCode());
    venueDO.setDisplayName(aggregate.getDisplayName());
    venueDO.setAbbreviatedTitle(aggregate.getAbbreviatedTitle());
    venueDO.setAlternateTitles(aggregate.getAlternateTitles());
    venueDO.setHomepageUrl(aggregate.getHomepageUrl());

    // 冗余标识符
    venueDO.setOpenalexId(aggregate.getOpenalexId());
    venueDO.setIssnL(aggregate.getIssnL());

    // 宿主机构
    HostOrganization host = aggregate.getHostOrganization();
    if (host != null) {
      venueDO.setHostOrganizationId(host.id());
      venueDO.setHostOrganizationName(host.name());
      venueDO.setHostOrganizationLineage(host.lineage());
    }

    // 地理信息
    venueDO.setCountryCode(aggregate.getCountryCode());

    // OA 状态
    venueDO.setIsOa(aggregate.isOa());
    venueDO.setIsInDoaj(aggregate.isInDoaj());
    venueDO.setIsCore(aggregate.isCore());

    // 统计快照（数据库约束 works_count/cited_by_count 不能为 NULL）
    VenueStats stats = aggregate.getCurrentStats();
    if (stats != null) {
      venueDO.setWorksCount(stats.worksCount() != null ? stats.worksCount() : 0);
      venueDO.setCitedByCount(stats.citedByCount() != null ? stats.citedByCount() : 0);
      venueDO.setHIndex(stats.hIndex());
      venueDO.setI10Index(stats.i10Index());
      venueDO.setTwoYearMeanCitedness(stats.twoYearMeanCitedness());
    } else {
      // 没有统计信息时设置默认值
      venueDO.setWorksCount(0);
      venueDO.setCitedByCount(0);
    }

    // APC 信息
    ApcInfo apcInfo = aggregate.getApcInfo();
    if (apcInfo != null) {
      venueDO.setApcUsd(apcInfo.usd());
      venueDO.setApcPrices(toApcPricesJson(apcInfo.prices()));
    }

    // 学会信息
    List<Society> societies = aggregate.getSocieties();
    if (societies != null && !societies.isEmpty()) {
      venueDO.setSocieties(toSocietiesJson(societies));
    }

    // 来源信息
    if (aggregate.getProvenance() != null) {
      venueDO.setProvenanceCode(aggregate.getProvenance().code());
      venueDO.setSourceCreatedDate(aggregate.getProvenance().sourceCreatedDate());
      venueDO.setSourceUpdatedDate(aggregate.getProvenance().sourceUpdatedDate());
    }

    venueDO.setLastSyncedAt(Instant.now());

    return venueDO;
  }

  /// 将 APC 价格列表转换为 JSON。
  private com.fasterxml.jackson.databind.JsonNode toApcPricesJson(List<ApcInfo.ApcPrice> prices) {
    if (prices == null || prices.isEmpty()) {
      return null;
    }

    ArrayNode arrayNode = objectMapper.createArrayNode();
    for (ApcInfo.ApcPrice price : prices) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("price", price.price());
      if (price.currency() != null) {
        node.put("currency", price.currency());
      }
      arrayNode.add(node);
    }
    return arrayNode;
  }

  /// 将学会列表转换为 JSON。
  private com.fasterxml.jackson.databind.JsonNode toSocietiesJson(List<Society> societies) {
    if (societies == null || societies.isEmpty()) {
      return null;
    }

    ArrayNode arrayNode = objectMapper.createArrayNode();
    for (Society society : societies) {
      ObjectNode node = objectMapper.createObjectNode();
      if (society.url() != null) {
        node.put("url", society.url());
      }
      if (society.organization() != null) {
        node.put("organization", society.organization());
      }
      arrayNode.add(node);
    }
    return arrayNode;
  }
}
