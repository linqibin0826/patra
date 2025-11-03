package com.patra.ingest.domain.model.vo.batch;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单次执行批次值对象。
 *
 * <p>封装已编译的查询、参数和分页元数据。
 *
 * <p>不变式:
 *
 * <ul>
 *   <li>{@code batchNo} >= 1
 *   <li>{@code query} 不能为空
 * </ul>
 *
 * <p>分页模式:
 *
 * <ul>
 *   <li><b>基于页码</b>: 使用 {@code pageNo} 和 {@code pageSize} (例如 PubMed 的 retstart/retmax)
 *   <li><b>基于游标</b>: 使用 {@code cursorToken} 和可选的 {@code pageSize} (例如 EPMC 的 cursorMark)
 * </ul>
 *
 * @param batchNo 批次序号 (从 1 开始)
 * @param query 已编译的查询字符串
 * @param params 查询参数(JSON 格式)
 * @param cursorToken 用于游标分页的游标令牌 (可为空)
 * @param pageNo 用于页码分页的页码 (从 1 开始, 游标分页时为空)
 * @param pageSize 页大小或预期获取数量 (可为空)
 * @author linqibin
 * @since 0.1.0
 */
public record Batch(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer pageNo,
    Integer pageSize) {
  public Batch {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1");
    }
  }

  /** 创建第一个批次,不包含分页元数据(遗留方法)。 */
  public static Batch first(String query, JsonNode params) {
    return new Batch(1, query, params, null, null, null);
  }

  /** 创建基于页码的批次(例如 PubMed 的 retstart/retmax)。 */
  public static Batch withPage(
      int batchNo, String query, JsonNode params, int pageNo, int pageSize) {
    return new Batch(batchNo, query, params, null, pageNo, pageSize);
  }

  /** 创建基于游标的批次(例如 EPMC 的 cursorMark)。 */
  public static Batch withToken(
      int batchNo, String query, JsonNode params, String cursorToken, Integer pageSize) {
    return new Batch(batchNo, query, params, cursorToken, null, pageSize);
  }

  /** 指示是否存在游标令牌。 */
  public boolean hasCursor() {
    return cursorToken != null && !cursorToken.isBlank();
  }
}
