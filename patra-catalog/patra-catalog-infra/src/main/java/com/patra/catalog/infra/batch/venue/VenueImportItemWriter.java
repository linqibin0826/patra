package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.port.VenueRepository;
import java.util.ArrayList;
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
/// - 将 VenueAggregate 批量持久化
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
public class VenueImportItemWriter implements ItemWriter<VenueAggregate> {

  private final VenueRepository venueRepository;

  @Override
  public void write(Chunk<? extends VenueAggregate> chunk) throws Exception {
    List<? extends VenueAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    // Step 1: Chunk 内去重（必须保留，否则同批次内重复也会导致插入失败）
    List<VenueAggregate> deduplicatedInChunk = deduplicateWithinChunk(items);

    // Step 2: 乐观插入
    try {
      venueRepository.insertAll(deduplicatedInChunk);
      log.debug("写入完成：新增={}", deduplicatedInChunk.size());
    } catch (DuplicateKeyException e) {
      // 明确的唯一键冲突
      handleDuplicateKeyException(deduplicatedInChunk);
    } catch (DataIntegrityViolationException e) {
      // 其他数据完整性冲突，检查是否为唯一键冲突
      if (isDuplicateKeyViolation(e)) {
        handleDuplicateKeyException(deduplicatedInChunk);
      } else {
        throw e; // 其他约束冲突，向上抛出
      }
    }
  }

  /// Chunk 内 ISSN-L 去重。
  ///
  /// 使用 LinkedHashMap 保持插入顺序，ISSN-L 为 null 的记录用 openalexId 作为 key。
  ///
  /// @param items 原始数据列表
  /// @return 去重后的数据列表
  private List<VenueAggregate> deduplicateWithinChunk(List<? extends VenueAggregate> items) {
    Map<String, VenueAggregate> uniqueMap = new LinkedHashMap<>();
    int duplicateCount = 0;

    for (VenueAggregate item : items) {
      String key = item.getIssnL() != null ? item.getIssnL() : ("_oa:" + item.getOpenalexId());

      if (uniqueMap.containsKey(key) && item.getIssnL() != null) {
        log.warn(
            "Chunk 内 ISSN-L 重复，跳过: issnL={}, openalexId={}", item.getIssnL(), item.getOpenalexId());
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
  /// @param items 原始数据列表（已经过 Chunk 内去重）
  private void handleDuplicateKeyException(List<VenueAggregate> items) {
    log.info("检测到 ISSN-L 唯一约束冲突，开始降级处理...");

    // 提取非空 ISSN-L
    Set<String> issnLs =
        items.stream()
            .map(VenueAggregate::getIssnL)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // 查询数据库已存在的
    Set<String> existingIssnLs = venueRepository.findExistingIssnLs(issnLs);

    // 过滤并记录日志
    List<VenueAggregate> toInsert = new ArrayList<>();
    for (VenueAggregate item : items) {
      if (item.getIssnL() != null && existingIssnLs.contains(item.getIssnL())) {
        log.warn(
            "数据库已存在 ISSN-L，跳过: issnL={}, openalexId={}", item.getIssnL(), item.getOpenalexId());
      } else {
        toInsert.add(item);
      }
    }

    log.info(
        "数据库去重完成：输入={}，输出={}，跳过={}", items.size(), toInsert.size(), items.size() - toInsert.size());

    // 重新插入
    if (!toInsert.isEmpty()) {
      venueRepository.insertAll(toInsert);
      log.debug("降级写入完成：新增={}", toInsert.size());
    }
  }
}
