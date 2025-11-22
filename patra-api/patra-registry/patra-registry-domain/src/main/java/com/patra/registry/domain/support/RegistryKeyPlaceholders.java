package com.patra.registry.domain.support;

/// 注册中心维度/条件键占位符常量。
///
/// 用于快照或配置合并的 SOURCE/TASK 作用域统一保留字,避免跨层重复定义。
///
/// 参考: docs/patra-registry/expr/Registry-expr-schema-design.md
///
/// @author linqibin
/// @since 0.1.0
public final class RegistryKeyPlaceholders {

  /// 私有构造函数,防止实例化工具类。
  private RegistryKeyPlaceholders() {}

  /// 表示"所有任务/来源",用作作用域合并中的回退键。
  public static final String ALL = "ALL";

  /// 表示"任意值",通常用于通配符/不可知维度匹配。
  public static final String ANY = "ANY";

  /// 取反规则的归一化标记(negated = true)。
  public static final String NEGATED_TRUE = "T";

  /// 非取反规则的归一化标记(negated = false 或显式 false)。
  public static final String NEGATED_FALSE = "F";
}
