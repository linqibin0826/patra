package dev.patra.catalog.infra.kernel.support;

import java.util.Arrays;
import java.util.List;

/// native 查询里用 `array_to_string(..., ' ')` 拼成一列的多值，拆回列表。
///
/// PostgreSQL 数组到 `String[]` 的接口投影不稳，所以 SQL 侧统一拼成空格分隔的字符串。
///
/// @author linqibin
/// @since 0.1.0
public final class ProjectionLists {

  private ProjectionLists() {}

  /// 按空格拆分；`null`、空串拆成空列表。
  ///
  /// @param value 空格分隔的值
  /// @return 不可变列表
  public static List<String> splitBySpace(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.trim().split(" ")).filter(s -> !s.isBlank()).toList();
  }
}
