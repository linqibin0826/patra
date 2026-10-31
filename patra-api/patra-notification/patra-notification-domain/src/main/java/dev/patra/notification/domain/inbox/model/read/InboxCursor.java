package dev.patra.notification.domain.inbox.model.read;

import dev.patra.notification.domain.inbox.exception.InvalidInboxRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

/// 提醒列表的游标：上一页最后一条的排序时间与提醒 ID。
///
/// 排序时间会变（有帮助条目再被点时挪到最上面），所以游标是两段，同一时刻再按 ID 定序。对外编码成不透明的
/// Base64URL，门户原样回传、不解析。
///
/// @param occurredAt 排序时间（微秒精度，与库里一致）
/// @param notificationId 提醒 ID
public record InboxCursor(Instant occurredAt, long notificationId) {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  /// 校验非空。
  public InboxCursor {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param occurredAt 排序时间
  /// @param notificationId 提醒 ID
  /// @return 游标
  public static InboxCursor of(Instant occurredAt, long notificationId) {
    return new InboxCursor(occurredAt, notificationId);
  }

  /// 编码成对外的写法。
  ///
  /// @return Base64URL（无填充）
  public String encode() {
    long micros = ChronoUnit.MICROS.between(Instant.EPOCH, occurredAt);
    return ENCODER.encodeToString(
        (micros + "_" + notificationId).getBytes(StandardCharsets.US_ASCII));
  }

  /// 解析请求里的游标。
  ///
  /// @param raw 上一页的 `nextCursor`，可空
  /// @return 游标；第一页为 `null`
  /// @throws InvalidInboxRequestException 不是本服务编出来的游标（排序时间早于 1970 的也不是：库里的时刻放不下极端值，
  ///     不拦下会变成数据库错误）
  public static InboxCursor parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      String[] parts =
          new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.US_ASCII).split("_", -1);
      if (parts.length != 2) {
        throw invalid();
      }
      long micros = Long.parseLong(parts[0]);
      long id = Long.parseLong(parts[1]);
      if (micros < 0 || id <= 0) {
        throw invalid();
      }
      return of(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
    } catch (IllegalArgumentException | ArithmeticException ex) {
      throw invalid();
    }
  }

  /// 游标不合法。
  ///
  /// @return 异常
  private static InvalidInboxRequestException invalid() {
    return new InvalidInboxRequestException("cursor 不合法");
  }
}
