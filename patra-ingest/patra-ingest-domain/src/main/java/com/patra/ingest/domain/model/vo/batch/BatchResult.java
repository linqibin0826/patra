package com.patra.ingest.domain.model.vo.batch;

/**
 * 批次执行结果 Value Object。
 *
 * <p>描述单个批次执行的结果,包含成功标识、数据计数、游标令牌、错误信息和存储位置。
 *
 * <p>不变性约束:
 *
 * <ul>
 *   <li>{@code batchNo} >= 1 — 批次序号从 1 开始
 *   <li>{@code fetchedCount} >= 0 — 获取记录数不能为负
 *   <li>当 {@code success} 为 {@code false} 时,{@code errorMessage} 必须提供
 * </ul>
 *
 * <p><b>业务语义:</b>
 *
 * <ul>
 *   <li>成功批次包含获取的记录数和下一批次游标
 *   <li>失败批次包含错误详情,其他字段为 null 或 0
 *   <li>存储键指向对象存储路径(如 OSS/S3)
 * </ul>
 *
 * @param batchNo 批次序号(从 1 开始)
 * @param success 成功标识
 * @param fetchedCount 获取的记录数
 * @param nextCursorToken 下一批次游标令牌(可为 null)
 * @param errorMessage 错误详情(仅失败时提供)
 * @param storageKey 存储位置(对象存储路径)
 * @author linqibin
 * @since 0.1.0
 */
public record BatchResult(
    int batchNo,
    boolean success,
    int fetchedCount,
    String nextCursorToken,
    String errorMessage,
    String storageKey) {
  public BatchResult {
    if (batchNo < 1) {
      throw new IllegalArgumentException("批次序号必须 >= 1");
    }
    if (fetchedCount < 0) {
      throw new IllegalArgumentException("获取记录数不能为负数");
    }
    if (!success && (errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("失败批次必须提供错误信息");
    }
  }

  /** 工厂方法: 创建成功的批次结果。 */
  public static BatchResult success(
      int batchNo, int fetchedCount, String nextCursorToken, String storageKey) {
    return new BatchResult(batchNo, true, fetchedCount, nextCursorToken, null, storageKey);
  }

  /** 工厂方法: 创建失败的批次结果。 */
  public static BatchResult failure(int batchNo, String errorMessage) {
    return new BatchResult(batchNo, false, 0, null, errorMessage, null);
  }

  /** 判断是否有下一批次游标用于继续分页。 */
  public boolean hasNextCursor() {
    return nextCursorToken != null && !nextCursorToken.isBlank();
  }
}
