package com.patra.ingest.domain.model.vo.plan;

/**
 * 计划元数据值对象,表示从数据源返回的规划信息。
 *
 * <p>封装将采集任务分解为批次所需的信息,同时允许执行阶段重用上游缓存(例如:PubMed WebEnv)。
 *
 * <p>业务约束:
 *
 * <ul>
 *   <li>totalCount必须 >= 0
 *   <li>webEnv和queryKey必须同时存在或同时为空
 * </ul>
 *
 * <p>使用场景:在规划阶段保存数据源元数据,供后续批次执行使用
 *
 * @param totalCount 匹配记录总数
 * @param webEnv PubMed历史服务器令牌(可空)
 * @param queryKey 与WebEnv令牌配对的PubMed查询键(可空)
 */
public record PlanMetadata(int totalCount, String webEnv, String queryKey) {

  public PlanMetadata {
    if (totalCount < 0) {
      throw new IllegalArgumentException("totalCount必须 >= 0");
    }

    boolean hasWebEnv = webEnv != null && !webEnv.isBlank();
    boolean hasQueryKey = queryKey != null && !queryKey.isBlank();
    if (hasWebEnv != hasQueryKey) {
      throw new IllegalArgumentException("webEnv和queryKey必须同时存在或同时为空");
    }
  }

  /**
   * 创建表示无可用结果的空元数据。
   *
   * @return 空元数据
   */
  public static PlanMetadata empty() {
    return new PlanMetadata(0, null, null);
  }

  /**
   * 检查元数据是否包含可在执行期间重用的WebEnv句柄。
   *
   * @return 如果WebEnv和QueryKey都存在则返回true
   */
  public boolean hasWebEnv() {
    return webEnv != null && !webEnv.isBlank();
  }
}
