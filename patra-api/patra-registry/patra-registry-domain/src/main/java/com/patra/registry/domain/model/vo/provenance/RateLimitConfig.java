package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 速率限制配置值对象,定义QPS/令牌桶、突发容量、最大并发等流控策略。
 *
 * <p><strong>不可变性</strong>:此对象一旦创建不可修改,通过值语义比较相等性。
 *
 * <p><strong>业务约束</strong>:
 *
 * <ul>
 *   <li>配置ID和数据源ID必须为正整数
 *   <li>生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
 *   <li>操作类型(operationType)为null时表示适用于所有操作(HARVEST/UPDATE/BACKFILL)
 *   <li>所有限流参数为可选项,null表示不限制
 * </ul>
 *
 * <p><strong>业务语义</strong>:
 *
 * <ul>
 *   <li>并发控制:maxConcurrentRequests限制全局并发请求数,防止过载
 *   <li>凭证级QPS:perCredentialQpsLimit限制每个API密钥/凭证的每秒查询数
 *   <li>粒度选择:支持按密钥/端点/IP/任务等多种粒度限流
 *   <li>平滑与自适应:可配合服务端速率头(Retry-After, RateLimit-*)动态调整
 *   <li>令牌桶模式:支持突发容量,允许短时间内超出平均速率
 *   <li>与重试和HTTP配置协同:共同保护API稳定性和遵守服务商限制
 * </ul>
 *
 * @param id 配置主键,唯一标识此速率限制配置,必须为正整数
 * @param provenanceId 数据源ID外键,引用{@code reg_provenance.id},必须为正整数
 * @param operationType 操作类型,取值为{@code HARVEST/UPDATE/BACKFILL},null表示适用于所有操作
 * @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
 * @param effectiveTo 配置失效时间(不包含),null表示永久有效
 * @param maxConcurrentRequests 全局最大并发请求数,null表示无并发限制
 * @param perCredentialQpsLimit 每个凭证/API密钥的QPS(每秒查询数)限制,null表示无凭证级限制
 * @author Patra Team
 * @since 2.0
 */
public record RateLimitConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit) {
  /**
   * 规范构造器,强制执行速率限制配置的业务约束。
   *
   * <p>验证规则:
   *
   * <ul>
   *   <li>配置ID和数据源ID必须为正整数
   *   <li>生效时间不可为空
   *   <li>所有字符串字段自动trim去除首尾空白
   * </ul>
   *
   * @throws DomainValidationException 如果验证失败
   */
  public RateLimitConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      Integer maxConcurrentRequests,
      Integer perCredentialQpsLimit) {
    DomainValidationException.positive(id, "Rate limit config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.perCredentialQpsLimit = perCredentialQpsLimit;
  }
}
