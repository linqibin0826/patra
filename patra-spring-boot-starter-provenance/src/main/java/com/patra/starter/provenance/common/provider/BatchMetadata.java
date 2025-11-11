package com.patra.starter.provenance.common.provider;

/**
 * 批次元数据,用于日志记录、监控和游标管理
 *
 * <p>本记录仅作为批次元数据使用。要构建 API 请求,请使用 {@link ProviderRequest} 中的 {@link BatchExecutionParams}。
 *
 * <p><b>职责边界:</b>
 *
 * <ul>
 *   <li>提供批次编号用于日志和监控
 *   <li>管理上游API返回的游标令牌
 *   <li>支持批次执行状态的追踪
 *   <li><b>不</b>用于构建API请求参数
 * </ul>
 *
 * @param batchNo 执行运行中的顺序批次号(从1开始)
 * @param cursorToken 上游数据源提供的恢复游标(可为 null)
 * @author linqibin
 * @since 0.1.0
 */
public record BatchMetadata(int batchNo, String cursorToken) {

  /**
   * 创建记录时验证不变式
   *
   * @param batchNo 顺序批次号(必须 >= 1)
   * @param cursorToken 恢复游标令牌
   */
  public BatchMetadata {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1, got: " + batchNo);
    }
  }

  /**
   * 创建第一批次的元数据(无游标)
   *
   * @return 批次号为1且无游标的元数据
   */
  public static BatchMetadata first() {
    return new BatchMetadata(1, null);
  }

  /**
   * 创建带更新游标令牌的元数据
   *
   * @param newCursorToken 新游标令牌
   * @return 带更新游标的新元数据实例
   */
  public BatchMetadata withCursorToken(String newCursorToken) {
    return new BatchMetadata(batchNo, newCursorToken);
  }
}
