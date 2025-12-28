package com.patra.registry.api.dto.dict;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// 字典解析请求。
///
/// 由调用方提供字典类型、来源标准以及待解析的原始值列表。
///
/// 来源标准来自注册中心 `sys_reference_standard` 表,为空时使用默认 GLOBAL。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveReq(
    String typeCode, @JsonAlias("sourceSystem") String sourceStandard, List<String> rawValues) {

  /// 规范构造器,执行非空/空白验证并修剪空格。
  ///
  /// @param typeCode 字典类型代码
  /// @param sourceStandard 来源标准(可为空,为空时使用默认全局标准)
  /// @param rawValues 原始值列表
  public DictionaryResolveReq {
    if (typeCode == null || typeCode.trim().isEmpty()) {
      throw new IllegalArgumentException("字典类型代码不能为null或空字符串");
    }
    if (rawValues == null) {
      throw new IllegalArgumentException("原始值列表不能为null");
    }
    typeCode = typeCode.trim();
    sourceStandard =
        (sourceStandard == null || sourceStandard.trim().isEmpty()) ? null : sourceStandard.trim();
    rawValues = Collections.unmodifiableList(new ArrayList<>(rawValues));
  }
}
