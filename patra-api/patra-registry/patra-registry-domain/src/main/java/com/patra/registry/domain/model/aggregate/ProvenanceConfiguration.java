package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

/**
 * 来源配置聚合根,提供来源和多个配置维度的整合只读视图。
 *
 * <p>用于 CQRS 读端,表示特定时间点的有效配置, 包括 HTTP 策略、重试、速率限制和其他运营设置。
 *
 * <p>作用域优先级:TASK 级切片覆盖 SOURCE 级默认值。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>provenance - 包含来源元数据的核心来源实体;永不为 null
 *   <li>windowOffset - 基于时间分段的窗口偏移配置;可为 null
 *   <li>pagination - 分页策略配置;可为 null
 *   <li>http - HTTP 客户端配置,包括超时和头部;可为 null
 *   <li>batching - 详情获取操作的批处理配置;可为 null
 *   <li>retry - 重试策略配置,包含退避策略;可为 null
 *   <li>rateLimit - API 节流的速率限制配置;可为 null
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit) {
  /**
   * 紧凑的规范构造器,强制 provenance 非空不变性。
   *
   * @throws DomainValidationException 如果 provenance 为 null
   */
  public ProvenanceConfiguration {
    DomainValidationException.nonNull(provenance, "Provenance");
  }

  /**
   * 检查窗口偏移配置是否存在。
   *
   * @return 如果配置了窗口偏移则返回 true
   */
  public boolean hasWindowOffset() {
    return windowOffset != null;
  }

  /**
   * 检查分页配置是否存在。
   *
   * @return 如果配置了分页则返回 true
   */
  public boolean hasPagination() {
    return pagination != null;
  }

  /**
   * 检查 HTTP 配置是否存在。
   *
   * @return 如果存在 HTTP 配置则返回 true
   */
  public boolean hasHttpConfig() {
    return http != null;
  }

  /**
   * 检查批处理配置是否存在。
   *
   * @return 如果配置了批处理则返回 true
   */
  public boolean hasBatching() {
    return batching != null;
  }

  /**
   * 检查重试配置是否存在。
   *
   * @return 如果配置了重试策略则返回 true
   */
  public boolean hasRetry() {
    return retry != null;
  }

  /**
   * 检查速率限制配置是否存在。
   *
   * @return 如果配置了速率限制则返回 true
   */
  public boolean hasRateLimit() {
    return rateLimit != null;
  }

  /**
   * 检查配置是否完整且激活。
   *
   * <p>如果 provenance 非空且激活,则认为配置完整。单个策略配置(分页、重试等)是可选的。
   *
   * @return 如果 provenance 存在且激活则返回 true
   */
  public boolean isComplete() {
    return provenance != null && provenance.isActive();
  }
}
