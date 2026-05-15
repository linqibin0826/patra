package dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 表达式渲染规则 JPA 实体，映射到表 `reg_prov_expr_render_rule`。
///
/// 定义表达式原子如何渲染为提供方查询或参数。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_expr_render_rule")
public class ProvExprRenderRuleEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 生命周期状态代码。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;

  /// 规则渲染的规范字段键。
  @Column(name = "field_key", nullable = false, length = 50)
  private String fieldKey;

  /// 操作符代码 (TERM/IN/RANGE/等)。
  @Column(name = "op_code", nullable = false, length = 20)
  private String opCode;

  /// 可选的匹配类型细化(例如，PHRASE/EXACT)。
  @Column(name = "match_type_code", length = 20)
  private String matchTypeCode;

  /// 如果规则特定于否定表达式则为 `true`。
  @Column(name = "negated")
  private Boolean negated;

  /// 可选的值类型鉴别器 (STRING/DATE/DATETIME/NUMBER)。
  @Column(name = "value_type_code", length = 20)
  private String valueTypeCode;

  /// 渲染输出类型 (QUERY 或 PARAMS)。
  @Column(name = "emit_type_code", length = 20)
  private String emitTypeCode;

  /// 规范化的匹配类型键（未指定时为 `ANY`）。
  ///
  /// MySQL 生成列，由数据库自动计算：`IFNULL(match_type_code, 'ANY')`。
  @Generated(event = EventType.INSERT)
  @Column(name = "match_type_key", length = 20, insertable = false, updatable = false)
  private String matchTypeKey;

  /// 规范化的否定键 (T/F/ANY)。
  ///
  /// MySQL 生成列，由数据库自动计算：`IFNULL(IF(negated = 1, 'T', 'F'), 'ANY')`。
  @Generated(event = EventType.INSERT)
  @Column(name = "negated_key", length = 5, insertable = false, updatable = false)
  private String negatedKey;

  /// 规范化的值类型键（未指定时为 `ANY`）。
  ///
  /// MySQL 生成列，由数据库自动计算：`IFNULL(value_type_code, 'ANY')`。
  @Generated(event = EventType.INSERT)
  @Column(name = "value_type_key", length = 20, insertable = false, updatable = false)
  private String valueTypeKey;

  /// 包含时间戳，渲染规则生效时间。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 排除时间戳，渲染规则过期时间。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 发出查询片段时使用的模板。
  @Column(name = "template", length = 1000)
  private String template;

  /// 可选的模板，应用于 IN 操作符的每个项目。
  @Column(name = "item_template", length = 500)
  private String itemTemplate;

  /// 用于连接多个渲染项目的连接符。
  @Column(name = "joiner", length = 20)
  private String joiner;

  /// 指示渲染输出是否应该用括号包裹。
  @Column(name = "wrap_group")
  private Boolean wrapGroup;

  /// JSON 结构，包含发出的参数映射。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "params", columnDefinition = "JSON")
  private JsonNode params;

  /// 可选的自定义函数，在渲染期间应用。
  @Column(name = "fn_code", length = 50)
  private String fnCode;
}
