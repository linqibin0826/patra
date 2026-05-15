package dev.linqibin.patra.registry.domain.model.vo.provenance;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 重试配置值对象,定义重试尝试次数、退避策略、熔断器阈值和错误分类规则。
///
/// **不可变性**:此对象一旦创建不可修改,通过值语义比较相等性。
///
/// **业务约束**:
///
/// - 配置ID和数据源ID必须为正整数
///   - 生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
///   - 退避策略类型(backoffPolicyTypeCode)不可为空白,支持FIXED/LINEAR/EXPONENTIAL
///   - 操作类型(operationType)为null时表示适用于所有操作(HARVEST/UPDATE/BACKFILL)
///   - 与HTTP配置的Retry-After策略配合工作,共同控制429/5xx/网络/客户端错误
///
/// **业务语义**:
///
/// - 重试次数控制:maxRetryTimes为0表示不重试,null表示使用默认值
///   - 退避策略:FIXED(固定延迟)、LINEAR(线性递增)、EXPONENTIAL(指数退避)
///   - 抖动机制:jitterFactorRatio添加随机性,防止雷鸣般的重试风暴
///   - HTTP状态码分类:retryHttpStatusJson定义可重试状态码,giveupHttpStatusJson定义立即放弃的状态码
///   - 网络错误处理:retryOnNetworkError控制连接超时/重置等网络级错误是否重试
///   - 熔断器保护:连续失败N次后打开熔断器,冷却期后半开尝试
///
/// @param id 配置主键,唯一标识此重试配置,必须为正整数
/// @param provenanceId 数据源ID外键,引用`reg_provenance.id`,必须为正整数
/// @param operationType 操作类型,取值为`HARVEST/UPDATE/BACKFILL`,null表示适用于所有操作
/// @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
/// @param effectiveTo 配置失效时间(不包含),null表示永久有效
/// @param maxRetryTimes 最大重试次数,0表示不重试,null表示使用默认值
/// @param backoffPolicyTypeCode 退避策略类型代码(字典值),定义退避策略(`FIXED/LINEAR/EXPONENTIAL`),不可为空白
/// @param initialDelayMillis 首次重试前的初始延迟(毫秒),必须非负
/// @param maxDelayMillis 最大延迟上限(毫秒),防止退避延迟无限增长
/// @param expMultiplierValue 指数退避乘数(如2.0表示每次翻倍),仅适用于EXPONENTIAL策略
/// @param jitterFactorRatio 抖动因子比率(0.0-1.0),添加随机性防止重试风暴
/// @param retryHttpStatusJson 触发重试的HTTP状态码(JSON数组,如`[429, 503]`),null表示使用默认值
/// @param giveupHttpStatusJson 立即放弃的HTTP状态码(JSON数组,如`[400, 401, 403]`),null表示无
/// @param retryOnNetworkError 是否对网络级错误重试(连接超时/重置),`true`重试,`false`快速失败
/// @param circuitBreakThreshold 熔断器失败阈值,连续失败N次后打开熔断器,null表示禁用熔断器
/// @param circuitCooldownMillis 熔断器冷却期(毫秒),打开后等待此时长再半开尝试,null表示使用默认值
/// @author linqibin
/// @since 0.1.0
public record RetryConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxRetryTimes,
    String backoffPolicyTypeCode,
    Integer initialDelayMillis,
    Integer maxDelayMillis,
    Double expMultiplierValue,
    Double jitterFactorRatio,
    String retryHttpStatusJson,
    String giveupHttpStatusJson,
    boolean retryOnNetworkError,
    Integer circuitBreakThreshold,
    Integer circuitCooldownMillis) {
  /// 规范构造器,强制执行重试配置的业务约束。
  ///
  /// 验证规则:
  ///
  /// - 配置ID和数据源ID必须为正整数
  ///   - 生效时间不可为空
  ///   - 退避策略类型不可为空白
  ///   - 所有字符串字段自动trim去除首尾空白
  ///
  /// @throws DomainValidationException 如果验证失败
  public RetryConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      Integer maxRetryTimes,
      String backoffPolicyTypeCode,
      Integer initialDelayMillis,
      Integer maxDelayMillis,
      Double expMultiplierValue,
      Double jitterFactorRatio,
      String retryHttpStatusJson,
      String giveupHttpStatusJson,
      boolean retryOnNetworkError,
      Integer circuitBreakThreshold,
      Integer circuitCooldownMillis) {
    DomainValidationException.positive(id, "Retry config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String backoffTrimmed =
        DomainValidationException.notBlank(backoffPolicyTypeCode, "Backoff policy type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.maxRetryTimes = maxRetryTimes;
    this.backoffPolicyTypeCode = backoffTrimmed;
    this.initialDelayMillis = initialDelayMillis;
    this.maxDelayMillis = maxDelayMillis;
    this.expMultiplierValue = expMultiplierValue;
    this.jitterFactorRatio = jitterFactorRatio;
    this.retryHttpStatusJson = retryHttpStatusJson;
    this.giveupHttpStatusJson = giveupHttpStatusJson;
    this.retryOnNetworkError = retryOnNetworkError;
    this.circuitBreakThreshold = circuitBreakThreshold;
    this.circuitCooldownMillis = circuitCooldownMillis;
  }
}
