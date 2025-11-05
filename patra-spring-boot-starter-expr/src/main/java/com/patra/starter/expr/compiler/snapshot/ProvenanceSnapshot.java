package com.patra.starter.expr.compiler.snapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 数据来源（Provenance）配置快照记录。
 *
 * <p>表示某个特定时间点捕获的完整 Provenance 配置，包括：
 * <ul>
 *   <li>身份信息（ID、代码、名称）
 *   <li>作用域信息（SOURCE/TASK 级别）
 *   <li>操作类型（HARVEST/UPDATE 等）
 *   <li>字段定义字典
 *   <li>字段能力矩阵（支持的操作符、约束等）
 *   <li>API 参数映射
 *   <li>渲染规则列表
 * </ul>
 *
 * <p><b>不可变性</b>：此 Record 的所有集合字段在构造时会复制为不可变副本，确保快照的一致性。
 *
 * <p><b>版本控制</b>：通过 {@code version} 和 {@code capturedAt} 字段追踪快照的版本和捕获时间。
 *
 * @param identity 身份信息
 * @param scope 作用域信息
 * @param operation 操作类型信息
 * @param version 配置版本号
 * @param capturedAt 快照捕获时间
 * @param fieldDictionary 字段定义字典（字段键 → 字段定义）
 * @param capabilityMatrix 能力矩阵（字段键 → 字段能力）
 * @param apiParameterMap API 参数映射（标准键 → API 参数）
 * @param renderRules 渲染规则列表
 *
 * @author Patra Team
 * @since 0.1.0
 */
public record ProvenanceSnapshot(
    Identity identity,
    Scope scope,
    Operation operation,
    long version,
    Instant capturedAt,
    Map<String, FieldDefinition> fieldDictionary,
    Map<String, Capability> capabilityMatrix,
    Map<String, ApiParameter> apiParameterMap,
    List<RenderRule> renderRules) {

  public ProvenanceSnapshot {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(capturedAt, "capturedAt");
    Objects.requireNonNull(fieldDictionary, "fieldDictionary");
    Objects.requireNonNull(capabilityMatrix, "capabilityMatrix");
    Objects.requireNonNull(apiParameterMap, "apiParameterMap");
    Objects.requireNonNull(renderRules, "renderRules");
    fieldDictionary = Map.copyOf(fieldDictionary);
    capabilityMatrix = Map.copyOf(capabilityMatrix);
    apiParameterMap = Map.copyOf(apiParameterMap);
    renderRules = List.copyOf(renderRules);
  }

  /**
   * Provenance 身份信息记录。
   *
   * @param provenanceId 数据来源 ID
   * @param code 数据来源代码（如 "PUBMED", "EPMC"）
   * @param name 数据来源名称
   */
  public record Identity(Long provenanceId, String code, String name) {
    public Identity {
      Objects.requireNonNull(code, "code");
    }
  }

  /**
   * 作用域信息记录，定义配置的应用范围。
   *
   * @param scopeCode 作用域代码（如 "SOURCE", "TASK"）
   * @param operationTypeKey 操作类型键（可选，用于 TASK 级作用域）
   */
  public record Scope(String scopeCode, String operationTypeKey) {
    /** 创建 SOURCE 级作用域（全局作用域） */
    public static Scope sourceScope() {
      return new Scope("SOURCE", null);
    }
  }

  /**
   * 操作类型信息记录。
   *
   * @param code 操作代码（如 "SEARCH", "DETAIL"）
   * @param defaultTimezone 默认时区（用于日期时间处理）
   */
  public record Operation(String code, String defaultTimezone) {
    public Operation {
      Objects.requireNonNull(code, "code");
    }
  }

  /**
   * 字段定义记录，描述字段的元数据和特性。
   *
   * @param fieldKey 字段键（唯一标识符）
   * @param displayName 显示名称
   * @param description 字段描述
   * @param dataType 数据类型
   * @param cardinality 基数（单值/多值）
   * @param exposable 是否可暴露给外部
   * @param dateField 是否为日期字段
   */
  public record FieldDefinition(
      String fieldKey,
      String displayName,
      String description,
      DataType dataType,
      Cardinality cardinality,
      boolean exposable,
      boolean dateField) {
    public FieldDefinition {
      Objects.requireNonNull(fieldKey, "fieldKey");
      Objects.requireNonNull(dataType, "dataType");
      Objects.requireNonNull(cardinality, "cardinality");
    }
  }

  /** 数据类型枚举 */
  public enum DataType {
    /** 日期类型 */
    DATE,
    /** 日期时间类型 */
    DATETIME,
    /** 数值类型 */
    NUMBER,
    /** 文本类型 */
    TEXT,
    /** 关键字类型 */
    KEYWORD,
    /** 布尔类型 */
    BOOLEAN,
    /** 标记类型 */
    TOKEN
  }

  /** 字段基数枚举 */
  public enum Cardinality {
    /** 单值字段 */
    SINGLE,
    /** 多值字段 */
    MULTI
  }

  /**
   * 字段能力记录，定义字段支持的操作和约束。
   *
   * @param ops 支持的操作符集合
   * @param negatableOps 可取反的操作符集合
   * @param supportsNot 是否支持 NOT 操作
   * @param termMatches 支持的匹配类型
   * @param termCaseSensitiveAllowed 是否允许大小写敏感
   * @param termAllowBlank 是否允许空白值
   * @param termMinLength 最小长度
   * @param termMaxLength 最大长度
   * @param termPattern 匹配模式
   * @param inMaxSize IN 操作的最大元素数
   * @param inCaseSensitiveAllowed IN 操作是否允许大小写敏感
   * @param rangeKind 范围类型
   * @param rangeAllowOpenStart 是否允许开放起点
   * @param rangeAllowOpenEnd 是否允许开放终点
   * @param rangeAllowClosedAtInfinity 是否允许无限边界
   * @param dateMin 日期最小值
   * @param dateMax 日期最大值
   * @param datetimeMin 日期时间最小值
   * @param datetimeMax 日期时间最大值
   * @param numberMin 数值最小值
   * @param numberMax 数值最大值
   * @param existsSupported 是否支持存在性检查
   * @param tokenKinds 支持的 token 类型
   * @param tokenValuePattern token 值模式
   */
  public record Capability(
      Set<String> ops,
      Set<String> negatableOps,
      boolean supportsNot,
      Set<String> termMatches,
      boolean termCaseSensitiveAllowed,
      boolean termAllowBlank,
      int termMinLength,
      int termMaxLength,
      String termPattern,
      int inMaxSize,
      boolean inCaseSensitiveAllowed,
      RangeKind rangeKind,
      boolean rangeAllowOpenStart,
      boolean rangeAllowOpenEnd,
      boolean rangeAllowClosedAtInfinity,
      LocalDate dateMin,
      LocalDate dateMax,
      Instant datetimeMin,
      Instant datetimeMax,
      String numberMin,
      String numberMax,
      boolean existsSupported,
      Set<String> tokenKinds,
      String tokenValuePattern) {
    public Capability {
      Objects.requireNonNull(ops, "ops");
      Objects.requireNonNull(rangeKind, "rangeKind");
      ops = Set.copyOf(ops);
      negatableOps = negatableOps == null ? Set.of() : Set.copyOf(negatableOps);
      termMatches = termMatches == null ? Set.of() : Set.copyOf(termMatches);
      tokenKinds = tokenKinds == null ? Set.of() : Set.copyOf(tokenKinds);
    }
  }

  /** 范围类型枚举 */
  public enum RangeKind {
    /** 不支持范围查询 */
    NONE,
    /** 日期范围 */
    DATE,
    /** 日期时间范围 */
    DATETIME,
    /** 数值范围 */
    NUMBER
  }

  /**
   * API 参数映射记录，定义标准参数到提供商参数的映射。
   *
   * @param stdKey 标准参数键
   * @param providerParamName 提供商参数名称
   * @param transformCode 转换代码（可选）
   * @param notesJson 备注信息（JSON 格式）
   */
  public record ApiParameter(
      String stdKey, String providerParamName, String transformCode, String notesJson) {
    public ApiParameter {
      Objects.requireNonNull(stdKey, "stdKey");
    }
  }

  /**
   * 渲染规则记录，定义如何将表达式节点渲染为查询字符串或参数。
   *
   * @param fieldKey 字段键
   * @param scopeCode 作用域代码
   * @param operationTypeKey 操作类型键
   * @param operator 操作符
   * @param matchTypeCode 匹配类型代码
   * @param negation 取反限定符
   * @param valueType 值类型
   * @param emitType 输出类型（查询字符串/参数）
   * @param template 模板字符串
   * @param itemTemplate 元素模板（用于多值）
   * @param joiner 连接符（用于多值）
   * @param wrapGroup 是否包装为分组
   * @param params 附加参数
   * @param functionCode 函数代码（可选）
   * @param effectiveFrom 生效开始时间
   * @param effectiveTo 生效结束时间
   * @param priority 优先级
   */
  public record RenderRule(
      String fieldKey,
      String scopeCode,
      String operationTypeKey,
      com.patra.expr.Atom.Operator operator,
      String matchTypeCode,
      NegationQualifier negation,
      ValueType valueType,
      EmitType emitType,
      String template,
      String itemTemplate,
      String joiner,
      boolean wrapGroup,
      Map<String, String> params,
      String functionCode,
      Instant effectiveFrom,
      Instant effectiveTo,
      int priority) {
    public RenderRule {
      Objects.requireNonNull(fieldKey, "fieldKey");
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(emitType, "emitType");
      if (params != null) {
        params = Map.copyOf(params);
      }
    }
  }

  /** 取反限定符枚举 */
  public enum NegationQualifier {
    /** 任意（不限制） */
    ANY,
    /** 必须取反 */
    TRUE,
    /** 不能取反 */
    FALSE
  }

  /** 值类型枚举 */
  public enum ValueType {
    /** 任意类型 */
    ANY,
    /** 字符串类型 */
    STRING,
    /** 日期类型 */
    DATE,
    /** 日期时间类型 */
    DATETIME,
    /** 数值类型 */
    NUMBER
  }

  /** 输出类型枚举 */
  public enum EmitType {
    /** 输出为查询字符串 */
    QUERY,
    /** 输出为参数 */
    PARAMS
  }
}
