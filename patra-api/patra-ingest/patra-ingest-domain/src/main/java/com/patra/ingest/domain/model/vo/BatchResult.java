package com.patra.ingest.domain.model.vo;

/**
 * 批次执行结果值对象。
 * <p>
 * 职责：封装批次执行的结果，包含成功标志、数据量、游标令牌、错误信息等。
 * </p>
 * <p>
 * 不变式：
 * <ul>
 *   <li>batchNo >= 1。</li>
 *   <li>fetchedCount >= 0。</li>
 *   <li>若 success = false，errorMessage 不能为空。</li>
 * </ul>
 * </p>
 *
 * @param batchNo 批次序号
 * @param success 是否成功
 * @param fetchedCount 实际抓取数据量
 * @param nextCursorToken 下一个游标令牌（用于后续批次）
 * @param errorMessage 错误信息（失败时）
 * @param storageKey 数据存储键（如 OSS 路径）
 * @author linqibin
 * @since 0.1.0
 */
public record BatchResult(
    int batchNo,
    boolean success,
    int fetchedCount,
    String nextCursorToken,
    String errorMessage,
    String storageKey
) {
    public BatchResult {
        if (batchNo < 1) {
            throw new IllegalArgumentException("batchNo 必须 >= 1");
        }
        if (fetchedCount < 0) {
            throw new IllegalArgumentException("fetchedCount 不能为负数");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("失败时 errorMessage 不能为空");
        }
    }

    /**
     * 创建成功的批次结果。
     */
    public static BatchResult success(int batchNo, int fetchedCount, String nextCursorToken, String storageKey) {
        return new BatchResult(batchNo, true, fetchedCount, nextCursorToken, null, storageKey);
    }

    /**
     * 创建失败的批次结果。
     */
    public static BatchResult failure(int batchNo, String errorMessage) {
        return new BatchResult(batchNo, false, 0, null, errorMessage, null);
    }

    /**
     * 是否有下一个游标（继续分页）。
     */
    public boolean hasNextCursor() {
        return nextCursorToken != null && !nextCursorToken.isBlank();
    }
}
