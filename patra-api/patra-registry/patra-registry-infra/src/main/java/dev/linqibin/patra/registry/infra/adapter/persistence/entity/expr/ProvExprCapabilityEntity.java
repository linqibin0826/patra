package dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 表达式能力 JPA 实体，映射到表 `reg_prov_expr_capability`。
///
/// 描述数据源字段的表达式能力和约束。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_expr_capability")
public class ProvExprCapabilityEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 生命周期状态代码。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;

  /// 规范字段键。
  @Column(name = "field_key", nullable = false, length = 50)
  private String fieldKey;

  /// 包含时间戳，能力生效时间。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 排除时间戳，能力过期时间。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// JSON 数组，枚举支持的表达式操作符。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ops", columnDefinition = "JSON")
  private JsonNode ops;

  /// JSON 数组，列出支持否定的操作符。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "negatable_ops", columnDefinition = "JSON")
  private JsonNode negatableOps;

  /// 指示是否全局支持 NOT 操作符。
  @Column(name = "supports_not")
  private Boolean supportsNot;

  /// JSON 数组，描述 TERM 操作符适用的匹配策略。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "term_matches", columnDefinition = "JSON")
  private JsonNode termMatches;

  /// 标志，表示 TERM 匹配是否可以区分大小写。
  @Column(name = "term_case_sensitive_allowed")
  private Boolean termCaseSensitiveAllowed;

  /// 指示是否允许空白 TERM 值。
  @Column(name = "term_allow_blank")
  private Boolean termAllowBlank;

  /// TERM 值的最小长度。
  @Column(name = "term_min_len")
  private Integer termMinLen;

  /// TERM 值的最大长度。
  @Column(name = "term_max_len")
  private Integer termMaxLen;

  /// 可选的正则表达式，约束 TERM 值。
  @Column(name = "term_pattern", length = 500)
  private String termPattern;

  /// IN 操作符中允许的最大元素数量。
  @Column(name = "in_max_size")
  private Integer inMaxSize;

  /// 指示 IN 比较是否可以区分大小写。
  @Column(name = "in_case_sensitive_allowed")
  private Boolean inCaseSensitiveAllowed;

  /// 范围类型代码(NONE/DATE/DATETIME/NUMBER)。
  @Column(name = "range_kind_code", length = 20)
  private String rangeKindCode;

  /// 指示是否允许开放起始范围。
  @Column(name = "range_allow_open_start")
  private Boolean rangeAllowOpenStart;

  /// 指示是否允许开放结束范围。
  @Column(name = "range_allow_open_end")
  private Boolean rangeAllowOpenEnd;

  /// 指示范围是否可以在正无穷或负无穷处闭合。
  @Column(name = "range_allow_closed_at_infty")
  private Boolean rangeAllowClosedAtInfty;

  /// DATE 范围支持的最小日期。
  @Column(name = "date_min")
  private LocalDate dateMin;

  /// DATE 范围支持的最大日期。
  @Column(name = "date_max")
  private LocalDate dateMax;

  /// DATETIME 范围支持的最小时刻。
  @Column(name = "datetime_min")
  private Instant datetimeMin;

  /// DATETIME 范围支持的最大时刻。
  @Column(name = "datetime_max")
  private Instant datetimeMax;

  /// NUMBER 范围允许的最小数值。
  @Column(name = "number_min", precision = 19, scale = 4)
  private BigDecimal numberMin;

  /// NUMBER 范围允许的最大数值。
  @Column(name = "number_max", precision = 19, scale = 4)
  private BigDecimal numberMax;

  /// 指示是否支持 EXISTS 操作符。
  @Column(name = "exists_supported")
  private Boolean existsSupported;

  /// JSON 数组，描述 TOKEN 操作符支持的令牌类型。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "token_kinds", columnDefinition = "JSON")
  private JsonNode tokenKinds;

  /// 可选的正则表达式，约束令牌值。
  @Column(name = "token_value_pattern", length = 500)
  private String tokenValuePattern;
}
