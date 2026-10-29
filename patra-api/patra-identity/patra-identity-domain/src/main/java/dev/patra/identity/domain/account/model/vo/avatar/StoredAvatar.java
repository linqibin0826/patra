package dev.patra.identity.domain.account.model.vo.avatar;

import java.io.InputStream;
import java.util.Objects;

/// 从对象存储打开的一档头像：流由调用方读完并关闭。
///
/// @param content 字节流
/// @param length 字节数
public record StoredAvatar(InputStream content, long length) {

  /// 校验非空。
  public StoredAvatar {
    Objects.requireNonNull(content, "content 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param content 字节流
  /// @param length 字节数
  /// @return 头像流
  public static StoredAvatar of(InputStream content, long length) {
    return new StoredAvatar(content, length);
  }
}
