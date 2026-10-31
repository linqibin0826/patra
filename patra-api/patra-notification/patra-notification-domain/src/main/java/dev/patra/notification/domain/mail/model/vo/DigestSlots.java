package dev.patra.notification.domain.mail.model.vo;

/// 每日汇总里两节各列几条。
///
/// @param replies 「回复我的」列几条
/// @param mentions 「@ 我的」列几条
public record DigestSlots(int replies, int mentions) {

  /// 构造。
  ///
  /// @param replies 回复列几条
  /// @param mentions @ 列几条
  /// @return 名额
  public static DigestSlots of(int replies, int mentions) {
    return new DigestSlots(replies, mentions);
  }
}
