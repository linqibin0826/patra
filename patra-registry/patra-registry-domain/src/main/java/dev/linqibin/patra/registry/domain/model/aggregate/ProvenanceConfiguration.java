package dev.linqibin.patra.registry.domain.model.aggregate;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import dev.linqibin.patra.registry.domain.model.vo.provenance.BatchingConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.HttpConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.PaginationConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.Provenance;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RetryConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

/// 数据源配置聚合根,聚合数据源元数据和多维运营配置的完整视图。
///
/// **一致性边界**:本聚合确保数据源配置的完整性和一致性,所有配置维度(分页、重试、限流等) 围绕数据源这一核心实体组织。
///
/// **业务规则**:
///
/// - 数据源元数据(provenance)是聚合的必需核心,不可为空
///   - 各维度配置(HTTP、重试、限流等)均为可选,根据数据源特性按需配置
///   - 配置作用域遵循优先级规则:TASK级 > OPERATION级 > SOURCE级
///   - 仅当数据源处于激活状态时,配置才被视为完整可用
///
/// **生命周期**:本聚合为只读聚合,用于CQRS读端。通过仓储接口在特定时刻(at参数) 加载完整配置快照,支持时态查询和配置版本管理。
///
/// **字段说明**:
///
/// - `provenance` - 数据源元数据,包含唯一标识、名称、基础URL等核心信息,必需且不可变
///   - `windowOffset` - 时间窗口偏移配置,用于基于时间分段的数据采集,可选
///   - `pagination` - 分页策略配置,定义如何分页获取数据,可选
///   - `http` - HTTP客户端配置,包括超时、请求头、代理等,可选
///   - `batching` - 批处理配置,用于优化详情获取等批量操作,可选
///   - `retry` - 重试策略配置,包含退避算法、最大重试次数等,可选
///   - `rateLimit` - 速率限制配置,用于API节流保护,可选
///
/// @author linqibin
/// @since 0.1.0
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit) {
  /// 规范构造器,强制执行聚合根的业务不变性。
  ///
  /// 确保数据源元数据不可为空,维护聚合的一致性边界。
  ///
  /// @throws DomainValidationException 如果数据源元数据为null,违反聚合根必需核心规则
  public ProvenanceConfiguration {
    DomainValidationException.nonNull(provenance, "Provenance");
  }

  /// 判断是否配置了时间窗口偏移。
  ///
  /// @return 如果存在时间窗口偏移配置则返回true
  public boolean hasWindowOffset() {
    return windowOffset != null;
  }

  /// 判断是否配置了分页策略。
  ///
  /// @return 如果存在分页配置则返回true
  public boolean hasPagination() {
    return pagination != null;
  }

  /// 判断是否配置了HTTP客户端策略。
  ///
  /// @return 如果存在HTTP配置则返回true
  public boolean hasHttpConfig() {
    return http != null;
  }

  /// 判断是否配置了批处理策略。
  ///
  /// @return 如果存在批处理配置则返回true
  public boolean hasBatching() {
    return batching != null;
  }

  /// 判断是否配置了重试策略。
  ///
  /// @return 如果存在重试配置则返回true
  public boolean hasRetry() {
    return retry != null;
  }

  /// 判断是否配置了速率限制策略。
  ///
  /// @return 如果存在速率限制配置则返回true
  public boolean hasRateLimit() {
    return rateLimit != null;
  }

  /// 判断配置是否完整可用。
  ///
  /// 业务规则:仅当数据源元数据存在且处于激活状态时,配置才被视为完整。 各维度策略配置(分页、重试等)为可选项,不影响完整性判断。
  ///
  /// @return 如果数据源存在且激活则返回true
  public boolean isComplete() {
    return provenance != null && provenance.isActive();
  }
}
