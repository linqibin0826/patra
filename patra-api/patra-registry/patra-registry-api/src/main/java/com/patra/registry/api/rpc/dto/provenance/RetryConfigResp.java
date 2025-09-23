package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 重试与退避（Retry & Backoff）配置响应 DTO。<br>
 * <p>对应表：reg_prov_retry_cfg。用于控制失败请求的重试行为、退避计算与熔断。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code maxRetryTimes} 最大重试次数（不含首次请求）。</li>
 *   <li>{@code backoffPolicyTypeCode} 退避策略（FIXED / EXPONENTIAL / EXP_JITTER / LINEAR）。</li>
 *   <li>{@code initialDelayMillis} 初始延迟毫秒（第 1 次重试等待）。</li>
 *   <li>{@code maxDelayMillis} 单次重试最大延迟上限。</li>
 *   <li>{@code expMultiplierValue} 指数退避倍率（EXP 策略）。</li>
 *   <li>{@code jitterFactorRatio} 抖动因子（0~1，随机扰动幅度比例）。</li>
 *   <li>{@code retryHttpStatusJson} 需要重试的 HTTP 状态码列表 JSON（数组或范围描述）。</li>
 *   <li>{@code giveupHttpStatusJson} 遇到即放弃（不再重试）状态码列表 JSON。</li>
 *   <li>{@code retryOnNetworkError} 是否对网络异常（超时/连接重置）执行重试。</li>
 *   <li>{@code circuitBreakThreshold} 熔断阈值（在窗口内连续失败到达后触发熔断）。</li>
 *   <li>{@code circuitCooldownMillis} 熔断后冷却时间（毫秒），结束后进入半开。</li>
 * </ul>
 */
public record RetryConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务子键 */
        String taskTypeKey,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 最大重试次数 */
        Integer maxRetryTimes,
        /** 退避策略类型编码 */
        String backoffPolicyTypeCode,
        /** 初始延迟毫秒 */
        Integer initialDelayMillis,
        /** 最大延迟毫秒 */
        Integer maxDelayMillis,
        /** 指数倍率 */
        Double expMultiplierValue,
        /** 抖动因子（0~1） */
        Double jitterFactorRatio,
        /** 需要重试状态码 JSON */
        String retryHttpStatusJson,
        /** 放弃状态码 JSON */
        String giveupHttpStatusJson,
        /** 网络错误是否重试 */
        boolean retryOnNetworkError,
        /** 熔断阈值 */
        Integer circuitBreakThreshold,
        /** 熔断冷却毫秒 */
        Integer circuitCooldownMillis
) {
}
