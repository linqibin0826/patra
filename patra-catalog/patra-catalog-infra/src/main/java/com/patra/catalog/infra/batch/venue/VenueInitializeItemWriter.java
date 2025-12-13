package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.port.repository.VenueRepository;
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
/// - 将 VenueParseResult 解析为聚合根，持久化到 cat_venue + cat_venue_identifier
/// - 嵌入式值对象（publicationProfile, citationMetrics, openAccess, affiliatedSocieties）作为 JSON 随聚合根一起保存
/// - 年度指标保存到 cat_venue_publication_stats
/// - Chunk 内 ISSN-L 去重：同一批次内的重复记录在内存中过滤
/// - 乐观插入：正常情况直接插入，唯一约束冲突时降级处理
///
/// **DDD 嵌入式值对象设计**：
///
/// 聚合根（VenueAggregate）已包含所有嵌入式值对象，不再需要分别保存：
/// - publicationProfile → cat_venue.publication_profile (JSON)
/// - citationMetrics → cat_venue.citation_metrics (JSON)
/// - openAccess → cat_venue.open_access (JSON)
/// - affiliatedSocieties → cat_venue.affiliated_societies (JSON)
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
public class VenueInitializeItemWriter implements ItemWriter<VenueParseResult> {

  private final VenueRepository venueRepository;

  @Override
  public void write(Chunk<? extends VenueParseResult> chunk) throws Exception {
    List<? extends VenueParseResult> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    // Step 1: Chunk 内去重（必须保留，否则同批次内重复也会导致插入失败）
    List<VenueParseResult> deduplicatedInChunk = deduplicateWithinChunk(items);

    // Step 2: 提取聚合根（已包含嵌入式值对象）
    List<VenueAggregate> aggregates =
        deduplicatedInChunk.stream().map(VenueParseResult::aggregate).toList();

    // Step 3: 乐观插入聚合根（嵌入式值对象随聚合根一起保存为 JSON）
    List<VenueAggregate> insertedAggregates;
    try {
      venueRepository.insertAll(aggregates);
      insertedAggregates = aggregates;
      log.debug("写入聚合根完成：新增={}", aggregates.size());
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

    // Step 4: 保存年度指标（唯一仍需独立保存的 1:N 数据）
    saveYearlyMetrics(insertedAggregates, deduplicatedInChunk);
  }

  /// 保存年度指标（cat_venue_publication_stats）。
  ///
  /// @param insertedAggregates 成功插入的聚合根列表
  /// @param items 原始解析结果（用于提取年度指标）
  private void saveYearlyMetrics(
      List<VenueAggregate> insertedAggregates, List<VenueParseResult> items) {
    if (insertedAggregates.isEmpty()) {
      return;
    }

    // 构建 OpenAlex ID → VenueParseResult 映射
    Map<String, VenueParseResult> itemByOpenalexId = new HashMap<>();
    for (VenueParseResult item : items) {
      String openalexId = getOpenalexId(item.aggregate());
      if (openalexId != null) {
        itemByOpenalexId.put(openalexId, item);
      }
    }

    // 构建 venueId → 年度指标列表映射
    Map<Long, List<VenuePublicationStats>> yearlyMetricsByVenueId = new HashMap<>();

    for (VenueAggregate aggregate : insertedAggregates) {
      String openalexId = getOpenalexId(aggregate);
      VenueParseResult item = itemByOpenalexId.get(openalexId);
      if (item == null) {
        continue;
      }

      Long venueId = aggregate.getId() != null ? aggregate.getId().value() : null;
      if (venueId == null) {
        continue; // 未持久化的聚合根，跳过
      }

      // 年度指标
      if (item.hasYearlyMetrics()) {
        yearlyMetricsByVenueId.put(venueId, item.yearlyMetrics());
      }
    }

    // 批量保存年度指标
    if (!yearlyMetricsByVenueId.isEmpty()) {
      venueRepository.replaceYearlyMetricsBatch(yearlyMetricsByVenueId);
      log.debug("写入年度指标完成：{} 个 Venue", yearlyMetricsByVenueId.size());
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
      String issnL = getIssnL(aggregate);
      String openalexId = getOpenalexId(aggregate);
      String key = issnL != null ? issnL : ("_oa:" + openalexId);

      if (uniqueMap.containsKey(key) && issnL != null) {
        log.warn("Chunk 内 ISSN-L 重复，使用后者覆盖: issnL={}, openalexId={}", issnL, openalexId);
        duplicateCount++;
      }
      uniqueMap.put(key, item);
    }

    if (duplicateCount > 0) {
      log.info("Chunk 内去重完成：原始={}，去重后={}，覆盖={}", items.size(), uniqueMap.size(), duplicateCount);
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
            .map(this::getIssnL)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // 查询数据库已存在的
    Set<String> existingIssnLs = venueRepository.findExistingIssnLs(issnLs);

    // 过滤并记录日志
    List<VenueAggregate> toInsert = new ArrayList<>();
    for (VenueAggregate item : aggregates) {
      String issnL = getIssnL(item);
      if (issnL != null && existingIssnLs.contains(issnL)) {
        log.warn("数据库已存在 ISSN-L，跳过: issnL={}, openalexId={}", issnL, getOpenalexId(item));
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

  // ========== 标识符访问辅助方法 ==========

  /// 获取聚合根的 OpenAlex ID。
  ///
  /// @param aggregate 聚合根
  /// @return OpenAlex ID，不存在时返回 null
  private String getOpenalexId(VenueAggregate aggregate) {
    return aggregate.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null);
  }

  /// 获取聚合根的 ISSN-L。
  ///
  /// @param aggregate 聚合根
  /// @return ISSN-L，不存在时返回 null
  private String getIssnL(VenueAggregate aggregate) {
    return aggregate.getIdentifier(VenueIdentifierType.ISSN_L).orElse(null);
  }
}
