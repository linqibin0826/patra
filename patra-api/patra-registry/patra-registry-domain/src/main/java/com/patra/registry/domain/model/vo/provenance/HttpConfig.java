package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// HTTP配置值对象,定义HTTP客户端的超时、代理、TLS、重试后处理、幂等性等策略。
/// 
/// **不可变性**:此对象一旦创建不可修改,通过值语义比较相等性。
/// 
/// **业务约束**:
/// 
/// - 配置ID和数据源ID必须为正整数
///   - 生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
///   - Retry-After策略代码(retryAfterPolicyCode)不可为空白,支持IGNORE/RESPECT/CLAMP
///   - 操作类型(operationType)为null时表示适用于所有操作(ALL/HARVEST/UPDATE/BACKFILL)
/// 
/// **业务语义**:
/// 
/// - 基础URL覆盖:允许在运行时覆盖数据源的默认基础URL
///   - 默认请求头:JSON格式的默认HTTP头,与运行时请求头合并
///   - 超时控制:分别控制连接建立、响应读取和端到端总超时
///   - TLS验证:生产环境启用,测试环境可选禁用
///   - 代理支持:支持HTTP和SOCKS5代理
///   - Retry-After处理:遵循HTTP 429/503响应的Retry-After头,支持忽略、遵守或限制上限
///   - 幂等性保护:通过Idempotency-Key头避免重复提交
/// 
/// @param id 配置主键,唯一标识此HTTP配置,必须为正整数
/// @param provenanceId 数据源ID外键,引用`reg_provenance.id`,必须为正整数
/// @param operationType 操作类型,取值为`ALL/HARVEST/UPDATE/BACKFILL`,null表示适用于所有操作
/// @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
/// @param effectiveTo 配置失效时间(不包含),null表示永久有效
/// @param defaultHeadersJson 默认HTTP请求头(JSON格式),与运行时请求头合并,可为null
/// @param timeoutConnectMillis 连接超时(毫秒),用于建立TCP/SSL连接,可为null
/// @param timeoutReadMillis 读取超时(毫秒),用于读取响应体,可为null
/// @param timeoutTotalMillis 总超时(毫秒),端到端请求上限,可为null
/// @param tlsVerifyEnabled TLS证书验证标志,`true`启用验证,`false`禁用(仅测试环境)
/// @param proxyUrlValue 代理URL,格式为`http://user:pass@host:port`或`socks5://host:port`,可为null
/// @param retryAfterPolicyCode Retry-After策略代码(字典值),取值为`IGNORE/RESPECT/CLAMP`,不可为空白
/// @param retryAfterCapMillis Retry-After最大等待上限(毫秒),仅在RESPECT/CLAMP策略下生效,可为null
/// @param idempotencyHeaderName 幂等性请求头名称(如`Idempotency-Key`),用于避免重复提交,可为null
/// @param idempotencyTtlSeconds 幂等性键TTL(秒),仅在支持幂等性时生效,可为null
/// @author Patra Team
/// @since 2.0
public record HttpConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String defaultHeadersJson,
    Integer timeoutConnectMillis,
    Integer timeoutReadMillis,
    Integer timeoutTotalMillis,
    boolean tlsVerifyEnabled,
    String proxyUrlValue,
    String retryAfterPolicyCode,
    Integer retryAfterCapMillis,
    String idempotencyHeaderName,
    Integer idempotencyTtlSeconds) {
  /// 规范构造器,强制执行HTTP配置的业务约束。
/// 
/// 验证规则:
/// 
/// - 配置ID和数据源ID必须为正整数
///   - 生效时间不可为空
///   - Retry-After策略代码不可为空白
///   - 所有字符串字段自动trim去除首尾空白
/// 
/// @throws DomainValidationException 如果验证失败
  public HttpConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      String defaultHeadersJson,
      Integer timeoutConnectMillis,
      Integer timeoutReadMillis,
      Integer timeoutTotalMillis,
      boolean tlsVerifyEnabled,
      String proxyUrlValue,
      String retryAfterPolicyCode,
      Integer retryAfterCapMillis,
      String idempotencyHeaderName,
      Integer idempotencyTtlSeconds) {
    DomainValidationException.positive(id, "HTTP config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String retryAfterTrimmed =
        DomainValidationException.notBlank(retryAfterPolicyCode, "Retry-after policy code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.defaultHeadersJson = defaultHeadersJson;
    this.timeoutConnectMillis = timeoutConnectMillis;
    this.timeoutReadMillis = timeoutReadMillis;
    this.timeoutTotalMillis = timeoutTotalMillis;
    this.tlsVerifyEnabled = tlsVerifyEnabled;
    this.proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
    this.retryAfterPolicyCode = retryAfterTrimmed;
    this.retryAfterCapMillis = retryAfterCapMillis;
    this.idempotencyHeaderName =
        idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
    this.idempotencyTtlSeconds = idempotencyTtlSeconds;
  }
}
