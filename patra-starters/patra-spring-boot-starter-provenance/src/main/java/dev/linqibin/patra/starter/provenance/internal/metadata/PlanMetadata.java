package dev.linqibin.patra.starter.provenance.internal.metadata;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// 计划元数据基类
///
/// 封装将采集任务分解为批次所需的信息,同时允许执行阶段重用上游缓存。
///
/// 设计原则:
///
/// - 使用继承体系支持不同数据源的特定需求
///   - 提供抽象方法供子类实现数据源特定逻辑
///   - 保持类型安全,避免使用 Map&lt;String, Object&gt;
///
/// 业务约束:
///
/// - totalCount 必须 >= 0
///   - dataSourceType 不能为空
///
/// @author linqibin
/// @since 0.1.0
public abstract class PlanMetadata {

  private final String dataSourceType;
  private final int totalCount;
  private final Instant plannedAt;
  private final Map<String, Object> extensionMetadata;

  protected PlanMetadata(String dataSourceType, int totalCount) {
    if (dataSourceType == null || dataSourceType.isBlank()) {
      throw new IllegalArgumentException("dataSourceType 不能为空");
    }
    if (totalCount < 0) {
      throw new IllegalArgumentException("totalCount 必须 >= 0");
    }
    this.dataSourceType = dataSourceType;
    this.totalCount = totalCount;
    this.plannedAt = Instant.now();
    this.extensionMetadata = new HashMap<>();
  }

  /// 获取数据源类型
  public String dataSourceType() {
    return dataSourceType;
  }

  /// 获取总记录数
  public int totalCount() {
    return totalCount;
  }

  /// 获取计划时间
  public Instant plannedAt() {
    return plannedAt;
  }

  /// 获取扩展元数据(不可变)
  public Map<String, Object> extensionMetadata() {
    return Collections.unmodifiableMap(extensionMetadata);
  }

  /// 检查元数据是否包含会话令牌
  ///
  /// 会话令牌用于在执行期间重用上游缓存(如 PubMed 的 WebEnv)
  ///
  /// @return 如果包含会话令牌则返回 true
  public abstract boolean hasSessionToken();

  /// 创建表示无可用结果的空元数据
  public static PlanMetadata empty(String dataSourceType) {
    return new EmptyPlanMetadata(dataSourceType);
  }

  /// 空元数据实现
  private static class EmptyPlanMetadata extends PlanMetadata {
    EmptyPlanMetadata(String dataSourceType) {
      super(dataSourceType, 0);
    }

    @Override
    public boolean hasSessionToken() {
      return false;
    }
  }
}
