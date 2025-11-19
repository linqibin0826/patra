package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/**
 * ISSN 类型枚举。
 *
 * <p>字段映射：cat_venue.issn_type → print/electronic
 *
 * <p>类型说明：
 *
 * <ul>
 *   <li><b>PRINT</b>：纸质版ISSN（Print ISSN）
 *   <li><b>ELECTRONIC</b>：电子版ISSN（Electronic ISSN，也称eISSN）
 * </ul>
 *
 * <p>业务规则：
 *
 * <ul>
 *   <li>同一期刊可能同时拥有Print ISSN和Electronic ISSN
 *   <li>Linking ISSN用于关联纸质版和电子版
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum IssnType {

  /** 纸质版ISSN */
  PRINT("print", "Print ISSN"),

  /** 电子版ISSN */
  ELECTRONIC("electronic", "Electronic ISSN");

  /** 数据库存储的代码值（小写） */
  private final String code;

  /** 描述文本 */
  private final String description;

  IssnType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 从代码值解析枚举（不区分大小写）。
   *
   * @param value 代码值（如 "print", "ELECTRONIC"）
   * @return 对应的枚举值
   * @throws IllegalArgumentException 如果代码值无效
   */
  public static IssnType fromCode(String value) {
    Assert.notBlank(value, "ISSN类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (IssnType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的ISSN类型：" + value);
  }

  /**
   * 判断是否为纸质版。
   *
   * @return true 如果为纸质版
   */
  public boolean isPrint() {
    return this == PRINT;
  }

  /**
   * 判断是否为电子版。
   *
   * @return true 如果为电子版
   */
  public boolean isElectronic() {
    return this == ELECTRONIC;
  }
}
