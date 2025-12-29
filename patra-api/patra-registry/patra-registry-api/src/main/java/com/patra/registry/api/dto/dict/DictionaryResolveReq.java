package com.patra.registry.api.dto.dict;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// 字典解析请求。
///
/// 由调用方提供字典类型、来源标准以及待解析的原始值列表。
///
/// 来源标准（必填）来自注册中心 `sys_reference_standard` 表。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveReq(
    @NotBlank(message = "字典类型代码不能为空") String typeCode,
    @NotBlank(message = "来源标准不能为空") String sourceStandard,
    @NotNull(message = "原始值列表不能为null") List<String> rawValues) {

  /// 规范构造器，执行数据规范化（trim、不可变列表）。
  ///
  /// 验证由 Jakarta Validation 注解处理，Controller 层需使用 `@Valid` 触发。
  ///
  /// @param typeCode 字典类型代码
  /// @param sourceStandard 来源标准（必填）
  /// @param rawValues 原始值列表
  public DictionaryResolveReq {
    // 数据规范化（验证由 Jakarta Validation 注解处理）
    typeCode = typeCode != null ? typeCode.trim() : null;
    sourceStandard = sourceStandard != null ? sourceStandard.trim() : null;
    rawValues = rawValues != null ? Collections.unmodifiableList(new ArrayList<>(rawValues)) : null;
  }
}
