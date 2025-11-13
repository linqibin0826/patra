package com.patra.ingest.domain.model.vo.batch;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.Map;

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
 *   <li><b>基于会话令牌</b>: 使用 {@code sessionTokens} 存储数据源特定的会话令牌 (例如 PubMed 的 webEnv/queryKey)
 * </ul>
 *
 * @param batchNo 批次序号 (从 1 开始)
 * @param query 已编译的查询字符串
 * @param params 查询参数(JSON 格式)
 * @param cursorToken 用于游标分页的游标令牌 (可为空)
 * @param pageNo 用于页码分页的页码 (从 1 开始, 游标分页时为空)
 * @param pageSize 页大小或预期获取数量 (可为空)
 * @param sessionTokens 数据源特定的会话令牌 (可为空, 例如 PubMed History Server 的 webEnv/queryKey)
 * @author linqibin
 * @since 0.1.0
 */
public record Batch(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer pageNo,
    Integer pageSize,
    Map<String, String> sessionTokens) {
  public Batch {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1");
    }
    // 确保 sessionTokens 不为 null，使用不可变空 Map
    if (sessionTokens == null) {
      sessionTokens = Collections.emptyMap();
    } else {
      // 创建不可变副本以保证 record 的不变性
      sessionTokens = Collections.unmodifiableMap(sessionTokens);
    }
  }

  /** 创建第一个批次,不包含分页元数据(遗留方法)。 */
  public static Batch first(String query, JsonNode params) {
    return new Batch(1, query, params, null, null, null, null);
  }

  /** 创建基于页码的批次(例如 PubMed 的 retstart/retmax)。 */
  public static Batch withPage(
      int batchNo, String query, JsonNode params, int pageNo, int pageSize) {
    return new Batch(batchNo, query, params, null, pageNo, pageSize, null);
  }

  /** 创建基于游标的批次(例如 EPMC 的 cursorMark)。 */
  public static Batch withToken(
      int batchNo, String query, JsonNode params, String cursorToken, Integer pageSize) {
    return new Batch(batchNo, query, params, cursorToken, null, pageSize, null);
  }

  /**
   * 创建带会话令牌的批次（用于 PubMed History Server）。
   *
   * @param batchNo 批次序号
   * @param query 查询字符串
   * @param params 查询参数
   * @param pageNo 页码（offset）
   * @param pageSize 页大小
   * @param webEnv PubMed History Server 会话令牌
   * @param queryKey PubMed History Server 查询键
   * @return 包含会话令牌的批次
   */
  public static Batch withPageAndSession(
      int batchNo,
      String query,
      JsonNode params,
      int pageNo,
      int pageSize,
      String webEnv,
      String queryKey) {
    Map<String, String> sessionTokens = Map.of(
        "webEnv", webEnv,
        "queryKey", queryKey
    );
    return new Batch(batchNo, query, params, null, pageNo, pageSize, sessionTokens);
  }

  /** 指示是否存在游标令牌。 */
  public boolean hasCursor() {
    return cursorToken != null && !cursorToken.isBlank();
  }

  /** 指示是否存在会话令牌。 */
  public boolean hasSessionTokens() {
    return sessionTokens != null && !sessionTokens.isEmpty();
  }
}
