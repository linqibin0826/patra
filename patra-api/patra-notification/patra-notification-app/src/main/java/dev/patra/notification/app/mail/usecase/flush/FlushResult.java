package dev.patra.notification.app.mail.usecase.flush;

/// 一轮到点处理的计数。
///
/// @param recipients 处理成功的收件人数
/// @param dropped 丢弃的排队行数
/// @param deliveries 新建的逐条投递数
public record FlushResult(int recipients, int dropped, int deliveries) {

  /// 创建。
  ///
  /// @param recipients 收件人数
  /// @param dropped 丢弃的行数
  /// @param deliveries 新建的投递数
  /// @return 计数
  public static FlushResult of(int recipients, int dropped, int deliveries) {
    return new FlushResult(recipients, dropped, deliveries);
  }
}
