package com.patra.catalog.domain.model.vo.affiliation;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// GRID 标识符值对象。封装 GRID(Global Research Identifier Database)的验证和管理。
///
/// **GRID 格式说明**：
///
/// - 格式：grid.XXXXX.YY(其中 X 为数字,Y 为字母或数字)
///   - 示例：grid.38142.3c
///   - GRID 是全球研究机构的标识符数据库
///   - 已于 2021 年合并到 ROR,但历史数据仍在使用
///
/// **设计原则**：
///
/// - 不可变性：Record 自动提供
///   - 格式验证：严格验证 GRID 格式
///   - 兼容性：支持历史数据中的 GRID ID
///
/// **使用示例**：
///
/// ```java
/// // 创建 GRID
/// GridId gridId = GridId.of("grid.38142.3c");
/// assert gridId.isValid();
///
/// // 获取 ID
/// String id = gridId.value(); // "grid.38142.3c"
/// ```
///
/// @param value GRID 标识符(格式：grid.XXXXX.YY)
/// @author linqibin
/// @since 0.1.0
public record GridId(String value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// GRID ID 格式正则表达式
  private static final String GRID_ID_PATTERN = "grid\\.[0-9]+\\.[a-z0-9]+";

  /// 紧凑构造器：验证 GRID 的有效性。
  ///
  /// @throws IllegalArgumentException 如果 GRID 为空或格式无效
  public GridId {
    Assert.notBlank(value, "GRID 标识符不能为空");

    // GRID 格式验证
    Assert.isTrue(value.matches(GRID_ID_PATTERN), "GRID 格式无效,必须符合 'grid.XXXXX.YY' 格式：%s", value);
  }

  /// 创建 GRID。
  ///
  /// @param value GRID 字符串
  /// @return GRID 值对象
  /// @throws IllegalArgumentException 如果格式无效
  public static GridId of(String value) {
    return new GridId(value);
  }

  /// 验证 GRID 格式是否有效。
  ///
  /// @param value GRID 字符串
  /// @return true 如果格式有效
  public static boolean isValid(String value) {
    try {
      new GridId(value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
