package dev.patra.interaction.domain.collection.model.enums;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import java.util.Locale;

/// 收藏夹的可见性。名字落库（`collection.visibility`）。
public enum CollectionVisibility {
  /// 只有所有者能看（默认）。
  PRIVATE,
  /// 所有者的主页对访客可见时，访客能看。
  PUBLIC;

  /// 解析请求参数，大小写不敏感。
  ///
  /// @param raw 参数
  /// @return 可见性
  /// @throws InvalidCollectionRequestException 缺失或未知
  public static CollectionVisibility fromParam(String raw) {
    if (raw != null) {
      for (CollectionVisibility v : values()) {
        if (v.name().equals(raw.toUpperCase(Locale.ROOT))) {
          return v;
        }
      }
    }
    throw new InvalidCollectionRequestException("可见性只能是 PRIVATE 或 PUBLIC");
  }
}
