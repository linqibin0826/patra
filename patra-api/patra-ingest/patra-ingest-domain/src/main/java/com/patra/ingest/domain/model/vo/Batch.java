package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 批次值对象。
 * <p>
 * 职责：封装单个批次的执行信息，包含查询、参数、游标令牌等。
 * </p>
 * <p>
 * 不变式：
 * <ul>
 *   <li>batchNo >= 1（批次序号从 1 开始）。</li>
 *   <li>query 不能为空。</li>
 * </ul>
 * </p>
 *
 * @param batchNo 批次序号（从 1 开始）
 * @param query 查询字符串（编译后的查询）
 * @param params 查询参数（JSON）
 * @param cursorToken 游标令牌（用于分页，可为空）
 * @param expectedCount 预期数据量（可为空）
 * @author linqibin
 * @since 0.1.0
 */
public record Batch(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer expectedCount
) {
    public Batch {
        if (batchNo < 1) {
            throw new IllegalArgumentException("batchNo 必须 >= 1");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
    }

    /**
     * 创建首批次（无游标）。
     */
    public static Batch first(String query, JsonNode params) {
        return new Batch(1, query, params, null, null);
    }

    /**
     * 创建后续批次（带游标）。
     */
    public static Batch next(int batchNo, String query, JsonNode params, String cursorToken) {
        return new Batch(batchNo, query, params, cursorToken, null);
    }

    /**
     * 是否有游标令牌。
     */
    public boolean hasCursor() {
        return cursorToken != null && !cursorToken.isBlank();
    }
}
