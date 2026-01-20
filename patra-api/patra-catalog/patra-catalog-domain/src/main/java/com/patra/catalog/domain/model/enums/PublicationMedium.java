package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 出版媒介枚举。
///
/// 字段映射：cat_publication.media_type
///
/// 类型说明：
///
/// - **PRINT** - 仅纸质版
/// - **ELECTRONIC** - 仅电子版
/// - **BOTH** - 纸质+电子双版本
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum PublicationMedium {

  /// 仅纸质版
  PRINT("print", "Print"),

  /// 仅电子版
  ELECTRONIC("electronic", "Electronic"),

  /// 纸质+电子双版本
  BOTH("both", "Both");

  private final String code;
  private final String description;

  PublicationMedium(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "print", "ELECTRONIC", "both"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static PublicationMedium fromCode(String value) {
    Assert.notBlank(value, "媒介类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (PublicationMedium type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的媒介类型：" + value);
  }

  /// 判断是否包含电子版。
  ///
  /// @return true 如果是电子版或双版本
  public boolean hasElectronic() {
    return this == ELECTRONIC || this == BOTH;
  }

  /// 判断是否包含纸质版。
  ///
  /// @return true 如果是纸质版或双版本
  public boolean hasPrint() {
    return this == PRINT || this == BOTH;
  }
}
