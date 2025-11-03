package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
import java.time.Instant;

/**
 * 表达式渲染规则领域值对象,对应表 {@code reg_prov_expr_render_rule}。
 *
 * <p>定义如何将表达式原子(字段/操作/匹配/否定/值类型)渲染为查询片段或标准参数。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprRenderRule(
    /* 主键;唯一渲染规则标识符 */
    Long id,
    /* 外键,引用 {@code reg_provenance.id} */
    Long provenanceId,
    /* 操作类型区分器 (HARVEST/UPDATE/BACKFILL);{@code null} 表示应用于所有类型 */
    String operationType,
    /* 统一的内部字段键(逻辑外键到 {@code reg_expr_field_dict.field_key}) */
    String fieldKey,
    /* 表达式操作符代码(字典代码: reg_expr_op)例如 TERM/IN/RANGE/EXISTS/TOKEN */
    String opCode,
    /* 匹配类型代码(字典代码: reg_match_type;仅 TERM)例如 PHRASE/EXACT/ANY;{@code null} 表示不可知 */
    String matchTypeCode,
    /* 否定标志:{@code true} 表示 NOT,{@code false} 表示非 NOT;{@code null} 表示不可知 */
    Boolean negated,
    /* RANGE 等的值类型代码 (STRING/DATE/DATETIME/NUMBER);{@code null} 表示不可知 */
    String valueTypeCode,
    /* 发射类型(字典代码: reg_emit_type):QUERY 表示查询片段,PARAMS 表示标准参数 */
    String emitTypeCode,
    /* {@code matchTypeCode} 的归一化:{@code null} -> {@code ANY} */
    String matchTypeKey,
    /* {@code negated} 的归一化:{@code null} -> {@code ANY},{@code true} -> {@code T},{@code false} -> {@code F} */
    String negatedKey,
    /* {@code valueTypeCode} 的归一化:{@code null} -> {@code ANY} */
    String valueTypeKey,
    /* 包含性时间戳,标记此规则何时生效 */
    Instant effectiveFrom,
    /* 排他性时间戳,标记此规则何时到期;{@code null} 表示开放式 */
    Instant effectiveTo,
    /* 当 {@code emitTypeCode} 为 {@code QUERY} 时渲染查询片段的模板;支持辅助函数(例如,{{q v}}/{{lower ...}}) */
    String template,
    /* 当 {@code emitTypeCode} 为 {@code QUERY} 且 {@code opCode} 为 {@code IN} 时每个项目的模板 */
    String itemTemplate,
    /* 当 {@code emitTypeCode} 为 {@code QUERY} 且 {@code opCode} 为 {@code IN} 时项目的连接符(例如," OR " / " AND ") */
    String joiner,
    /* 当 {@code emitTypeCode} 为 {@code QUERY} 且 {@code opCode} 为 {@code IN} 时是否用括号包裹整个组 */
    boolean wrapGroup,
    /* 当 {@code emitTypeCode} 为 {@code PARAMS} 时的标准键/模板变量 JSON(例如,{"from":"from","to":"to"}) */
    String paramsJson,
    /* 模板级渲染函数代码(reg_transform 的子集/扩展);例如,PUBMED_DATETYPE */
    String functionCode)
    implements TemporalEntity {
  public ExprRenderRule(
      Long id,
      Long provenanceId,
      String operationType,
      String fieldKey,
      String opCode,
      String matchTypeCode,
      Boolean negated,
      String valueTypeCode,
      String emitTypeCode,
      String matchTypeKey,
      String negatedKey,
      String valueTypeKey,
      Instant effectiveFrom,
      Instant effectiveTo,
      String template,
      String itemTemplate,
      String joiner,
      boolean wrapGroup,
      String paramsJson,
      String functionCode) {
    validateBasicFields(id, provenanceId, fieldKey, opCode, emitTypeCode, effectiveFrom);
    validateNormalizedKeys(matchTypeKey, negatedKey, valueTypeKey);

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = DomainValidationException.trimOrNull(operationType);
    this.fieldKey = fieldKey.trim();
    this.opCode = opCode.trim();
    this.matchTypeCode = DomainValidationException.trimOrNull(matchTypeCode);
    this.negated = negated;
    this.valueTypeCode = DomainValidationException.trimOrNull(valueTypeCode);
    this.emitTypeCode = emitTypeCode.trim();
    this.matchTypeKey = matchTypeKey.trim();
    this.negatedKey = negatedKey.trim();
    this.valueTypeKey = valueTypeKey.trim();
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.template = template;
    this.itemTemplate = itemTemplate;
    this.joiner = DomainValidationException.trimOrNull(joiner);
    this.wrapGroup = wrapGroup;
    this.paramsJson = paramsJson;
    this.functionCode = DomainValidationException.trimOrNull(functionCode);
  }

  /**
   * 验证渲染规则的基本必需字段。
   *
   * @param id 规则标识符
   * @param provenanceId 来源标识符
   * @param fieldKey 字段键
   * @param opCode 操作代码
   * @param emitTypeCode 发射类型代码
   * @param effectiveFrom 生效开始时间戳
   */
  private static void validateBasicFields(
      Long id,
      Long provenanceId,
      String fieldKey,
      String opCode,
      String emitTypeCode,
      Instant effectiveFrom) {
    DomainValidationException.positive(id, "Render rule id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.notBlank(fieldKey, "Field key");
    DomainValidationException.notBlank(opCode, "Operation code");
    DomainValidationException.notBlank(emitTypeCode, "Emit type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }

  /**
   * 验证归一化的维度键。
   *
   * @param matchTypeKey 归一化的匹配类型键
   * @param negatedKey 归一化的否定键
   * @param valueTypeKey 归一化的值类型键
   */
  private static void validateNormalizedKeys(
      String matchTypeKey, String negatedKey, String valueTypeKey) {
    DomainValidationException.notBlank(matchTypeKey, "Match type key");
    DomainValidationException.notBlank(negatedKey, "Negated key");
    DomainValidationException.notBlank(valueTypeKey, "Value type key");
  }
}
