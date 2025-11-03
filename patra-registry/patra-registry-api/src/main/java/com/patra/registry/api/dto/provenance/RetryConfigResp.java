package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 数据源 API 调用的重试和退避配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 重试配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 配置应用的操作类型鉴别器
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>maxRetryTimes - 不包括首次尝试的最大重试次数
 *   <li>backoffPolicyTypeCode - 退避策略(FIXED/EXPONENTIAL/EXP_JITTER/LINEAR)
 *   <li>initialDelayMillis - 首次重试前的初始延迟(毫秒)
 *   <li>maxDelayMillis - 单次重试延迟的上限(毫秒)
 *   <li>expMultiplierValue - 指数退避应用的乘数
 *   <li>jitterFactorRatio - 用于随机化的抖动比率(0-1)
 *   <li>retryHttpStatusJson - 触发重试的序列化 HTTP 状态码
 *   <li>giveupHttpStatusJson - 停止重试的序列化 HTTP 状态码
 *   <li>retryOnNetworkError - 网络异常是否触发重试
 *   <li>circuitBreakThreshold - 激活熔断器的失败阈值
 *   <li>circuitCooldownMillis - 熔断器跳闸后的冷却时间(毫秒)
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RetryConfigResp(
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
    Integer circuitCooldownMillis) {}
