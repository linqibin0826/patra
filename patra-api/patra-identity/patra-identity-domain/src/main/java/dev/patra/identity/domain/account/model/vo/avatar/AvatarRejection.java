package dev.patra.identity.domain.account.model.vo.avatar;

/// 头像上传被拒的原因，`name()` 就是返回给门户的原因码（字段 `file`）。超过 5 MB 不在这里：
/// 由 multipart 上限拦下，回 413。
public enum AvatarRejection {
  /// 没有文件或文件为空。
  REQUIRED,
  /// 不是 JPEG / PNG / WebP（含 GIF、动图、伪造扩展名）。
  UNSUPPORTED_TYPE,
  /// 短边不足 128 像素。
  TOO_SMALL,
  /// 宽 × 高超过 4096²（防解压炸弹与解码内存放大）。
  TOO_MANY_PIXELS,
  /// 文件头坏了或解码失败。
  UNREADABLE
}
