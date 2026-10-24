package dev.patra.interaction.domain.collection.model.enums;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import java.util.Locale;

/// 文献库的视图。
public enum LibraryView {
  /// 全部条目（默认）。
  ALL,
  /// 不在任何收藏夹里的条目。
  UNFILED;

  /// 解析请求参数（`all` / `unfiled`），缺省为 `ALL`。
  ///
  /// @param raw 参数，可空
  /// @return 视图
  /// @throws InvalidCollectionRequestException 未知取值
  public static LibraryView fromParam(String raw) {
    if (raw == null) {
      return ALL;
    }
    for (LibraryView v : values()) {
      if (v.name().equals(raw.toUpperCase(Locale.ROOT))) {
        return v;
      }
    }
    throw new InvalidCollectionRequestException("view 只能是 all 或 unfiled");
  }
}
