package dev.patra.interaction.domain.collection.model.enums;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import java.util.Locale;

/// 文献库与收藏夹列表的排序。
public enum LibrarySort {
  /// 加入时间倒序（默认）：文献库按收藏时间，收藏夹按加入该夹的时间。
  ADDED,
  /// 出版日期倒序，缺的排后。
  PUBLISHED,
  /// 期刊全称升序（不分大小写），缺的排后。
  JOURNAL;

  /// 解析请求参数（`added` / `published` / `journal`），缺省为 `ADDED`。
  ///
  /// @param raw 参数，可空
  /// @return 排序
  /// @throws InvalidCollectionRequestException 未知取值
  public static LibrarySort fromParam(String raw) {
    if (raw == null) {
      return ADDED;
    }
    for (LibrarySort s : values()) {
      if (s.name().equals(raw.toUpperCase(Locale.ROOT))) {
        return s;
      }
    }
    throw new InvalidCollectionRequestException("sort 只能是 added、published 或 journal");
  }
}
