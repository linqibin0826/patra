package com.patra.common.enums;

import java.util.Locale;

/// 注册表配置的分发作用域枚举。
/// 
/// 标识注册表条目应用的级别——涵盖表达式字段、渲染规则、API 参数映射, 以及与溯源相关的设置,如端点、分页、HTTP、重试或速率限制:
/// 
/// - {@link #SOURCE}: 应用于给定溯源内的每个任务。
///   - {@link #TASK}: 应用于特定任务类型,可能覆盖溯源级别的默认值。
/// 
/// 此枚举使配置作用域在代码中显式化,避免与其他作用域值(例如授权或分析作用域)混淆。 持久化列继续存储 `SOURCE`/`TASK`,因此不需要历史数据迁移。
/// 
/// @author linqibin
/// @since 0.1.0
/// @see #fromCode(String)
public enum RegistryConfigScope {
  /// 应用于溯源(数据源)级别。
  SOURCE,
  /// 应用于任务类型级别。
  TASK;

  /// 返回用于持久化和查找的规范大写代码。
/// 
/// @return 作用域代码
  public String code() {
    return name();
  }

  /// 通过忽略大小写和周围空格来解析代码。
/// 
/// @param code 持久化的代码值
/// @return 匹配的作用域
/// @throws IllegalArgumentException 如果输入为空或未知
  public static RegistryConfigScope fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("作用域代码不能为空");
    }
    try {
      return RegistryConfigScope.valueOf(code.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("未知的作用域代码: " + code, ex);
    }
  }
}
