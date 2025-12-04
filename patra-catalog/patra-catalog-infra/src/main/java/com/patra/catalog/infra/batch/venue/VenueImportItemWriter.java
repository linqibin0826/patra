package com.patra.catalog.infra.batch.venue;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 批量写入器（纯 INSERT 策略）。
///
/// **职责**：
///
/// - 将 VenueAggregate 转换为 DO 并持久化
/// - 纯 INSERT 策略：所有记录作为新记录插入
/// - 处理子表数据：VenueIdentifier、VenueMetrics
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持 Upsert（更新已存在记录）
/// - 如果存在主键冲突，批处理会失败
/// - 数据库应该在导入前为空表状态
///
/// **性能优化**：
///
/// - 使用单条 SQL 批量插入（`INSERT INTO ... VALUES (...), (...), ...`）
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
    insertVenues(new ArrayList<>(items));
    log.debug("写入完成：新增={}", items.size());
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
