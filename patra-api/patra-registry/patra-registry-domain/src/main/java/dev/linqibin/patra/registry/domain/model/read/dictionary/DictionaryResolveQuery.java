package dev.linqibin.patra.registry.domain.model.read.dictionary;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.util.List;

/// 字典解析批量查询视图。
///
/// 用于向外部服务返回批量解析结果。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveQuery(
    String typeCode, String sourceStandard, List<DictionaryResolveItemQuery> items) {

  /// 带验证的规范构造函数。
  ///
  /// @param typeCode 字典类型代码
  /// @param sourceStandard 来源标准
  /// @param items 解析结果列表
  public DictionaryResolveQuery {
    typeCode = DomainValidationException.notBlank(typeCode, "Dictionary type code");
    sourceStandard =
        DomainValidationException.notBlank(sourceStandard, "Dictionary source standard");
    items = List.copyOf(DomainValidationException.nonNull(items, "Dictionary resolve items"));
  }
}
