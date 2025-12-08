package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.repository.VenueSupplementRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 批量写入器（乐观插入策略）。
///
/// **职责**：
///
/// - 将 VenueParseResult 解析为聚合根和年度指标，分别持久化
/// - 聚合根通过 VenueRepository 持久化
/// - 年度指标通过 VenueSupplementRepository 持久化
/// - Chunk 内 ISSN-L 去重：同一批次内的重复记录在内存中过滤
/// - 乐观插入：正常情况直接插入，唯一约束冲突时降级处理
///
/// **乐观插入策略**：
///
/// 针对「一次性初始化」场景优化，数据库通常为空：
///
/// 1. 正常情况：直接批量插入，零额外查询开销
/// 2. 唯一约束冲突：捕获异常 → 查询已存在的 ISSN-L → 过滤重复 → 重新插入
///
/// **ISSN-L 去重规则**：
///
/// - Chunk 内重复：使用 LinkedHashMap 去重，保留第一条，跳过后续重复
/// - ISSN-L 为 null：不受去重影响，正常插入（MySQL 唯一索引对 NULL 不生效）
/// - 数据库重复：触发降级处理，查询后过滤
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueImportItemWriter implements ItemWriter<VenueParseResult> {

  private final VenueRepository venueRepository;
  private final VenueSupplementRepository venueSupplementRepository;

  @Override
  public void write(Chunk<? extends VenueParseResult> chunk) throws Exception {
    List<? extends VenueParseResult> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    // Step 1: Chunk 内去重（必须保留，否则同批次内重复也会导致插入失败）
    List<VenueParseResult> deduplicatedInChunk = deduplicateWithinChunk(items);

    // Step 2: 提取聚合根和年度指标
    List<VenueAggregate> aggregates =
        deduplicatedInChunk.stream().map(VenueParseResult::aggregate).toList();
    Map<String, List<VenuePublicationStats>> metricsByOpenalexId =
        extractMetrics(deduplicatedInChunk);

    // Step 3: 乐观插入聚合根
    List<VenueAggregate> insertedAggregates;
    try {
      venueRepository.insertAll(aggregates);
      insertedAggregates = aggregates;
      log.debug("写入完成：新增={}", aggregates.size());
    } catch (DuplicateKeyException e) {
      // 明确的唯一键冲突
      insertedAggregates = handleDuplicateKeyException(aggregates);
    } catch (DataIntegrityViolationException e) {
      // 其他数据完整性冲突，检查是否为唯一键冲突
      if (isDuplicateKeyViolation(e)) {
        insertedAggregates = handleDuplicateKeyException(aggregates);
      } else {
        throw e; // 其他约束冲突，向上抛出
      }
    }

    // Step 4: 插入年度指标（仅针对成功插入的聚合根）
    insertYearlyMetrics(insertedAggregates, metricsByOpenalexId);
  }

  /// 提取年度指标，按 OpenAlex ID 分组。
  ///
  /// @param items 解析结果列表
  /// @return 按 OpenAlex ID 分组的年度指标
  private Map<String, List<VenuePublicationStats>> extractMetrics(List<VenueParseResult> items) {
    Map<String, List<VenuePublicationStats>> result = new HashMap<>();
    for (VenueParseResult item : items) {
      if (item.hasYearlyMetrics()) {
        result.put(item.aggregate().getOpenalexId(), item.yearlyMetrics());
      }
    }
    return result;
  }

  /// 插入年度指标。
  ///
  /// 根据成功插入的聚合根，将对应的年度指标写入数据库。
  ///
  /// @param insertedAggregates 成功插入的聚合根列表
  /// @param metricsByOpenalexId 按 OpenAlex ID 分组的年度指标
  private void insertYearlyMetrics(
      List<VenueAggregate> insertedAggregates,
      Map<String, List<VenuePublicationStats>> metricsByOpenalexId) {
    if (metricsByOpenalexId.isEmpty() || insertedAggregates.isEmpty()) {
      return;
    }

    Map<Long, List<VenuePublicationStats>> metricsByVenueId = new HashMap<>();
    for (VenueAggregate aggregate : insertedAggregates) {
      List<VenuePublicationStats> metrics = metricsByOpenalexId.get(aggregate.getOpenalexId());
      if (metrics != null && !metrics.isEmpty()) {
        metricsByVenueId.put(aggregate.getId(), metrics);
      }
    }

    if (!metricsByVenueId.isEmpty()) {
      venueSupplementRepository.replaceYearlyMetricsBatch(metricsByVenueId);
      log.debug("写入年度指标：{} 个 Venue", metricsByVenueId.size());
    }
  }

  /// Chunk 内 ISSN-L 去重。
  ///
  /// 使用 LinkedHashMap 保持插入顺序，ISSN-L 为 null 的记录用 openalexId 作为 key。
  ///
  /// @param items 原始数据列表
  /// @return 去重后的数据列表
  private List<VenueParseResult> deduplicateWithinChunk(List<? extends VenueParseResult> items) {
    Map<String, VenueParseResult> uniqueMap = new LinkedHashMap<>();
    int duplicateCount = 0;

    for (VenueParseResult item : items) {
      VenueAggregate aggregate = item.aggregate();
      String key =
          aggregate.getIssnL() != null
              ? aggregate.getIssnL()
              : ("_oa:" + aggregate.getOpenalexId());

      if (uniqueMap.containsKey(key) && aggregate.getIssnL() != null) {
        log.warn(
            "Chunk 内 ISSN-L 重复，跳过: issnL={}, openalexId={}",
            aggregate.getIssnL(),
            aggregate.getOpenalexId());
        duplicateCount++;
      } else {
        uniqueMap.put(key, item);
      }
    }

    if (duplicateCount > 0) {
      log.info("Chunk 内去重完成：原始={}，去重后={}，跳过={}", items.size(), uniqueMap.size(), duplicateCount);
    }

    return new ArrayList<>(uniqueMap.values());
  }

  /// 检查是否为 ISSN-L 唯一键冲突（通过异常消息判断）。
  ///
  /// 仅检查 ISSN-L 唯一约束冲突，OpenAlex ID 冲突不在此处理（数据源保证唯一性）。
  ///
  /// @param e 数据完整性异常
  /// @return 如果是 ISSN-L 唯一键冲突返回 true
  private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
    String message = e.getMessage();
    return message != null
        && (message.contains("Duplicate entry") // MySQL 错误消息
            || message.contains("uk_issn_l")); // ISSN-L 唯一索引名
  }

  /// 处理 ISSN-L 唯一约束冲突。
  ///
  /// 查询数据库已存在的 ISSN-L，过滤后重新插入。
  ///
  /// @param aggregates 原始数据列表（已经过 Chunk 内去重）
  /// @return 成功插入的聚合根列表
  private List<VenueAggregate> handleDuplicateKeyException(List<VenueAggregate> aggregates) {
    log.info("检测到 ISSN-L 唯一约束冲突，开始降级处理...");

    // 提取非空 ISSN-L
    Set<String> issnLs =
        aggregates.stream()
            .map(VenueAggregate::getIssnL)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // 查询数据库已存在的
    Set<String> existingIssnLs = venueRepository.findExistingIssnLs(issnLs);

    // 过滤并记录日志
    List<VenueAggregate> toInsert = new ArrayList<>();
    for (VenueAggregate item : aggregates) {
      if (item.getIssnL() != null && existingIssnLs.contains(item.getIssnL())) {
        log.warn(
            "数据库已存在 ISSN-L，跳过: issnL={}, openalexId={}", item.getIssnL(), item.getOpenalexId());
      } else {
        toInsert.add(item);
      }
    }

    log.info(
        "数据库去重完成：输入={}，输出={}，跳过={}",
        aggregates.size(),
        toInsert.size(),
        aggregates.size() - toInsert.size());

    // 重新插入
    if (!toInsert.isEmpty()) {
      venueRepository.insertAll(toInsert);
      log.debug("降级写入完成：新增={}", toInsert.size());
    }

    return toInsert;
  }
}
