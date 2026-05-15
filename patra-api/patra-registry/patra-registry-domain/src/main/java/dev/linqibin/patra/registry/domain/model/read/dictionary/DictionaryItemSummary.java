package dev.linqibin.patra.registry.domain.model.read.dictionary;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;

/// 字典项摘要，用于列表查询场景。
///
/// 包含字典项的基本信息和可选的本地化标签。
///
/// @param code 字典项代码（如 ISO alpha-2 国家代码）
/// @param name 字典项默认名称（英文）
/// @param label 本地化标签（由 labelStandard 决定），不传标准时为 null
/// @param displayOrder 显示排序
/// @author linqibin
/// @since 0.1.0
public record DictionaryItemSummary(String code, String name, String label, int displayOrder) {

  /// 带验证的规范构造函数。
  public DictionaryItemSummary {
    code = DomainValidationException.notBlank(code, "字典项代码");
    name = DomainValidationException.notBlank(name, "字典项名称");
    label = DomainValidationException.trimOrNull(label);
  }
}
