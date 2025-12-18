package com.patra.catalog.infra.adapter.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

/// Venue 导入全阶段错误监听器。
///
/// **职责**：
///
/// 在批量导入的任何阶段出错时，自动打印导致错误的数据，方便排查问题。
///
/// **监听阶段**：
///
/// - 读取阶段（ItemReadListener）：捕获 JSON 解析/文件读取错误
/// - 处理阶段（ItemProcessListener）：捕获数据转换/验证错误
/// - 写入阶段（ItemWriteListener）：捕获数据库写入错误（如 DuplicateKeyException）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class VenueInitializeErrorListener
    implements ItemReadListener<VenueParseResult>,
        ItemProcessListener<VenueParseResult, VenueParseResult>,
        ItemWriteListener<VenueParseResult> {

  /// 最大打印记录数（避免 Chunk 过大时日志爆炸）。
  private static final int MAX_ITEMS_TO_LOG = 20;

  // ========== ItemReadListener ==========

  @Override
  public void onReadError(Exception ex) {
    log.error("[读取错误] 解析数据失败");
    log.error("异常类型: {}", ex.getClass().getSimpleName());
    log.error("异常信息: {}", ex.getMessage());
    if (ex.getCause() != null) {
      log.error("根因: {}", ex.getCause().getMessage());
    }
  }

  // ========== ItemProcessListener ==========

  @Override
  public void onProcessError(VenueParseResult item, Exception ex) {
    log.error("[处理错误] 数据转换/验证失败");
    log.error("失败数据: {}", formatItem(item));
    log.error("异常类型: {}", ex.getClass().getSimpleName());
    log.error("异常信息: {}", ex.getMessage());
  }

  // ========== ItemWriteListener ==========

  @Override
  public void onWriteError(Exception ex, Chunk<? extends VenueParseResult> items) {
    log.error("[写入错误] 数据库操作失败");
    log.error("异常类型: {}", ex.getClass().getSimpleName());
    log.error("异常信息: {}", ex.getMessage());
    log.error("失败 Chunk 包含 {} 条记录:", items.size());

    int count = 0;
    for (VenueParseResult item : items) {
      if (count >= MAX_ITEMS_TO_LOG) {
        log.error("  ... 省略剩余 {} 条记录", items.size() - MAX_ITEMS_TO_LOG);
        break;
      }
      log.error("  [{}] {}", count, formatItem(item));
      count++;
    }
  }

  // ========== 辅助方法 ==========

  /// 格式化 VenueParseResult 关键字段用于日志输出。
  ///
  /// @param item VenueParseResult 实例
  /// @return 格式化的字符串
  private String formatItem(VenueParseResult item) {
    if (item == null) {
      return "null";
    }
    VenueAggregate aggregate = item.aggregate();
    String openalexId = aggregate.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null);
    String issnL = aggregate.getIdentifier(VenueIdentifierType.ISSN_L).orElse(null);
    return String.format(
        "openalexId=%s, displayName=%s, issnL=%s, type=%s, metrics=%d",
        openalexId,
        truncate(aggregate.getDisplayName(), 50),
        issnL,
        aggregate.getVenueType(),
        item.yearlyMetrics().size());
  }

  /// 截断字符串（避免日志过长）。
  ///
  /// @param str 原始字符串
  /// @param maxLength 最大长度
  /// @return 截断后的字符串
  private String truncate(String str, int maxLength) {
    if (str == null) {
      return null;
    }
    if (str.length() <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength) + "...";
  }
}
