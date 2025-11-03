package com.patra.registry.api.dto.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 表达式字段的能力元数据。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>provenanceId - 拥有该能力的内部数据源标识符
 *   <li>operationType - 与能力关联的操作类型鉴别器
 *   <li>fieldKey - 能力所属的表达式字段键
 *   <li>opsJson - 字段允许的操作序列化内容
 *   <li>negatableOpsJson - 支持否定的操作序列化内容
 *   <li>supportsNot - 字段是否支持逻辑 NOT
 *   <li>termMatchesJson - 基于术语搜索的匹配运算符序列化内容
 *   <li>termCaseSensitiveAllowed - 术语操作是否可以区分大小写
 *   <li>termAllowBlank - 是否允许空白术语
 *   <li>termMinLength - 术语输入的最小允许长度
 *   <li>termMaxLength - 术语输入的最大允许长度
 *   <li>termPattern - 强制术语格式的可选正则表达式
 *   <li>inMaxSize - IN 子句的最大大小
 *   <li>inCaseSensitiveAllowed - IN 值是否可以区分大小写
 *   <li>rangeKindCode - 范围求值策略鉴别器
 *   <li>rangeAllowOpenStart - 是否允许开放起始范围
 *   <li>rangeAllowOpenEnd - 是否允许开放结束范围
 *   <li>rangeAllowClosedAtInfinity - 无穷边界是否可以闭合
 *   <li>dateMin - 支持的最小日期值
 *   <li>dateMax - 支持的最大日期值
 *   <li>datetimeMin - 支持的最小时间戳值
 *   <li>datetimeMax - 支持的最大时间戳值
 *   <li>numberMin - 最小数值
 *   <li>numberMax - 最大数值
 *   <li>existsSupported - 是否支持 EXISTS 运算符
 *   <li>tokenKindsJson - 用于标记化查询的标记类型序列化内容
 *   <li>tokenValuePattern - 标记值的可选正则表达式
 *   <li>effectiveFrom - 能力生效的时间戳
 *   <li>effectiveTo - 能力保持有效的截止时间戳
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCapabilityResp(
    Long provenanceId,
    String operationType,
    String fieldKey,
    String opsJson,
    String negatableOpsJson,
    boolean supportsNot,
    String termMatchesJson,
    boolean termCaseSensitiveAllowed,
    boolean termAllowBlank,
    int termMinLength,
    int termMaxLength,
    String termPattern,
    int inMaxSize,
    boolean inCaseSensitiveAllowed,
    String rangeKindCode,
    boolean rangeAllowOpenStart,
    boolean rangeAllowOpenEnd,
    boolean rangeAllowClosedAtInfinity,
    LocalDate dateMin,
    LocalDate dateMax,
    Instant datetimeMin,
    Instant datetimeMax,
    BigDecimal numberMin,
    BigDecimal numberMax,
    boolean existsSupported,
    String tokenKindsJson,
    String tokenValuePattern,
    Instant effectiveFrom,
    Instant effectiveTo) {}
