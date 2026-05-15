package dev.linqibin.patra.ingest.domain.model.vo.batch;

/// 单次执行批次值对象（纯领域模型）。
///
/// 封装批次的核心业务概念：批次编号、查询和分页范围。
///
/// **设计原则**：
///
/// - 只包含业务概念，不包含技术细节
///   - 使用通用的分页抽象（offset/limit），而非数据源特定参数
///   - 数据源特定的参数映射由 Infrastructure 层的 ParameterMapper 处理
///
/// 不变式:
///
/// - `batchNo` >= 1
///   - `query` 可以为 null 或空（某些数据源如 PubMed 允许仅使用日期过滤器而无搜索词）
///   - `offset` >= 0
///   - `limit` > 0
///
/// **示例**：
///
/// ```java
/// // 第1批：获取前500条 (offset=0, limit=500)
/// Batch batch1 = new Batch(1, "cancer AND 2024", 0, 500);
///
/// // 第2批：获取第501-1000条 (offset=500, limit=500)
/// Batch batch2 = new Batch(2, "cancer AND 2024", 500, 500);
/// ```
///
/// @param batchNo 批次序号 (从 1 开始)
/// @param query 查询字符串
/// @param offset 起始偏移量 (从 0 开始)
/// @param limit 获取数量 (必须 > 0)
/// @author linqibin
/// @since 0.1.0
public record Batch(int batchNo, String query, int offset, int limit) {

  /// 紧凑构造器：验证不变式。
  public Batch {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1, got: " + batchNo);
    }
    // query 可以为 null 或空：某些数据源（如 PubMed）允许仅使用日期过滤器而无搜索词
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0, got: " + limit);
    }
  }

  /// 计算批次的结束位置（不包含）。
  ///
  /// @return offset + limit
  public int endOffset() {
    return offset + limit;
  }

  /// 判断批次是否包含指定的记录位置。
  ///
  /// @param recordPosition 记录位置（从0开始）
  /// @return 如果在范围内返回 true
  public boolean contains(int recordPosition) {
    return recordPosition >= offset && recordPosition < endOffset();
  }
}
