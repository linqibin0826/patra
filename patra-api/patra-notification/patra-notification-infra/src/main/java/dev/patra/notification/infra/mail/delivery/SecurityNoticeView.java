package dev.patra.notification.infra.mail.delivery;

import dev.patra.notification.domain.mail.model.vo.OperationContext;
import dev.patra.notification.domain.mail.model.vo.SecurityNotice;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/// 安全通知片段用的展示值：时间已按时区格式化并标出偏移，缺失的设备和 IP 换成「未知」。
///
/// @param occurredAt 形如 `2026-10-21 16:30 (UTC+8)`
/// @param device 设备描述，缺失时为「未知」
/// @param ip IP 与属地，形如 `203.0.113.9（中国 上海）`；没有属地时只有 IP；缺失时为「未知」
/// @param notMeAdvice 不是本人操作时该怎么办
public record SecurityNoticeView(String occurredAt, String device, String ip, String notMeAdvice) {

  /// 缺失值的显示文字。
  static final String UNKNOWN = "未知";

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /// 四项都不能为 `null`。
  public SecurityNoticeView {
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    Objects.requireNonNull(device, "device 不能为 null");
    Objects.requireNonNull(ip, "ip 不能为 null");
    Objects.requireNonNull(notMeAdvice, "notMeAdvice 不能为 null");
  }

  /// 从领域的安全通知换算。
  ///
  /// @param notice 安全通知
  /// @param zone 显示时区
  /// @return 展示值
  public static SecurityNoticeView from(SecurityNotice notice, ZoneId zone) {
    Objects.requireNonNull(notice, "notice 不能为 null");
    Objects.requireNonNull(zone, "zone 不能为 null");
    OperationContext context = notice.context();
    String ip;
    if (context.ipAddress() == null) {
      ip = UNKNOWN;
    } else if (context.ipLocation() == null) {
      ip = context.ipAddress();
    } else {
      ip = context.ipAddress() + "（" + context.ipLocation() + "）";
    }
    return new SecurityNoticeView(
        formatTime(context.occurredAt(), zone),
        context.device() == null ? UNKNOWN : context.device(),
        ip,
        notice.notMeAdvice());
  }

  /// 按时区格式化并标出与 UTC 的偏移：整点写 `UTC+8`，非整点写 `UTC+5:30`，零偏移写 `UTC`。
  ///
  /// @param instant 时刻
  /// @param zone 时区
  /// @return 显示文字
  static String formatTime(Instant instant, ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(instant);
    int total = offset.getTotalSeconds();
    String suffix;
    if (total == 0) {
      suffix = "UTC";
    } else {
      int abs = Math.abs(total);
      int hours = abs / 3600;
      int minutes = (abs % 3600) / 60;
      suffix = "UTC" + (total < 0 ? "-" : "+") + hours + (minutes == 0 ? "" : ":" + minutes);
    }
    return TIME.format(instant.atZone(zone)) + " (" + suffix + ")";
  }
}
