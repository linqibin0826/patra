package dev.linqibin.patra.registry.domain.model.vo.expr;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.util.Objects;

/// 统一表达式字段字典领域值对象,对应表 `reg_expr_field_dict`。
///
/// 描述用于表达式建模、能力声明和渲染规则选择的规范原子字段和核心属性。与来源无关; 仅描述字段数据类型/基数/可暴露性。
///
/// @author linqibin
/// @since 0.1.0
public record ExprField(
    /* 主键;唯一字段标识符 */
    Long id,
    /* 在环境间保持稳定的规范字段键(例如,publish_date, ti, ab, tiab) */
    String fieldKey,
    /* 可选的显示名称,在控制台中显示以提高可读性 */
    String displayName,
    /* 详细描述,解释字段语义、约束和暴露说明 */
    String description,
    /* 数据类型代码(字典代码: reg_data_type)指示值类型 (DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN) */
    String dataTypeCode,
    /* 基数代码(字典代码: reg_cardinality)指示字段是否允许多个值 (SINGLE/MULTI) */
    String cardinalityCode,
    /* 字段是否允许被全局暴露/使用 */
    boolean exposable,
    /* 冗余标志,指示字段是否应被视为日期类型(通常与 DATE/DATETIME 类型一致) */
    boolean dateField) {
  /// 带验证的规范构造函数。
  ///
  /// @param id 唯一字段标识符,必须为正数
  /// @param fieldKey 规范字段键,不能为空白
  /// @param displayName 显示名称,可为 null
  /// @param description 字段描述,可为 null
  /// @param dataTypeCode 来自字典的数据类型代码,不能为空白
  /// @param cardinalityCode 来自字典的基数代码,不能为空白
  /// @param exposable 字段是否可暴露
  /// @param dateField 字段是否为日期类型
  /// @throws DomainValidationException 如果验证失败
  public ExprField(
      Long id,
      String fieldKey,
      String displayName,
      String description,
      String dataTypeCode,
      String cardinalityCode,
      boolean exposable,
      boolean dateField) {
    DomainValidationException.positive(id, "Expr field id");
    String keyTrimmed = DomainValidationException.notBlank(fieldKey, "Expr field key");
    String dtTrimmed =
        DomainValidationException.notBlank(dataTypeCode, "Expr field data type code");
    String cardinalityTrimmed =
        DomainValidationException.notBlank(cardinalityCode, "Expr field cardinality code");

    this.id = id;
    this.fieldKey = keyTrimmed;
    this.displayName = displayName != null ? displayName.trim() : "";
    this.description = description != null ? description.trim() : "";
    this.dataTypeCode = dtTrimmed;
    this.cardinalityCode = cardinalityTrimmed;
    this.exposable = exposable;
    this.dateField = dateField;
  }

  /// 检查字段是否可暴露给客户端。
  ///
  /// @return 如果字段可以暴露给客户端则返回 true
  public boolean isExposable() {
    return exposable;
  }

  /// 检查字段是否应被视为日期类型以用于渲染/验证分支。
  ///
  /// @return 如果字段为日期类型则返回 true
  public boolean isDateField() {
    return dateField;
  }

  /// 相等性仅基于 fieldKey(稳定的业务键)。
  ///
  /// @param o 要比较的对象
  /// @return 如果另一个对象是具有相同 fieldKey 的 ExprField 则返回 true
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExprField other)) {
      return false;
    }
    return Objects.equals(fieldKey, other.fieldKey);
  }

  /// 仅基于 fieldKey 的哈希码。
  ///
  /// @return fieldKey 的哈希码
  @Override
  public int hashCode() {
    return Objects.hash(fieldKey);
  }
}
