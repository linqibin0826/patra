package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 表示具有时态有效性约束的实体。
 *
 * <p>实现此接口的实体具有由 {@code effectiveFrom} 和 {@code effectiveTo} 时间戳定义的有效期, 支持时态查询和配置切片。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TemporalEntity {

  /**
   * 返回此实体生效的包含性起始时间戳。
   *
   * @return 生效起始时刻,永不为 null
   */
  Instant effectiveFrom();

  /**
   * 返回此实体失效的排他性结束时间戳。
   *
   * @return 生效结束时刻,null 表示开放式(永不失效)
   */
  Instant effectiveTo();

  /**
   * 检查此实体在给定时刻是否有效。
   *
   * <p>当时刻落在有效期内时,实体有效: {@code effectiveFrom <= instant < effectiveTo}。
   *
   * @param instant 要检查的时间点,不能为 null
   * @return 如果实体在给定时刻有效则返回 true
   * @throws DomainValidationException 如果 instant 为 null
   */
  default boolean isEffectiveAt(Instant instant) {
    DomainValidationException.nonNull(instant, "Instant");
    boolean afterStart = !instant.isBefore(effectiveFrom());
    boolean beforeEnd = effectiveTo() == null || instant.isBefore(effectiveTo());
    return afterStart && beforeEnd;
  }
}
